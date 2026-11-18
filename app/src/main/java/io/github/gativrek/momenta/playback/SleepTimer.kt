package io.github.gativrek.momenta.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sleep timer (criterion 11). Lives in the service, not a ViewModel, so it keeps
 * running with the UI gone. [onChange] publishes the state to controllers.
 *
 * "Finish current track": when time is up, ExoPlayer's pauseAtEndOfMediaItems pauses
 * exactly at the end of the playing track, before any of the next one is heard.
 */
@OptIn(UnstableApi::class) // pauseAtEndOfMediaItems
class SleepTimer(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val onChange: (SleepTimerState?) -> Unit,
) {
    private var job: Job? = null

    var state: SleepTimerState? = null
        private set(value) {
            field = value
            onChange(value)
        }

    fun start(
        durationMs: Long,
        finishTrack: Boolean,
    ) {
        cancel()
        val end = SystemClock.elapsedRealtime() + durationMs
        state = SleepTimerState(endElapsedMs = end, finishTrack = finishTrack)
        job =
            scope.launch {
                // Re-check the real clock in short steps: a single long delay() counts uptime,
                // which stops while the phone is in deep sleep.
                while (true) {
                    val left = end - SystemClock.elapsedRealtime()
                    if (left <= 0) break
                    delay(left.coerceAtMost(CHECK_INTERVAL_MS))
                }
                timeUp()
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (state?.waitingForTrackEnd == true) player.pauseAtEndOfMediaItems = false
        if (state != null) state = null
    }

    /** Call from the player listener: any pause while waiting means the job is done. */
    fun onPlayerChanged() {
        if (state?.waitingForTrackEnd != true) return
        if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED) cancel()
    }

    private fun timeUp() {
        val current = state ?: return
        val playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        if (current.finishTrack && playing) {
            player.pauseAtEndOfMediaItems = true
            state = current.copy(waitingForTrackEnd = true)
        } else {
            player.pause()
            cancel()
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 1_000L
    }
}
