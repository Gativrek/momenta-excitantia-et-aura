package io.github.gativrek.momenta.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.MainActivity
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Owns the one ExoPlayer and its MediaSession. The service owns the player, never an Activity or ViewModel.
 *
 * Everything that controls playback, our own UI included, goes through the session:
 * the notification, lock screen, Bluetooth buttons and car displays all use the same path.
 * MediaSessionService handles the foreground-service lifecycle and the media notification
 * for us: it goes foreground while playing and drops back when paused.
 *
 * Also home to what must outlive the UI: the sleep timer and saving/restoring the queue.
 */
@OptIn(UnstableApi::class) // ExoPlayer shuffle order and notification icon
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: QueuePlayer
    private lateinit var sleepTimer: SleepTimer

    /** Main-thread scope for player work; cancelled in onDestroy. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as App).container

    /** False until the saved queue has been restored, so an empty player never overwrites it. */
    private var restored = false
    private var periodicSave: Job? = null

    @OptIn(UnstableApi::class) // DefaultMediaNotificationProvider.setSmallIcon
    override fun onCreate() {
        super.onCreate()

        val exoPlayer =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // handleAudioFocus = true: ExoPlayer requests focus, pauses for calls and
                    // other media, ducks for navigation prompts and resumes when appropriate.
                    // No hand-written OnAudioFocusChangeListener anywhere.
                    true,
                )
                // Pause when headphones are unplugged or Bluetooth audio disconnects.
                .setHandleAudioBecomingNoisy(true)
                // Hold a partial wake lock while playing so the CPU keeps decoding with the screen off.
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        player = QueuePlayer(exoPlayer)

        // Tapping the notification (or the lock-screen player) opens the app.
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val session =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(openApp)
                .setCallback(SessionCallback())
                .build()
        mediaSession = session

        sleepTimer =
            SleepTimer(exoPlayer, scope) { state ->
                session.setSessionExtras(SessionProtocol.sleepTimerToExtras(state))
            }
        exoPlayer.addListener(PlayerEvents())

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )

        restoreQueue()
        followLibrary()
    }

    /** Every controller (our UI, System UI, Bluetooth) gets the same session. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * The user swiped the app away from Recents. Keep playing if music is
     * playing; otherwise there's nothing to keep the service for, so stop it.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        if (!player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        sleepTimer.cancel()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Keeps the queue in step with the library: when a file's tags change (edited in the
     * app or anywhere else), the queue, notification and car display show the new ones.
     */
    private fun followLibrary() {
        scope.launch {
            container.libraryRepository.tracks.collect { tracks -> refreshQueue(tracks.associateBy { it.id }) }
        }
    }

    private fun refreshQueue(library: Map<Long, Track>) {
        val exo = player.exoPlayer
        val count = exo.mediaItemCount
        if (count == 0) return
        val updates =
            (0 until count)
                .mapNotNull { i ->
                    val old = exo.getMediaItemAt(i)
                    val track = MediaItemMapper.trackId(old)?.let(library::get) ?: return@mapNotNull null
                    val fresh = MediaItemMapper.toMediaItem(track)
                    if (MediaItemMapper.isOutdated(old, fresh)) i to fresh else null
                }.toMap()
        if (updates.isEmpty()) return
        val current = exo.currentMediaItemIndex
        val currentUpdate = updates[current]
        if (currentUpdate != null && MediaItemMapper.fileChanged(exo.getMediaItemAt(current), currentUpdate)) {
            // The playing file was rewritten and its audio moved: reload the queue at the same
            // spot. Straight to ExoPlayer (QueuePlayer would redraw the shuffle), then put the
            // play order back. A prepared player stays prepared, and play/pause is kept.
            val order = PlayOrder.of(exo.currentTimeline, true)
            val position = exo.currentPosition
            exo.setMediaItems((0 until count).map { updates[it] ?: exo.getMediaItemAt(it) }, current, position)
            player.setPlayOrder(order)
        } else {
            // Same audio, new tags: swapped in place without interrupting playback.
            updates.forEach { (i, item) -> exo.replaceMediaItem(i, item) }
        }
    }

    // region Saving and restoring the queue (survives force-stop and reboot)

    /**
     * Put back the last queue, paused (never auto-play on launch). The player isn't
     * prepared here, so no notification appears until the user presses play.
     */
    private fun restoreQueue() {
        scope.launch {
            try {
                val saved = container.playbackStateStore.load() ?: return@launch
                val tracks =
                    try {
                        container.mediaStoreSource.queryTracks()
                    } catch (e: SecurityException) {
                        Log.w(TAG, "No audio permission, queue not restored", e)
                        return@launch
                    }
                val byId = tracks.associateBy { it.id }
                val queue = saved.keepOnly(byId.keys) ?: return@launch
                // The user started something while we were loading: theirs wins.
                if (player.mediaItemCount > 0) return@launch
                val exo = player.exoPlayer
                exo.setMediaItems(
                    queue.trackIds.map { MediaItemMapper.toMediaItem(byId.getValue(it)) },
                    queue.index,
                    queue.positionMs,
                )
                exo.repeatMode = queue.repeatMode
                // Straight to ExoPlayer, not QueuePlayer: keep the saved order, don't redraw it.
                exo.shuffleModeEnabled = queue.shuffle
                exo.setShuffleOrder(DefaultShuffleOrder(queue.playOrder.toIntArray(), Random.nextLong()))
            } finally {
                restored = true
            }
        }
    }

    private fun saveQueue() {
        if (!restored) return
        val p = player.exoPlayer
        val ids = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
        val queue =
            SavedQueue(
                trackIds = ids,
                index = p.currentMediaItemIndex,
                positionMs = p.currentPosition,
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
                playOrder = PlayOrder.of(p.currentTimeline, true),
            )
        // Application scope: a save started in onDestroy still finishes.
        container.applicationScope.launch { container.playbackStateStore.save(queue) }
    }

    // endregion

    /**
     * A track that can't be played (deleted while queued or playing, unreadable, or in a
     * format the device can't decode) would otherwise leave the player stuck in an error
     * state. Drop it from the queue, say so, and carry on with the next track in play order.
     */
    private fun skipUnplayable(error: PlaybackException) {
        val exo = player.exoPlayer
        if (exo.mediaItemCount == 0) return
        val index = exo.currentMediaItemIndex
        val title = exo.currentMediaItem?.mediaMetadata?.title ?: getString(R.string.unknown_track)
        Log.w(TAG, "Skipping unplayable item $index (${error.errorCodeName})", error)
        // Next in play order (shuffle-aware); repeat-one must not pick the same broken track.
        val repeat = if (exo.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else exo.repeatMode
        val next = exo.currentTimeline.getNextWindowIndex(index, repeat, exo.shuffleModeEnabled)

        exo.removeMediaItem(index)
        if (next != C.INDEX_UNSET && next != index && exo.mediaItemCount > 0) {
            exo.seekTo(if (next > index) next - 1 else next, 0)
            exo.prepare()
        } else {
            // Nothing playable left after it: stop cleanly instead of sitting in the error state.
            exo.stop()
        }
        val message = if (error.isFileGone()) R.string.error_track_missing else R.string.error_track_unplayable
        Toast.makeText(this, getString(message, title), Toast.LENGTH_LONG).show()
    }

    private fun PlaybackException.isFileGone() =
        errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
            errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

    private inner class PlayerEvents : Player.Listener {
        override fun onPlayerError(error: PlaybackException) = skipUnplayable(error)

        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) {
            sleepTimer.onPlayerChanged()
            if (events.containsAny(*SAVE_EVENTS)) saveQueue()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                periodicSave?.cancel()
                periodicSave =
                    if (player.isPlaying) {
                        scope.launch {
                            while (isActive) {
                                delay(SAVE_INTERVAL_MS)
                                saveQueue()
                            }
                        }
                    } else {
                        null
                    }
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        /** Our own UI gets the custom commands; other apps get the standard set. */
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (controller.packageName != packageName) return super.onConnect(session, controller)
            val commands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .apply { SessionProtocol.ALL.forEach(::add) }
                    .build()
            return MediaSession.ConnectionResult
                .AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        @OptIn(UnstableApi::class) // MediaItem.fromBundle
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                SessionProtocol.PLAY_NEXT.customAction,
                SessionProtocol.ADD_TO_QUEUE.customAction,
                -> {
                    val items =
                        args
                            .getParcelableArrayListCompat(SessionProtocol.ARG_ITEMS)
                            .mapNotNull { resolve(MediaItem.fromBundle(it)) }
                    val playNext = customCommand.customAction == SessionProtocol.PLAY_NEXT.customAction
                    player.insert(items, playNext)
                }
                SessionProtocol.MOVE.customAction ->
                    player.moveInPlayOrder(args.getInt(SessionProtocol.ARG_FROM), args.getInt(SessionProtocol.ARG_TO))
                SessionProtocol.SET_SLEEP_TIMER.customAction ->
                    sleepTimer.start(
                        durationMs = args.getLong(SessionProtocol.ARG_DURATION_MS),
                        finishTrack = args.getBoolean(SessionProtocol.ARG_FINISH_TRACK),
                    )
                SessionProtocol.CANCEL_SLEEP_TIMER.customAction -> sleepTimer.cancel()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * Items arriving from a controller may have lost their URI on the way (Media3 strips
         * `localConfiguration` when items cross process boundaries, e.g. from System UI).
         * Our mediaId is the MediaStore ID, so the playable URI can always be rebuilt from it.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapNotNullTo(mutableListOf(), ::resolve))
    }

    private fun resolve(item: MediaItem): MediaItem? {
        if (item.localConfiguration != null) return item
        val id = item.mediaId.toLongOrNull() ?: return null
        return item
            .buildUpon()
            .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
            .build()
    }

    private companion object {
        const val TAG = "PlaybackService"
        const val SAVE_INTERVAL_MS = 10_000L

        /** Changes worth saving right away. Position while playing is covered by the 10 s save. */
        val SAVE_EVENTS =
            intArrayOf(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
            )
    }
}

@Suppress("DEPRECATION")
private fun Bundle.getParcelableArrayListCompat(key: String): List<Bundle> =
    // The typed overload is API 33+; the untyped one is fine for our own Bundles.
    getParcelableArrayList<Bundle>(key).orEmpty()
