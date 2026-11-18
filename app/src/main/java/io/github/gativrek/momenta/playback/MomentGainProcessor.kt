package io.github.gativrek.momenta.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.tanh

/**
 * The Moment volume boost, applied inside the player's own audio pipeline: every sample is
 * multiplied by the gain, then a soft limiter rounds off anything that would clip. Unlike a
 * system audio effect, it works the same on every phone (some, Samsung's among them, ignore
 * or override effects attached to an app's audio session).
 *
 * The gain glides towards its target sample by sample (about a third of a second from 1.0
 * to the full boost), so it swells instead of stepping. At gain 1.0 the audio passes through
 * untouched.
 */
@OptIn(UnstableApi::class)
class MomentGainProcessor : BaseAudioProcessor() {
    @Volatile
    private var target = 1f
    private var current = 1f
    private var step = 0f

    /** Sets the gain to glide to (1.0 = unchanged). Safe to call from any thread. */
    fun setGain(gain: Float) {
        target = gain.coerceIn(1f, MAX_GAIN)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // Full swing in GLIDE_SECONDS, counted in frames (all channels of one instant).
        step = (MAX_GAIN - 1f) / (inputAudioFormat.sampleRate * GLIDE_SECONDS)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size).order(ByteOrder.nativeOrder())
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val channels = inputAudioFormat.channelCount
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (float) 4 else 2
        val frames = size / (bytesPerSample * channels)
        val goal = target
        repeat(frames) {
            // Glide once per frame so every channel of a frame gets the same gain.
            current =
                when {
                    current < goal -> minOf(goal, current + step)
                    current > goal -> maxOf(goal, current - step)
                    else -> current
                }
            repeat(channels) {
                if (float) {
                    out.putFloat(shape(input.getFloat() * current))
                } else {
                    val sample = input.getShort() / SHORT_SCALE * current
                    out.putShort((shape(sample) * SHORT_SCALE).toInt().toShort())
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    /** Untouched below the knee; above it, eased towards full scale so peaks never clip. */
    private fun shape(x: Float): Float {
        if (current == 1f) return x
        val magnitude = abs(x)
        if (magnitude <= KNEE) return x
        val over = (magnitude - KNEE) / (1f - KNEE)
        val shaped = KNEE + (1f - KNEE) * tanh(over)
        return if (x < 0) -shaped else shaped
    }

    override fun onReset() {
        current = 1f
    }

    companion object {
        /** +50% loudness, the most a Moment can ask for, as a gain (see Moments.boostGain). */
        const val MAX_GAIN = 2f
        private const val GLIDE_SECONDS = 0.35f

        /** The limiter starts rounding off peaks here (about -1 dBFS). */
        private const val KNEE = 0.89f
        private const val SHORT_SCALE = 32_768f
    }
}
