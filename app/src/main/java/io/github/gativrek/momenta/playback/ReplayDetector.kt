package io.github.gativrek.momenta.playback

import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.Moments
import kotlin.math.abs
import kotlin.math.min

/**
 * Notices a section that keeps getting replayed: seeking back to about the same spot twice in
 * one listen of a song is a Moment the user hasn't saved yet. Pure logic, fed by the service.
 */
class ReplayDetector {
    private var trackId: Long? = null
    private val replays = ArrayDeque<LongRange>()

    /** Sections already offered for the current song, by start, so one isn't offered twice. */
    private val offered = mutableListOf<Long>()

    /**
     * A seek on [trackId] from [fromMs] to [toMs]. Returns the section to offer as a Moment, or
     * null. Nothing is offered where the song already has a Moment.
     */
    fun onSeek(
        trackId: Long,
        fromMs: Long,
        toMs: Long,
        moments: List<MomentEntity>,
    ): LongRange? {
        if (trackId != this.trackId) {
            this.trackId = trackId
            replays.clear()
            offered.clear()
        }
        if (fromMs - toMs !in MIN_BACK_MS..MAX_BACK_MS) return null
        val again = replays.any { abs(it.first - toMs) <= SAME_SPOT_MS }
        replays.addLast(toMs..fromMs)
        if (replays.size > KEEP) replays.removeFirst()
        if (!again) return null
        if (offered.any { abs(it - toMs) <= SAME_SPOT_MS }) return null
        if (moments.any { it.startMs < fromMs && toMs < it.endMs }) return null
        val end = min(fromMs, toMs + Moments.SUGGEST_MAX_MS)
        if (end - toMs < Moments.MIN_DURATION_MS) return null
        offered += toMs
        return toMs..end
    }

    private companion object {
        /** Shorter jumps back are nudges, longer ones are starting over. */
        const val MIN_BACK_MS = 2_000L
        const val MAX_BACK_MS = 90_000L

        /** Two replays landing this close count as the same spot. */
        const val SAME_SPOT_MS = 6_000L
        const val KEEP = 6
    }
}
