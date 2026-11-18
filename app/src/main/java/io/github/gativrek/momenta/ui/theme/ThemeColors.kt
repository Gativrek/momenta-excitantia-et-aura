package io.github.gativrek.momenta.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** Text that sits directly on the background or on a surface needs at least this much contrast (WCAG AA). */
const val TEXT_CONTRAST = 4.5

/** Accents (icons, sliders, the playing track) need at least this much contrast against the background. */
const val ACCENT_CONTRAST = 3.0

/**
 * Whether a theme built on this background is a dark theme (light text, light status-bar
 * icons): true when white reads better on it than black does.
 */
fun isDarkBackground(background: Color): Boolean =
    contrast(Color.White, background) >= contrast(Color.Black, background)

/** WCAG contrast ratio between two opaque colors, 1..21. */
fun contrast(
    a: Color,
    b: Color,
): Double {
    val la = a.luminance() + 0.05
    val lb = b.luminance() + 0.05
    return max(la, lb) / min(la, lb)
}

/**
 * [color], moved toward [toward] just far enough to reach [minimum] contrast against
 * [background]. Colors that already pass come back unchanged, so a chosen accent is only
 * touched when it would otherwise be unreadable.
 */
fun ensureContrast(
    color: Color,
    background: Color,
    minimum: Double,
    toward: Color = if (isDarkBackground(background)) Color.White else Color.Black,
): Color {
    if (contrast(color, background) >= minimum) return color
    var step = 0.05f
    while (step < 1f) {
        val candidate = lerp(color, toward, step)
        if (contrast(candidate, background) >= minimum) return candidate
        step += 0.05f
    }
    return toward
}

/**
 * Near-black or near-white, whichever reads better on [color]. On mid-tones (a saturated
 * red, say) the softened tones can fall short of [TEXT_CONTRAST]; then pure black or white,
 * one of which always passes.
 */
fun readableOn(color: Color): Color {
    val darkBetter = contrast(Color.Black, color) >= contrast(Color.White, color)
    val soft = if (darkBetter) Color(0xFF111114) else Color(0xFFF7F5F2)
    return when {
        contrast(soft, color) >= TEXT_CONTRAST -> soft
        darkBetter -> Color.Black
        else -> Color.White
    }
}

/**
 * The full Material 3 color scheme for a theme, derived from its four chosen colors.
 *
 * Surfaces are the background stepped toward the text color (tinted slightly by the accent),
 * containers are the accent sunk into the background, and every "on" color is checked
 * against what it sits on. Whatever four colors the user picks, text stays readable.
 */
fun colorSchemeFor(spec: ThemeSpec): ColorScheme {
    val bg = spec.background
    val dark = isDarkBackground(bg)
    val pure = if (dark) Color.White else Color.Black
    val ink = if (dark) Color(0xFFF2F0EC) else Color(0xFF15141A)
    // Cards and sheets normally step from the background toward the text color. On a
    // mid-tone background that would leave text too little contrast, so they step away instead.
    val surfaceToward =
        if (contrast(pure, lerp(bg, pure, 0.14f)) >= TEXT_CONTRAST) {
            pure
        } else if (dark) {
            Color.Black
        } else {
            Color.White
        }
    val tintedBg = lerp(bg, spec.accent, 0.04f)

    fun surface(level: Float) = lerp(tintedBg, surfaceToward, level)

    // Text must read on the background and on the strongest card: moving it toward pure
    // white/black raises its contrast with both.
    fun readable(color: Color) =
        ensureContrast(ensureContrast(color, surface(0.14f), TEXT_CONTRAST, pure), bg, TEXT_CONTRAST, pure)

    // Text carries a whisper of the accent so it belongs to the theme, but stays readable.
    val onBg = readable(lerp(ink, spec.accent, 0.06f))
    val onBgVariant = readable(lerp(onBg, bg, 0.3f))

    val primary = ensureContrast(spec.accent, bg, ACCENT_CONTRAST)
    val primaryContainer = lerp(bg, spec.accent, if (dark) 0.32f else 0.22f)
    val secondary = ensureContrast(lerp(spec.accent, onBgVariant, 0.45f), bg, ACCENT_CONTRAST)
    val secondaryContainer = lerp(bg, spec.accent, if (dark) 0.18f else 0.12f)
    val tertiary = ensureContrast(spec.spark, bg, ACCENT_CONTRAST)
    val tertiaryContainer = lerp(bg, spec.spark, if (dark) 0.3f else 0.2f)

    fun onContainer(container: Color) = ensureContrast(onBg, container, TEXT_CONTRAST, readableOn(container))

    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = readableOn(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onContainer(primaryContainer),
        inversePrimary = lerp(spec.accent, if (dark) Color.Black else Color.White, 0.35f),
        secondary = secondary,
        onSecondary = readableOn(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onContainer(secondaryContainer),
        tertiary = tertiary,
        onTertiary = readableOn(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onContainer(tertiaryContainer),
        background = bg,
        onBackground = onBg,
        surface = bg,
        onSurface = onBg,
        surfaceVariant = surface(0.12f),
        onSurfaceVariant = onBgVariant,
        surfaceTint = primary,
        inverseSurface = onBg,
        inverseOnSurface = bg,
        outline = lerp(bg, onBg, 0.45f),
        outlineVariant = lerp(bg, onBg, 0.2f),
        surfaceBright = surface(0.16f),
        surfaceDim = lerp(bg, Color.Black, 0.2f),
        surfaceContainerLowest = lerp(bg, if (dark) Color.Black else Color.White, 0.3f),
        surfaceContainerLow = surface(0.04f),
        surfaceContainer = surface(0.07f),
        surfaceContainerHigh = surface(0.10f),
        surfaceContainerHighest = surface(0.14f),
    )
}
