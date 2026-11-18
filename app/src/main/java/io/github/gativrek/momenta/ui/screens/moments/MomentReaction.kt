package io.github.gativrek.momenta.ui.screens.moments

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * How the app reacts while a Moment plays. Everything takes [glow], 0 (no Moment) to 1 (in
 * the Moment), already animated by the caller, so effects swell in and settle out together.
 * Nothing here runs its animations unless glow is above zero.
 */

/** Heavenly light behind the cover: a warm white bloom with slow, turning rays. Draw it behind and larger than the cover. */
@Composable
fun HeavenlyLight(
    glow: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (glow <= 0f) return
    val turn by rememberInfiniteTransition(label = "rays").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(RAYS_TURN_MS, easing = LinearEasing)),
        label = "turn",
    )
    Canvas(modifier) {
        val center = this.center
        val radius = size.minDimension / 2
        // The light: white at the heart, the theme's spark at the edge, fading out.
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.85f * glow),
                    0.35f to color.copy(alpha = 0.55f * glow),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
            radius = radius,
        )
        rotate(turn, center) { drawRays(center, radius, glow) }
    }
}

private fun DrawScope.drawRays(
    center: Offset,
    radius: Float,
    glow: Float,
) {
    val brush =
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.5f * glow),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        )
    val spread = (2 * PI / RAYS).toFloat()
    for (i in 0 until RAYS) {
        val a = i * spread
        // Alternate wide and narrow rays, like light through clouds.
        val half = spread * if (i % 2 == 0) 0.22f else 0.1f
        val path =
            Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + radius * cos(a - half), center.y + radius * sin(a - half))
                lineTo(center.x + radius * cos(a + half), center.y + radius * sin(a + half))
                close()
            }
        drawPath(path, brush, blendMode = BlendMode.Plus)
    }
}

/**
 * The song title, catching fire during a Moment: flames run up through the letters, the
 * text glows, and embers rise off it. Outside a Moment it's the plain title.
 */
@Composable
fun FieryTitle(
    text: String,
    glow: Float,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    // Always one line: a long title scrolls (both layers together) instead of pushing the
    // controls below it down.
    Box(modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = MARQUEE_DELAY_MS)) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer { alpha = 1f - glow },
        )
        if (glow > 0f) {
            val transition = rememberInfiniteTransition(label = "fire")
            val flicker by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(FLAME_MS, easing = LinearEasing)),
                label = "flame",
            )
            val time by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(EMBER_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
                label = "embers",
            )
            // Flames: a fire gradient scrolling upward through the letters.
            val flames =
                object : ShaderBrush() {
                    override fun createShader(size: Size) =
                        LinearGradientShader(
                            from = Offset(0f, size.height * (1f - flicker)),
                            to = Offset(0f, size.height * (1f - flicker) - size.height),
                            colors = FIRE,
                            tileMode = TileMode.Mirror,
                        )
                }
            Text(
                text = text,
                style =
                    style.copy(
                        brush = flames,
                        shadow = Shadow(color = FIRE_GLOW.copy(alpha = 0.9f * glow), blurRadius = 28f),
                    ),
                maxLines = 1,
                softWrap = false,
                modifier =
                    Modifier
                        .graphicsLayer { alpha = glow }
                        .drawBehind { drawEmbers(time, glow) },
            )
        }
    }
}

private fun DrawScope.drawEmbers(
    time: Float,
    glow: Float,
) {
    for (i in 0 until EMBERS) {
        // Each ember has its own lane, speed and phase, derived from its index: no allocation, no randomness per frame.
        val lane = ((i * 0.618034f) % 1f)
        val speed = 0.6f + (i % 5) * 0.15f
        val phase = ((time * speed + i * 0.37f) % 1f)
        val x = size.width * lane + sin((phase * 6 + i) * 1.3f) * 6f
        val y = size.height * (1f - phase * 1.6f)
        val fade = (1f - phase) * glow
        val r = 2.5f + (i % 3)
        drawCircle(EMBER.copy(alpha = fade), radius = r, center = Offset(x, y), blendMode = BlendMode.Plus)
    }
}

/** A soft glow from behind the cover over the whole screen: the aura brightening. */
fun Modifier.momentAura(
    glow: Float,
    color: Color,
): Modifier =
    if (glow <= 0f) {
        this
    } else {
        drawBehind {
            drawRect(
                Brush.radialGradient(
                    0f to color.copy(alpha = 0.35f * glow),
                    1f to Color.Transparent,
                    center = Offset(size.width / 2, size.height * 0.32f),
                    radius = size.maxDimension * 0.7f,
                ),
            )
        }
    }

private const val RAYS = 14
private const val MARQUEE_DELAY_MS = 1_500
private const val RAYS_TURN_MS = 40_000
private const val FLAME_MS = 900
private const val EMBER_CYCLE_MS = 2_400
private const val EMBERS = 18

private val FIRE =
    listOf(
        Color(0xFFFFF4C8), // white-hot
        Color(0xFFFFC233),
        Color(0xFFFF7A1A),
        Color(0xFFE5361B),
        Color(0xFFFF7A1A),
        Color(0xFFFFC233),
    )
private val FIRE_GLOW = Color(0xFFFF6A00)
private val EMBER = Color(0xFFFFB347)
