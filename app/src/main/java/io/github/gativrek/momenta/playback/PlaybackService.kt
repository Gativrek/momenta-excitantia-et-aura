package io.github.gativrek.momenta.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.MainActivity
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.listening.PlayEntity
import io.github.gativrek.momenta.data.moments.MomentSuggestion
import io.github.gativrek.momenta.widget.NowPlayingWidgets
import io.github.gativrek.momenta.widget.WidgetBridge
import io.github.gativrek.momenta.widget.WidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Owns the one ExoPlayer and its MediaSession. The service owns the player, never an Activity or ViewModel.
 *
 * Everything that controls playback, our own UI included, goes through the session:
 * the notification, lock screen, Bluetooth buttons and car displays all use the same path.
 * MediaSessionService handles the foreground-service lifecycle and the media notification
 * for us: it goes foreground while playing and drops back when paused.
 *
 * Also home to what must outlive the UI: the sleep timer, saving/restoring the queue and the
 * listening log.
 */
@OptIn(UnstableApi::class) // ExoPlayer shuffle order and notification icon
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: QueuePlayer
    private lateinit var sleepTimer: SleepTimer
    private lateinit var momentEngine: MomentEngine

    /** Main-thread scope for player work; cancelled in onDestroy. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as App).container

    /** False until the saved queue has been restored, so an empty player never overwrites it. */
    private var restored = false
    private var periodicSave: Job? = null

    /** The library as last seen, to name what was listened to. */
    private var library: Map<Long, Track> = emptyMap()
    private val replays = ReplayDetector()

    /** The home-screen widget's cover, for the track it was loaded for. */
    private var widgetCover: Pair<Long?, Bitmap?>? = null
    private var widgetJob: Job? = null

    /** The Moment volume boost, in the audio pipeline (see MomentGainProcessor). */
    private val momentGain = MomentGainProcessor()

    /** ExoPlayer's renderers with [momentGain] in its audio sink, after decoding. */
    private fun renderersWithMomentGain() =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink
                    .Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(momentGain))
                    .build()
        }

    private val playTracker =
        PlayTracker(SystemClock::elapsedRealtime, System::currentTimeMillis) { listen -> record(listen) }

    @OptIn(UnstableApi::class) // DefaultMediaNotificationProvider.setSmallIcon
    override fun onCreate() {
        super.onCreate()

        val exoPlayer =
            ExoPlayer
                .Builder(this, renderersWithMomentGain())
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

        // Save a Moment from the media notification and lock screen: Moments happen with the phone in a pocket.
        val saveMomentButton =
            CommandButton
                .Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_moment)
                .setDisplayName(getString(R.string.moment_save))
                .setSessionCommand(SessionProtocol.SAVE_MOMENT)
                .build()
        val session =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(openApp)
                .setCallback(SessionCallback())
                .setMediaButtonPreferences(listOf(saveMomentButton))
                .build()
        mediaSession = session

        sleepTimer =
            SleepTimer(exoPlayer, scope) { state ->
                session.setSessionExtras(SessionProtocol.sleepTimerToExtras(state))
            }
        momentEngine =
            MomentEngine(
                exo = exoPlayer,
                gain = momentGain,
                scope = scope,
                momentsOf = { id ->
                    container.momentsRepository.byTrack.value[id]
                        .orEmpty()
                },
                boostsActive = { container.momentsSettings.settings.value.boostsActive },
                onHeard = { moment ->
                    container.applicationScope.launch { container.momentsRepository.countHeard(moment.id) }
                },
            )
        exoPlayer.addListener(PlayerEvents())

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )

        restoreQueue()
        followLibrary()
        followMoments()
        WidgetBridge.refresh = ::updateWidget
        WidgetBridge.saveMoment = ::saveMomentFromWidget
        updateWidget()
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
        WidgetBridge.refresh = null
        WidgetBridge.saveMoment = null
        if (NowPlayingWidgets.hasAny(this)) {
            NowPlayingWidgets.push(this, widgetState().copy(playing = false), widgetCover?.second)
        }
        playTracker.flush()
        saveQueue()
        sleepTimer.cancel()
        momentEngine.release()
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
            container.libraryRepository.tracks.collect { tracks ->
                library = tracks.associateBy { it.id }
                refreshQueue(library)
            }
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
                    val fresh = MediaItemMapper.toMediaItem(track, MediaItemMapper.isReel(old))
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

    /** New or edited Moments, or the boost switches, take effect on the playing song at once. */
    private fun followMoments() {
        scope.launch {
            combine(container.momentsRepository.byTrack, container.momentsSettings.settings) { _, _ -> }
                .collect { momentEngine.refresh() }
        }
    }

    /**
     * A Moment around the current position of the current track, or exactly [section] of it;
     * completes with its ID, or null.
     */
    private fun saveMoment(section: LongRange? = null): ListenableFuture<Long?> {
        val result = SettableFuture.create<Long?>()
        val track = player.currentMediaItem?.let(MediaItemMapper::trackId)?.let(library::get)
        if (track == null) {
            result.set(null)
            return result
        }
        val position = player.currentPosition
        container.applicationScope.launch {
            try {
                val repository = container.momentsRepository
                val moment =
                    if (section != null) {
                        repository.save(track, section.first, section.last)
                    } else {
                        repository.saveAt(track, position)
                    }
                result.set(moment.id)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save a Moment", e)
                result.set(null)
            }
        }
        return result
    }

    private fun widgetState(): WidgetState {
        val item = player.currentMediaItem
        return WidgetState(
            title = item?.mediaMetadata?.title?.toString(),
            artist = item?.mediaMetadata?.artist?.toString(),
            playing = item != null && player.playWhenReady && player.playbackState != Player.STATE_ENDED,
        )
    }

    /** Shows the current song and play state on the home-screen widgets, if there are any. */
    private fun updateWidget() {
        if (!NowPlayingWidgets.hasAny(this)) return
        val item = player.currentMediaItem
        val state = widgetState()
        val trackId = item?.let(MediaItemMapper::trackId)
        val cached = widgetCover
        if (cached != null && cached.first == trackId) {
            NowPlayingWidgets.push(this, state, cached.second)
            return
        }
        widgetJob?.cancel()
        widgetJob =
            scope.launch {
                val art = item?.let(MediaItemMapper::artwork)
                val cover =
                    art?.let {
                        withContext(Dispatchers.IO) {
                            NowPlayingWidgets.loadCover(this@PlaybackService, it.trackId, it.albumId)
                        }
                    }
                widgetCover = trackId to cover
                NowPlayingWidgets.push(this@PlaybackService, widgetState(), cover)
            }
    }

    /** The widget's bolt: like the notification's, with a toast to say what happened. */
    private fun saveMomentFromWidget() {
        if (player.currentMediaItem == null) {
            Toast.makeText(this, R.string.widget_nothing_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        val saving = saveMoment()
        saving.addListener(
            {
                val id = runCatching { saving.get() }.getOrNull()
                val message = if (id != null) R.string.moment_saved else R.string.moment_save_failed
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    /** Into the listening log. Application scope: a listen flushed in onDestroy is still written. */
    private fun record(listen: Listen) {
        val track = library[listen.trackId] ?: return
        val play =
            PlayEntity(
                songId = 0,
                startedAt = listen.startedAt,
                listenedMs = listen.listenedMs,
                completed = listen.completed,
            )
        container.applicationScope.launch {
            try {
                container.database.listeningDao().record(track, play)
            } catch (e: Exception) {
                // History is a nice-to-have: never let it disturb playback.
                Log.w(TAG, "Couldn't record a listen", e)
            }
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

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            val completed =
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            playTracker.onTrackChanged(mediaItem?.let(MediaItemMapper::trackId), completed, player.isPlaying)
            momentEngine.onTrackChanged()
            momentEngine.refresh()
        }

        /** Seeking back to the same spot again: offer that section as a Moment. */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) return
            if (MediaItemMapper.isReel(player.currentMediaItem)) return // Moments mode seeks by itself
            val trackId = player.currentMediaItem?.let(MediaItemMapper::trackId) ?: return
            val moments =
                container.momentsRepository.byTrack.value[trackId]
                    .orEmpty()
            val section =
                replays.onSeek(trackId, oldPosition.positionMs, newPosition.positionMs, moments) ?: return
            container.momentsRepository.suggest(MomentSuggestion(trackId, section.first, section.last))
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playTracker.onPlayingChanged(isPlaying, player.currentMediaItem?.let(MediaItemMapper::trackId))
            momentEngine.refresh()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) playTracker.onEnded()
        }

        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) {
            sleepTimer.onPlayerChanged()
            if (events.containsAny(*SAVE_EVENTS)) saveQueue()
            if (events.containsAny(*WIDGET_EVENTS)) updateWidget()
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
            if (session.isMediaNotificationController(controller)) {
                // The notification (and through it the lock screen and System UI) gets the Moment button.
                val commands =
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(SessionProtocol.SAVE_MOMENT)
                        .build()
                return MediaSession.ConnectionResult
                    .AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands)
                    .build()
            }
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
                SessionProtocol.SAVE_MOMENT.customAction -> {
                    // From our UI the screen shows what happened; from the notification, a toast says so.
                    val fromNotification = session.isMediaNotificationController(controller)
                    return Futures.transform(
                        saveMoment(
                            args
                                .takeIf { it.containsKey(SessionProtocol.ARG_START) }
                                ?.let { it.getLong(SessionProtocol.ARG_START)..it.getLong(SessionProtocol.ARG_END) },
                        ),
                        { id -> momentResult(id, fromNotification) },
                        ContextCompat.getMainExecutor(this@PlaybackService),
                    )
                }
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        private fun momentResult(
            id: Long?,
            toast: Boolean,
        ): SessionResult {
            if (toast) {
                val message = if (id != null) R.string.moment_saved else R.string.moment_save_failed
                Toast.makeText(this@PlaybackService, message, Toast.LENGTH_SHORT).show()
            }
            if (id == null) return SessionResult(SessionError.ERROR_UNKNOWN)
            return SessionResult(
                SessionResult.RESULT_SUCCESS,
                Bundle().apply { putLong(SessionProtocol.RESULT_MOMENT_ID, id) },
            )
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
        val WIDGET_EVENTS =
            intArrayOf(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
            )

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
