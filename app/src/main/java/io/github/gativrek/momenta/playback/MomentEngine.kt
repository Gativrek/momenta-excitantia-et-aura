package io.github.gativrek.momenta.playback

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.Moments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * What Moments do to playback: the volume boost while a Moment plays, and counting Moments
 * heard all the way through.
 *
 * The boost is Android's LoudnessEnhancer on the player's audio session (no permission
 * needed). It ramps in and out over a few hundred milliseconds so it swells rather than
 * jumps. Polls the position while playing; idle when paused or the song has no Moments.
 */
class MomentEngine(
    private val exo: ExoPlayer,
    private val scope: CoroutineScope,
    /** Moments of a track, by MediaStore ID. */
    private val momentsOf: (Long) -> List<MomentEntity>,
    private val boostsActive: () -> Boolean,
    private val onHeard: (MomentEntity) -> Unit,
) {
    private val tracker = MomentTracker()
    private var enhancer: LoudnessEnhancer? = null
    private var gainMb = 0
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
            setGain(0)
        }
    }

    fun onAudioSessionIdChanged() {
        release()
        refresh()
    }

    fun release() {
        loop?.cancel()
        loop = null
        enhancer?.release()
        enhancer = null
        gainMb = 0
    }

    private fun tick() {
        val trackId = currentTrackId()
        val result = tracker.update(trackId, exo.currentPosition, trackId?.let(momentsOf).orEmpty())
        result.heard?.let(onHeard)
        val target = result.active?.takeIf { boostsActive() }?.let { Moments.boostGainMb(it.boostPercent) } ?: 0
        // Ramp towards the target a step per tick.
        val next =
            if (abs(target - gainMb) <=
                RAMP_STEP_MB
            ) {
                target
            } else {
                gainMb + RAMP_STEP_MB * Integer.signum(target - gainMb)
            }
        setGain(next)
    }

    private fun setGain(mb: Int) {
        if (mb == gainMb) return
        gainMb = mb
        try {
            val fx = enhancer ?: createEnhancer() ?: return
            fx.setTargetGain(mb)
            fx.enabled = mb > 0
        } catch (e: RuntimeException) {
            // Some devices refuse audio effects: Moments still work, just without the boost.
            Log.w(TAG, "Loudness boost unavailable", e)
            enhancer?.release()
            enhancer = null
        }
    }

    @OptIn(UnstableApi::class) // ExoPlayer.audioSessionId
    private fun createEnhancer(): LoudnessEnhancer? {
        val session = exo.audioSessionId
        if (session == C.AUDIO_SESSION_ID_UNSET) return null
        return LoudnessEnhancer(session).also { enhancer = it }
    }

    private fun currentTrackId(): Long? = exo.currentMediaItem?.let(MediaItemMapper::trackId)

    private companion object {
        const val TAG = "MomentEngine"
        const val TICK_MS = 100L

        /** Full +30% (about 228 mB) in roughly half a second. */
        const val RAMP_STEP_MB = 50
    }
}
