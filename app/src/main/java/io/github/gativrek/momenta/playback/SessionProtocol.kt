package io.github.gativrek.momenta.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * What our UI can ask the service beyond the standard Player commands, and what the service
 * reports back. Custom commands are only granted to controllers from our own package.
 */
object SessionProtocol {
    private const val PREFIX = "io.github.gativrek.momenta."

    /** Args: [ARG_ITEMS] (MediaItem bundles). Inserts right after the current track in play order. */
    val PLAY_NEXT = SessionCommand(PREFIX + "PLAY_NEXT", Bundle.EMPTY)

    /** Args: [ARG_ITEMS]. Appends to the end of the play order. */
    val ADD_TO_QUEUE = SessionCommand(PREFIX + "ADD_TO_QUEUE", Bundle.EMPTY)

    /** Args: [ARG_FROM], [ARG_TO], both play positions. */
    val MOVE = SessionCommand(PREFIX + "MOVE", Bundle.EMPTY)

    /** Args: [ARG_DURATION_MS], [ARG_FINISH_TRACK]. */
    val SET_SLEEP_TIMER = SessionCommand(PREFIX + "SET_SLEEP_TIMER", Bundle.EMPTY)

    val CANCEL_SLEEP_TIMER = SessionCommand(PREFIX + "CANCEL_SLEEP_TIMER", Bundle.EMPTY)

    val ALL = listOf(PLAY_NEXT, ADD_TO_QUEUE, MOVE, SET_SLEEP_TIMER, CANCEL_SLEEP_TIMER)

    const val ARG_ITEMS = "items"
    const val ARG_FROM = "from"
    const val ARG_TO = "to"
    const val ARG_DURATION_MS = "duration_ms"
    const val ARG_FINISH_TRACK = "finish_track"

    // Session extras: the sleep timer's state, pushed to every controller.
    private const val EXTRA_SLEEP_END = PREFIX + "SLEEP_END_ELAPSED"
    private const val EXTRA_SLEEP_FINISH_TRACK = PREFIX + "SLEEP_FINISH_TRACK"
    private const val EXTRA_SLEEP_WAITING = PREFIX + "SLEEP_WAITING"

    fun sleepTimerToExtras(state: SleepTimerState?): Bundle =
        Bundle().apply {
            if (state != null) {
                putLong(EXTRA_SLEEP_END, state.endElapsedMs)
                putBoolean(EXTRA_SLEEP_FINISH_TRACK, state.finishTrack)
                putBoolean(EXTRA_SLEEP_WAITING, state.waitingForTrackEnd)
            }
        }

    fun sleepTimerFromExtras(extras: Bundle): SleepTimerState? {
        if (!extras.containsKey(EXTRA_SLEEP_END)) return null
        return SleepTimerState(
            endElapsedMs = extras.getLong(EXTRA_SLEEP_END),
            finishTrack = extras.getBoolean(EXTRA_SLEEP_FINISH_TRACK),
            waitingForTrackEnd = extras.getBoolean(EXTRA_SLEEP_WAITING),
        )
    }
}

/** An armed sleep timer. Times are `SystemClock.elapsedRealtime()`, which keeps counting in deep sleep. */
data class SleepTimerState(
    val endElapsedMs: Long,
    val finishTrack: Boolean,
    /** Time is up and "finish current track" is on: playback stops when this track ends. */
    val waitingForTrackEnd: Boolean = false,
)
