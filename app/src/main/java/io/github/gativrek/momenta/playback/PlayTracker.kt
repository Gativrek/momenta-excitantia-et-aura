package io.github.gativrek.momenta.playback

/** A finished listen, ready for the listening log. */
data class Listen(
    val trackId: Long,
    /** Wall-clock start, epoch ms. */
    val startedAt: Long,
    val listenedMs: Long,
    val completed: Boolean,
)

/**
 * Turns player events into listens. Pure (clocks are passed in) so it's unit-tested.
 *
 * One listen runs from the moment a track becomes current until the next track takes over
 * or playback ends. Only time actually playing counts: pauses, buffering and seeking add
 * nothing. A listen that never played (or less than [MIN_LISTEN_MS]) isn't reported: the
 * queue restored at startup, or a track flicked past while choosing, isn't a listen.
 */
class PlayTracker(
    /** Monotonic time for measuring, e.g. SystemClock.elapsedRealtime. */
    private val elapsed: () -> Long,
    /** Wall-clock time for stamping, e.g. System.currentTimeMillis. */
    private val wall: () -> Long,
    private val onListen: (Listen) -> Unit,
) {
    private var trackId: Long? = null
    private var startedAt: Long? = null
    private var listenedMs = 0L
    private var playingSince: Long? = null

    /** The current track changed (or the queue was replaced). [completed]: the previous one played to its end. */
    fun onTrackChanged(
        newTrackId: Long?,
        completed: Boolean,
        isPlaying: Boolean,
    ) {
        finish(completed)
        trackId = newTrackId
        if (isPlaying) resume()
    }

    fun onPlayingChanged(
        isPlaying: Boolean,
        currentTrackId: Long?,
    ) {
        if (isPlaying) {
            // Playing again after the queue ended (or before any transition was seen): a new listen.
            if (trackId == null) trackId = currentTrackId
            resume()
        } else {
            pause()
        }
    }

    /** The queue played to its end: the last track completed. */
    fun onEnded() = finish(completed = true)

    /** The player is going away: report what was heard so far. */
    fun flush() = finish(completed = false)

    private fun resume() {
        if (trackId == null || playingSince != null) return
        if (startedAt == null) startedAt = wall()
        playingSince = elapsed()
    }

    private fun pause() {
        playingSince?.let { listenedMs += elapsed() - it }
        playingSince = null
    }

    private fun finish(completed: Boolean) {
        pause()
        val id = trackId
        val start = startedAt
        if (id != null && start != null && listenedMs >= MIN_LISTEN_MS) {
            onListen(Listen(id, start, listenedMs, completed))
        }
        trackId = null
        startedAt = null
        listenedMs = 0
    }

    companion object {
        const val MIN_LISTEN_MS = 1_000L
    }
}
