package io.github.gativrek.momenta.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.gativrek.momenta.data.library.Artwork
import io.github.gativrek.momenta.data.library.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume

/** What the UI shows about the current track. */
data class NowPlaying(
    val trackId: Long?,
    val title: String,
    val artist: String?,
    val album: String?,
    val artwork: Artwork?,
)

/** A snapshot of the player, as seen through the MediaController. */
data class PlayerUiState(
    val connected: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    val isPlaying: Boolean = false,
    /** True while the user wants playback, even if buffering or paused for a call. */
    val playWhenReady: Boolean = false,
    val durationMs: Long = 0,
    val hasPrevious: Boolean = false,
    /** There's a song before this one (hasPrevious also counts restarting this one). */
    val hasPreviousTrack: Boolean = false,
    val hasNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sleepTimer: SleepTimerState? = null,
)

/** One row of the queue screen. */
data class QueueEntry(
    /** Index in the player's item list: what remove and jump use. */
    val itemIndex: Int,
    /** Position in play order: what move uses. */
    val playPosition: Int,
    val nowPlaying: NowPlaying,
    val durationMs: Long,
)

/** The current track and what plays after it, in play order (shuffle-aware). */
data class QueueState(
    val current: QueueEntry? = null,
    val upcoming: List<QueueEntry> = emptyList(),
)

/**
 * The UI's only door to playback. Wraps a [MediaController] bound to [PlaybackService],
 * the same kind of client Bluetooth and the lock screen use, so if it works here it works there.
 *
 * Lifecycle: [connect] in the Activity's onStart, [disconnect] in onStop. Holding the
 * controller while the app is in the background would keep the service bound, and a bound
 * service can't stop itself after playback ends.
 */
class PlayerConnection(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var sleepTimer: SleepTimerState? = null

    /** Commands issued before the connection finished (e.g. a tap right at launch). */
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _queue = MutableStateFlow(QueueState())

    /** Kept apart from [state]: rebuilding it walks the whole queue, so only on queue changes. */
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private val listener =
        object : Player.Listener {
            // onEvents batches every change in one callback, so the state is rebuilt once per batch.
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                publish(player)
                if (events.containsAny(*QUEUE_EVENTS)) publishQueue(player)
            }
        }

    private val sessionListener =
        object : MediaController.Listener {
            override fun onExtrasChanged(
                controller: MediaController,
                extras: Bundle,
            ) {
                sleepTimer = SessionProtocol.sleepTimerFromExtras(extras)
                publish(controller)
            }
        }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future =
            MediaController
                .Builder(appContext, token)
                .setListener(sessionListener)
                .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                // The future may have been cancelled by disconnect() in the meantime.
                if (controllerFuture !== future) return@addListener
                val connected =
                    try {
                        future.get()
                    } catch (e: ExecutionException) {
                        Log.e(TAG, "Could not connect to PlaybackService", e)
                        return@addListener
                    }
                controller = connected
                connected.addListener(listener)
                sleepTimer = SessionProtocol.sleepTimerFromExtras(connected.sessionExtras)
                publish(connected)
                publishQueue(connected)
                pending.forEach { it(connected) }
                pending.clear()
            },
            // Controller callbacks and commands must run on the main thread.
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun disconnect() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        pending.clear()
        _state.value = PlayerUiState()
        _queue.value = QueueState()
    }

    /**
     * "Shuffle all": a shuffled copy of [tracks], played from the top. The queue itself is
     * shuffled, so every track plays exactly once and the queue reads in play order, whether
     * or not the shuffle toggle is on.
     */
    fun shuffleTracks(tracks: List<Track>) = playTracks(tracks.shuffled(), 0)

    /** Replace the queue with [tracks] and start playing at [startIndex], [startPositionMs] into it. */
    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        /** Moments mode: the service plays only each song's Moments, with their run-ups. */
        reel: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        val items = tracks.map { MediaItemMapper.toMediaItem(it, reel) }
        withController {
            it.setMediaItems(items, startIndex.coerceIn(tracks.indices), startPositionMs)
            it.prepare()
            it.play()
        }
    }

    /** Right after the current track (in play order, so shuffle doesn't scatter it). */
    fun playNext(tracks: List<Track>) = sendInsert(SessionProtocol.PLAY_NEXT, tracks)

    /** At the end of the queue. */
    fun addToQueue(tracks: List<Track>) = sendInsert(SessionProtocol.ADD_TO_QUEUE, tracks)

    fun togglePlayPause() =
        withController {
            // The restored queue isn't prepared until it's first played (no notification at launch).
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            // Restart a finished queue from the top instead of doing nothing.
            if (it.playbackState == Player.STATE_ENDED) it.seekTo(0, 0L)
            if (it.playWhenReady) it.pause() else it.play()
        }

    fun next() = withController { it.seekToNext() }

    /** Standard "previous": restarts the track if a few seconds in, otherwise goes back one. */
    fun previous() = withController { it.seekToPrevious() }

    /** Back one song, however far into this one (swiping the cover). */
    fun previousTrack() = withController { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** Off -> all -> one -> off. */
    fun cycleRepeat() =
        withController {
            it.repeatMode =
                when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
        }

    /** Tap in the queue: play that entry from its start. */
    fun jumpTo(itemIndex: Int) =
        withController {
            if (itemIndex !in 0 until it.mediaItemCount) return@withController
            it.seekTo(itemIndex, 0L)
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            it.play()
        }

    fun removeFromQueue(itemIndex: Int) =
        withController {
            if (itemIndex in 0 until it.mediaItemCount) it.removeMediaItem(itemIndex)
        }

    /** Drag in the queue, between two play positions. */
    fun moveInQueue(
        fromPlayPosition: Int,
        toPlayPosition: Int,
    ) = send(
        SessionProtocol.MOVE,
        Bundle().apply {
            putInt(SessionProtocol.ARG_FROM, fromPlayPosition)
            putInt(SessionProtocol.ARG_TO, toPlayPosition)
        },
    )

    fun setSleepTimer(
        durationMs: Long,
        finishTrack: Boolean,
    ) = send(
        SessionProtocol.SET_SLEEP_TIMER,
        Bundle().apply {
            putLong(SessionProtocol.ARG_DURATION_MS, durationMs)
            putBoolean(SessionProtocol.ARG_FINISH_TRACK, finishTrack)
        },
    )

    fun cancelSleepTimer() = send(SessionProtocol.CANCEL_SLEEP_TIMER, Bundle.EMPTY)

    /**
     * Playback position, polled while collected. Kept out of [state] so only the views that
     * show a seek bar recompose twice a second, not everything observing the player.
     */
    fun positionUpdates(intervalMs: Long = 500): Flow<Long> =
        flow {
            while (true) {
                emit(controller?.currentPosition ?: 0L)
                delay(intervalMs)
            }
        }.distinctUntilChanged()

    @OptIn(UnstableApi::class) // MediaItem.toBundle
    private fun sendInsert(
        command: SessionCommand,
        tracks: List<Track>,
    ) {
        if (tracks.isEmpty()) return
        val bundles = tracks.mapTo(ArrayList()) { MediaItemMapper.toMediaItem(it).toBundle() }
        send(command, Bundle().apply { putParcelableArrayList(SessionProtocol.ARG_ITEMS, bundles) })
    }

    /**
     * Saves a Moment at the current position (the service knows it exactly). Returns the new
     * Moment's ID, or null if there was nothing playing or it couldn't be saved.
     */
    suspend fun saveMoment(section: LongRange? = null): Long? {
        val current = controller ?: return null
        val args =
            section?.let {
                Bundle().apply {
                    putLong(SessionProtocol.ARG_START, it.first)
                    putLong(SessionProtocol.ARG_END, it.last)
                }
            } ?: Bundle.EMPTY
        val result =
            suspendCancellableCoroutine<SessionResult?> { cont ->
                val future = current.sendCustomCommand(SessionProtocol.SAVE_MOMENT, args)
                future.addListener(
                    { cont.resume(runCatching { future.get() }.getOrNull()) },
                    ContextCompat.getMainExecutor(appContext),
                )
            } ?: return null
        if (result.resultCode != SessionResult.RESULT_SUCCESS) return null
        return result.extras.getLong(SessionProtocol.RESULT_MOMENT_ID, -1).takeIf { it >= 0 }
    }

    private fun send(
        command: SessionCommand,
        args: Bundle,
    ) = withController { it.sendCustomCommand(command, args) }

    private fun withController(command: (MediaController) -> Unit) {
        val current = controller
        if (current != null) command(current) else pending += command
    }

    private fun publish(player: Player) {
        val item: MediaItem? = player.currentMediaItem
        _state.value =
            PlayerUiState(
                connected = true,
                nowPlaying = item?.let(::nowPlaying),
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                durationMs = player.duration.takeIf { it > 0 } ?: item?.mediaMetadata?.durationMs ?: 0L,
                hasPrevious = player.hasPreviousMediaItem() || player.currentPosition > 0,
                hasPreviousTrack = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem(),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                sleepTimer = sleepTimer,
            )
    }

    private fun publishQueue(player: Player) {
        val order = PlayOrder.of(player.currentTimeline, player.shuffleModeEnabled)
        val currentPosition = order.indexOf(player.currentMediaItemIndex)
        if (currentPosition < 0) {
            _queue.value = QueueState()
            return
        }

        fun entry(playPosition: Int): QueueEntry {
            val index = order[playPosition]
            val item = player.getMediaItemAt(index)
            return QueueEntry(
                itemIndex = index,
                playPosition = playPosition,
                nowPlaying = nowPlaying(item),
                durationMs = item.mediaMetadata.durationMs ?: 0L,
            )
        }
        _queue.value =
            QueueState(
                current = entry(currentPosition),
                upcoming = (currentPosition + 1 until order.size).map(::entry),
            )
    }

    private fun nowPlaying(item: MediaItem): NowPlaying {
        val metadata = item.mediaMetadata
        return NowPlaying(
            trackId = MediaItemMapper.trackId(item),
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString(),
            album = metadata.albumTitle?.toString(),
            artwork = MediaItemMapper.artwork(item),
        )
    }

    private companion object {
        const val TAG = "PlayerConnection"

        val QUEUE_EVENTS =
            intArrayOf(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            )
    }
}
