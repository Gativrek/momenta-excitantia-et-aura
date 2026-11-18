package io.github.gativrek.momenta.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
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
import java.util.concurrent.ExecutionException

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
    val hasNext: Boolean = false,
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

    /** Commands issued before the connection finished (e.g. a tap right at launch). */
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener =
        object : Player.Listener {
            // onEvents batches every change in one callback, so the state is rebuilt once per batch.
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                publish(player)
            }
        }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
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
                publish(connected)
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
    }

    /**
     * "Shuffle all": a shuffled copy of [tracks], played from the top. The queue itself is
     * shuffled (not the player's shuffle mode), so every track plays exactly once and the
     * queue reads in play order. The shuffle toggle (Phase 4) uses Media3's mode instead.
     */
    fun shuffleTracks(tracks: List<Track>) = playTracks(tracks.shuffled(), 0)

    /** Replace the queue with [tracks] and start playing at [startIndex]. */
    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
    ) {
        if (tracks.isEmpty()) return
        val items = tracks.map(MediaItemMapper::toMediaItem)
        withController {
            it.setMediaItems(items, startIndex.coerceIn(tracks.indices), 0L)
            it.prepare()
            it.play()
        }
    }

    fun togglePlayPause() =
        withController {
            // Restart a finished queue from the top instead of doing nothing.
            if (it.playbackState == Player.STATE_ENDED) it.seekTo(0, 0L)
            if (it.playWhenReady) it.pause() else it.play()
        }

    fun next() = withController { it.seekToNext() }

    /** Standard "previous": restarts the track if a few seconds in, otherwise goes back one. */
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

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
                hasNext = player.hasNextMediaItem(),
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
    }
}
