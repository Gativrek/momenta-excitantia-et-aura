package io.github.gativrek.momenta.playback

import io.github.gativrek.momenta.data.moments.MomentEntity

/**
 * Follows the playback position through a song's Moments. Pure, so it's unit-tested.
 *
 * Feed it the position every tick; it says which Moment is playing (for the volume boost)
 * and reports a Moment heard all the way through: entered near its start and played on
 * past its end. Seeking into the middle of a Moment, or away before it ends, doesn't count.
 */
class MomentTracker {
    private var trackId: Long? = null
    private var entered: MomentEntity? = null
    private var lastPositionMs = -1L

    data class Tick(
        val active: MomentEntity?,
        val heard: MomentEntity?,
    )

    fun update(
        trackId: Long?,
        positionMs: Long,
        moments: List<MomentEntity>,
    ): Tick {
        if (trackId != this.trackId) {
            this.trackId = trackId
            entered = null
            lastPositionMs = -1
        }
        val active = moments.firstOrNull { positionMs in it }
        var heard: MomentEntity? = null

        val previous = entered
        if (previous != null && active?.id != previous.id) {
            // Left the Moment: heard it if playback ran on from its last stretch to past its end.
            val ranOut = positionMs >= previous.endMs && lastPositionMs >= previous.endMs - END_SLACK_MS
            if (ranOut && positionMs - lastPositionMs <= MAX_STEP_MS) heard = previous
            entered = null
        }
        if (active != null && entered == null && positionMs - active.startMs <= START_SLACK_MS) {
            entered = active
        }
        lastPositionMs = positionMs
        return Tick(active, heard)
    }

    private companion object {
        /** Entering this close to the start still counts as hearing it from the start. */
        const val START_SLACK_MS = 1_500L

        /** Polling granularity: the last tick inside may be this far before the end. */
        const val END_SLACK_MS = 1_000L

        /** A bigger jump between ticks is a seek, not playback running on. */
        const val MAX_STEP_MS = 2_000L
    }
}
