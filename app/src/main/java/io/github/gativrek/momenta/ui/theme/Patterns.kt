package io.github.gativrek.momenta.ui.theme

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** At full pattern strength the pattern is drawn at this opacity: present, never louder than the text. */
private const val MAX_PATTERN_ALPHA = 0.32f

/** At full aura the top glow starts at this opacity. */
private const val MAX_AURA_ALPHA = 0.42f

/**
 * The themed backdrop: background color, the aura glow, then the pattern. Its own graphics
 * layer, so scrolling a list over it replays the recorded drawing instead of redrawing it;
 * the paths themselves are only rebuilt when the size or the theme changes.
 */
@Composable
fun ThemeBackdrop(
    spec: ThemeSpec,
    modifier: Modifier = Modifier,
) {
    Spacer(modifier = modifier.graphicsLayer().themeBackdrop(spec))
}

fun Modifier.themeBackdrop(spec: ThemeSpec): Modifier =
    drawWithCache {
        val marks = buildPattern(spec.pattern, size, density)
        val auraTop =
            Brush.radialGradient(
                colors = listOf(spec.accent.copy(alpha = spec.aura * MAX_AURA_ALPHA), spec.accent.copy(alpha = 0f)),
                center = Offset(size.width * 0.5f, -size.height * 0.04f),
                radius = max(size.width, size.height) * 0.7f,
            )
        val auraBottom =
            Brush.radialGradient(
                colors =
                    listOf(
                        spec.spark.copy(alpha = spec.aura * MAX_AURA_ALPHA * 0.5f),
                        spec.spark.copy(alpha = 0f),
                    ),
                center = Offset(size.width * 1.05f, size.height * 1.02f),
                radius = size.width * 0.95f,
            )
        val patternAlpha = spec.patternStrength * MAX_PATTERN_ALPHA
        onDrawBehind {
            drawRect(spec.background)
            if (spec.aura > 0f) {
                drawRect(auraTop)
                drawRect(auraBottom)
            }
            if (patternAlpha > 0f) {
                marks.forEach { it.draw(this, spec, patternAlpha) }
            }
        }
    }

/** One layer of a pattern: a path drawn filled or stroked at a fraction of the pattern's opacity. */
private class Mark(
    val path: Path,
    val strokeWidth: Float? = null,
    val alpha: Float = 1f,
) {
    fun draw(
        scope: DrawScope,
        spec: ThemeSpec,
        patternAlpha: Float,
    ) {
        val style = strokeWidth?.let { Stroke(width = it, cap = StrokeCap.Round, join = StrokeJoin.Miter) } ?: Fill
        scope.drawPath(path, color = spec.patternColor, alpha = (patternAlpha * alpha).coerceIn(0f, 1f), style = style)
    }
}

/**
 * Builds a pattern for a canvas of [size] pixels. Every random choice comes from a fixed
 * seed, so a pattern looks the same every time the app opens and in every preview.
 */
private fun buildPattern(
    pattern: Pattern,
    size: Size,
    density: Float,
): List<Mark> {
    if (size.width <= 0f || size.height <= 0f) return emptyList()
    val p = PatternCanvas(size.width, size.height, density, Random(pattern.ordinal * 7919 + 17))
    return when (pattern) {
        Pattern.Halo -> p.halo()
        Pattern.Fracture -> p.fracture()
        Pattern.Radar -> p.radar()
        Pattern.Bars -> p.bars()
        Pattern.Speed -> p.speed()
        Pattern.Slash -> p.slash()
        Pattern.Embers -> p.embers()
        Pattern.Rain -> p.rain()
        Pattern.Tears -> p.tears()
        Pattern.Domain -> p.domain()
        Pattern.None -> emptyList()
    }
}

private class PatternCanvas(
    val w: Float,
    val h: Float,
    /** Pixels per dp: line widths and spacing are in dp so patterns read the same on any screen. */
    val dp: Float,
    val random: Random,
) {
    fun rnd(
        from: Float,
        to: Float,
    ) = from + random.nextFloat() * (to - from)

    /**
     * Concentric halos with a ring of tick marks and rays, rising from below the bottom edge
     * like a dawn: the way home, the "new dreams" after the long night.
     */
    fun halo(): List<Mark> {
        val c = Offset(w * 0.5f, h * 1.04f)
        val marks = mutableListOf<Mark>()
        repeat(8) { i ->
            val r = (56 + i * 52) * dp
            marks += Mark(Path().apply { addOval(Rect(c, r)) }, (if (i == 2) 1.5f else 1f) * dp, 1f - i * 0.09f)
        }
        val ticks = Path()
        val tickRadius = (56 + 2 * 52) * dp
        repeat(72) { i ->
            val a = i * 2 * PI / 72
            val len = (if (i % 6 == 0) 14 else 6) * dp
            ticks.moveTo(c.x + cos(a).toFloat() * tickRadius, c.y + sin(a).toFloat() * tickRadius)
            ticks.lineTo(c.x + cos(a).toFloat() * (tickRadius + len), c.y + sin(a).toFloat() * (tickRadius + len))
        }
        marks += Mark(ticks, 1f * dp)
        val rays = Path()
        repeat(12) { i ->
            val a = i * 2 * PI / 12 + PI / 12
            rays.moveTo(c.x + cos(a).toFloat() * 40 * dp, c.y + sin(a).toFloat() * 40 * dp)
            rays.lineTo(c.x + cos(a).toFloat() * 420 * dp, c.y + sin(a).toFloat() * 420 * dp)
        }
        marks += Mark(rays, 0.75f * dp, 0.5f)
        val stars = Path()
        repeat(16) { star(stars, Offset(rnd(0f, w), rnd(0f, h)), rnd(2f, 5f) * dp) }
        marks += Mark(stars, alpha = 1.4f)
        return marks
    }

    /** Cracks running out from one point of impact, the way stone gives way. */
    fun fracture(): List<Mark> {
        val origin = Offset(w * 0.7f, h * 0.27f)
        val main = Path()
        val branches = Path()
        val count = 11
        repeat(count) { i ->
            var angle = i * 2 * PI / count + rnd(-0.2f, 0.2f)
            var point = origin
            main.moveTo(point.x, point.y)
            var steps = 0
            while (steps < 40 && point.x in -40f..w + 40f && point.y in -40f..h + 40f) {
                angle += rnd(-0.45f, 0.45f)
                val len = rnd(18f, 42f) * dp
                point = Offset(point.x + cos(angle).toFloat() * len, point.y + sin(angle).toFloat() * len)
                main.lineTo(point.x, point.y)
                if (random.nextFloat() < 0.22f) crack(branches, point, angle + rnd(-1.1f, 1.1f), random.nextInt(3, 8))
                steps++
            }
        }
        val impact = Path()
        repeat(7) { i ->
            val a = i * 2 * PI / 7
            val r = rnd(8f, 16f) * dp
            val x = origin.x + cos(a).toFloat() * r
            val y = origin.y + sin(a).toFloat() * r
            if (i == 0) impact.moveTo(x, y) else impact.lineTo(x, y)
        }
        impact.close()
        return listOf(Mark(main, 1.4f * dp), Mark(branches, 0.8f * dp, 0.7f), Mark(impact, 1.2f * dp))
    }

    private fun crack(
        path: Path,
        start: Offset,
        startAngle: Double,
        steps: Int,
    ) {
        var angle = startAngle
        var point = start
        path.moveTo(point.x, point.y)
        repeat(steps) {
            angle += rnd(-0.5f, 0.5f)
            val len = rnd(10f, 24f) * dp
            point = Offset(point.x + cos(angle).toFloat() * len, point.y + sin(angle).toFloat() * len)
            path.lineTo(point.x, point.y)
        }
    }

    /** A hangar floor running to the horizon, and a radar sweeping above it. */
    fun radar(): List<Mark> {
        val horizon = h * 0.62f
        val vanish = Offset(w * 0.5f, horizon)
        val floor = Path()
        var x = -w
        while (x <= 2 * w) {
            floor.moveTo(vanish.x, vanish.y)
            floor.lineTo(x, h)
            x += 44 * dp
        }
        repeat(12) { i ->
            val t = (i + 1) / 12f
            val y = horizon + (h - horizon) * t.pow(2)
            floor.moveTo(0f, y)
            floor.lineTo(w, y)
        }
        val radarCenter = Offset(w * 0.14f, h * 0.16f)
        val rings = Path()
        repeat(5) { i -> rings.addOval(Rect(radarCenter, (48 + i * 48) * dp)) }
        val sweep = Path()
        val sweepAngle = -PI / 5
        sweep.moveTo(radarCenter.x, radarCenter.y)
        sweep.lineTo(
            radarCenter.x + cos(sweepAngle).toFloat() * 240 * dp,
            radarCenter.y - sin(sweepAngle).toFloat() * 240 * dp,
        )
        val blips = Path()
        repeat(6) {
            val a = rnd(-0.9f, 0.1f)
            val r = rnd(40f, 230f) * dp
            blips.addOval(Rect(Offset(radarCenter.x + cos(a) * r, radarCenter.y - sin(a) * r), rnd(2f, 4f) * dp))
        }
        return listOf(
            Mark(floor, 1f * dp, 0.8f),
            Mark(rings, 1f * dp, 0.7f),
            Mark(sweep, 2f * dp),
            Mark(blips, alpha = 1.5f),
        )
    }

    /** Lines of verse, some of them redacted: literal bars. */
    fun bars(): List<Mark> {
        val path = Path()
        // Sized against the canvas too, so a small preview tile gets a page of bars, not none.
        val lineHeight = min(30 * dp, h / 6)
        val barHeight = lineHeight * 0.24f
        val margin = min(16 * dp, w * 0.08f)
        val span = min(w, 360 * dp)
        var y = h * 0.06f
        while (y < h) {
            if (random.nextFloat() > 0.4f) {
                var x = margin + rnd(0f, 0.15f) * span
                repeat(random.nextInt(1, 4)) {
                    val end = min(x + rnd(0.08f, 0.42f) * span, w - margin)
                    if (end - x > barHeight * 2) path.addRect(Rect(x, y, end, y + barHeight))
                    x = end + rnd(0.02f, 0.045f) * span
                }
            }
            y += lineHeight
        }
        return listOf(Mark(path, alpha = 0.7f))
    }

    /** Speed lines, the blur of a run through heaven. */
    fun speed(): List<Mark> {
        val angle = -0.32
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        return listOf(0.75f, 1.5f, 3f).map { width ->
            val path = Path()
            repeat(
                if (width < 1f) {
                    26
                } else if (width < 2f) {
                    14
                } else {
                    6
                },
            ) {
                val start = Offset(rnd(-0.4f, 1f) * w, rnd(0f, 1.3f) * h)
                val len = rnd(80f, 420f) * dp
                path.moveTo(start.x, start.y)
                path.lineTo(start.x + dx * len, start.y + dy * len)
            }
            Mark(path, width * dp, if (width > 2f) 0.6f else 1f)
        }
    }

    /** Blade strokes: long tapered slashes across the screen, each with a thin echo. */
    fun slash(): List<Mark> {
        val blades = Path()
        val echoes = Path()
        val diagonal = hypot(w, h)
        repeat(5) {
            val center = Offset(rnd(0f, w), rnd(0f, h))
            val angle = rnd(0.9f, 1.25f) * if (random.nextBoolean()) 1 else -1
            val half = rnd(0.3f, 0.6f) * diagonal
            val thick = rnd(3f, 9f) * dp
            val ux = cos(angle)
            val uy = sin(angle)
            // Perpendicular, for the blade's width at its middle.
            val nx = -uy * thick
            val ny = ux * thick
            blades.moveTo(center.x - ux * half, center.y - uy * half)
            blades.lineTo(center.x + nx, center.y + ny)
            blades.lineTo(center.x + ux * half, center.y + uy * half)
            blades.lineTo(center.x - nx, center.y - ny)
            blades.close()
            val offset = thick * 3
            echoes.moveTo(
                center.x - ux * half * 0.7f + nx / thick * offset,
                center.y - uy * half * 0.7f + ny / thick * offset,
            )
            echoes.lineTo(
                center.x + ux * half * 0.7f + nx / thick * offset,
                center.y + uy * half * 0.7f + ny / thick * offset,
            )
        }
        return listOf(Mark(blades, alpha = 0.9f), Mark(echoes, 1f * dp, 0.7f))
    }

    /** Embers rising from below, thicker near the bottom of the screen. */
    fun embers(): List<Mark> {
        val small = Path()
        val large = Path()
        repeat(110) {
            val y = h * (1f - random.nextFloat().pow(1.8f))
            val x = rnd(0f, w)
            val r = rnd(1.2f, 5f) * dp
            val target = if (r > 3 * dp) large else small
            target.moveTo(x, y - r * 1.7f)
            target.lineTo(x + r, y)
            target.lineTo(x, y + r * 1.7f)
            target.lineTo(x - r, y)
            target.close()
        }
        return listOf(Mark(small, alpha = 1.3f), Mark(large, alpha = 0.8f))
    }

    /** Slanted rain on a jittered grid, with a few splashes at the bottom. */
    fun rain(): List<Mark> {
        val streaks = Path()
        val cellW = 34 * dp
        val cellH = 64 * dp
        var y = -cellH
        while (y < h) {
            var x = -cellW
            while (x < w + cellW) {
                val sx = x + rnd(0f, cellW)
                val sy = y + rnd(0f, cellH)
                val len = rnd(12f, 30f) * dp
                streaks.moveTo(sx, sy)
                streaks.lineTo(sx - len * 0.22f, sy + len)
                x += cellW
            }
            y += cellH
        }
        val splashes = Path()
        repeat(7) {
            val c = Offset(rnd(0f, w), h * rnd(0.86f, 0.98f))
            repeat(2) { ring ->
                val rx = (10 + ring * 9) * dp
                splashes.addOval(Rect(c.x - rx, c.y - rx * 0.28f, c.x + rx, c.y + rx * 0.28f))
            }
        }
        return listOf(Mark(streaks, 1f * dp, 0.9f), Mark(splashes, 0.8f * dp, 0.8f))
    }

    /** Falling tears and the ripples where they land. */
    fun tears(): List<Mark> {
        val drops = Path()
        repeat(24) {
            val x = rnd(0f, w)
            val y = rnd(0f, h * 0.85f)
            val r = rnd(4f, 11f) * dp
            drops.moveTo(x, y - r * 2.4f)
            drops.quadraticTo(x + r * 0.9f, y - r * 0.9f, x + r, y)
            drops.arcTo(Rect(Offset(x, y), r), 0f, 180f, false)
            drops.quadraticTo(x - r * 0.9f, y - r * 0.9f, x, y - r * 2.4f)
            drops.close()
        }
        val ripples = Path()
        repeat(6) {
            val c = Offset(rnd(0f, w), h * rnd(0.82f, 0.97f))
            repeat(3) { ring ->
                val rx = (12 + ring * 12) * dp
                ripples.addOval(Rect(c.x - rx, c.y - rx * 0.3f, c.x + rx, c.y + rx * 0.3f))
            }
        }
        return listOf(Mark(drops, alpha = 0.8f), Mark(ripples, 1f * dp, 0.7f))
    }

    /** A barrier closing in: nested diamonds around one point, every third one heavier. */
    fun domain(): List<Mark> {
        val c = Offset(w * 0.5f, h * 0.42f)
        val light = Path()
        val heavy = Path()
        var r = 28 * dp
        var i = 0
        val limit = hypot(w, h)
        while (r < limit) {
            val target = if (i % 3 == 0) heavy else light
            target.moveTo(c.x, c.y - r)
            target.lineTo(c.x + r, c.y)
            target.lineTo(c.x, c.y + r)
            target.lineTo(c.x - r, c.y)
            target.close()
            r *= 1.32f
            i++
        }
        val axes = Path()
        axes.moveTo(c.x, 0f)
        axes.lineTo(c.x, h)
        axes.moveTo(0f, c.y)
        axes.lineTo(w, c.y)
        return listOf(Mark(light, 1f * dp, 0.8f), Mark(heavy, 2.5f * dp), Mark(axes, 0.75f * dp, 0.5f))
    }

    /** A four-pointed star (sparkle). */
    private fun star(
        path: Path,
        c: Offset,
        r: Float,
    ) {
        val waist = r * 0.28f
        path.moveTo(c.x, c.y - r)
        path.lineTo(c.x + waist, c.y - waist)
        path.lineTo(c.x + r, c.y)
        path.lineTo(c.x + waist, c.y + waist)
        path.lineTo(c.x, c.y + r)
        path.lineTo(c.x - waist, c.y + waist)
        path.lineTo(c.x - r, c.y)
        path.lineTo(c.x - waist, c.y - waist)
        path.close()
    }
}
