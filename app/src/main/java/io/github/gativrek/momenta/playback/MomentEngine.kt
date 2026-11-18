package io.github.gativrek.momenta.playback

import androidx.media3.exoplayer.ExoPlayer
import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.Moments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * What Moments do to playback: the volume boost while a Moment plays, and counting Moments
 * heard all the way through.
 *
 * The boost is [MomentGainProcessor], in the player's own audio pipeline, which glides in and
 * out so the boost swells rather than jumps. Polls the position while playing; idle when
 * paused or the song has no Moments.
 *
 * Moments mode (items queued as a reel, see MediaItemMapper.isReel) plays only the Moments:
 * each song starts at its first Moment's run-up, plays on a few seconds past the Moment, fades
 * out and hands over to the next Moment (later in the same song, or the next song), which
 * fades in. A hype reel of the library.
 */
class MomentEngine(
    private val exo: ExoPlayer,
    private val gain: MomentGainProcessor,
    private val scope: CoroutineScope,
    /** Moments of a track, by MediaStore ID. */
    private val momentsOf: (Long) -> List<MomentEntity>,
    private val boostsActive: () -> Boolean,
    private val onHeard: (MomentEntity) -> Unit,
) {
    private val tracker = MomentTracker()
    private var loop: Job? = null

    /** Call when playing starts or stops, the track changes, or Moments or settings change. */
    fun refresh() {
        val running = exo.isPlaying && momentsOf(currentTrackId() ?: -1).isNotEmpty()
        if (running && loop?.isActive != true) {
            loop =
                scope.launch {
                    while (isActive) {
                        tick()
                        delay(TICK_MS)
                    }
                }
        } else if (!running && loop != null) {
            loop?.cancel()
            loop = null
            // Paused or no Moments: no boost left behind (the next play starts unboosted, then ramps).
            gain.setGain(1f)
            exo.volume = 1f
        }
    }

    /** A new item is playing: in Moments mode, go straight to its first Moment's run-up. */
    fun onTrackChanged() {
        if (!MediaItemMapper.isReel(exo.currentMediaItem)) {
            exo.volume = 1f
            return
        }
        val first = momentsOf(currentTrackId() ?: return).firstOrNull() ?: return
        if (exo.currentPosition < first.runUpStartMs - REEL_SLACK_MS) exo.seekTo(first.runUpStartMs)
    }

    fun release() {
        loop?.cancel()
        loop = null
        gain.setGain(1f)
    }

    private fun tick() {
        val trackId = currentTrackId()
        val moments = trackId?.let(momentsOf).orEmpty()
        if (MediaItemMapper.isReel(exo.currentMediaItem)) reelTick(exo.currentPosition, moments)
        val result = tracker.update(trackId, exo.currentPosition, moments)
        result.heard?.let(onHeard)
        gain.setGain(result.active?.takeIf { boostsActive() }?.let { Moments.boostGain(it.boostPercent) } ?: 1f)
    }

    /** Moments mode: fade out after each Moment and cut to the next one. */
    private fun reelTick(
        positionMs: Long,
        moments: List<MomentEntity>,
    ) {
        if (moments.isEmpty()) return
        // The Moment playing (or just played), and the next one in this song.
        val current = moments.lastOrNull { it.runUpStartMs <= positionMs + REEL_SLACK_MS }
        val following = moments.firstOrNull { it.runUpStartMs > positionMs + REEL_SLACK_MS }
        if (current == null) {
            // Before the first run-up (e.g. seeked back): skip ahead to it.
            following?.let { exo.seekTo(it.runUpStartMs) }
            return
        }
        val outAt = current.endMs + Moments.REEL_TAIL_MS
        if (following != null && following.runUpStartMs <= outAt) {
            // The next Moment's run-up starts before this one would fade: play straight through.
            fadeIn()
            return
        }
        val left = outAt - positionMs
        when {
            left <= 0 -> {
                exo.volume = 0f
                when {
                    following != null -> exo.seekTo(following.runUpStartMs)
                    exo.hasNextMediaItem() -> exo.seekToNextMediaItem() // onTrackChanged finds its run-up
                    else -> {
                        // The reel is over.
                        exo.pause()
                        exo.volume = 1f
                    }
                }
            }
            left < REEL_FADE_MS -> exo.volume = left.toFloat() / REEL_FADE_MS
            else -> fadeIn()
        }
    }

    private fun fadeIn() {
        if (exo.volume < 1f) exo.volume = (exo.volume + FADE_IN_STEP).coerceAtMost(1f)
    }

    private fun currentTrackId(): Long? = exo.currentMediaItem?.let(MediaItemMapper::trackId)

    private companion object {
        const val TICK_MS = 100L

        /** Moments mode: the fade out at the end of each Moment's tail, and the fade in (per tick). */
        const val REEL_FADE_MS = 1_500L
        const val FADE_IN_STEP = 0.12f

        /** Positions this close to a run-up count as at it (seeks land a little off). */
        const val REEL_SLACK_MS = 1_000L
    }
}
