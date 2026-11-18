package io.github.gativrek.momenta.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The theme the user built, for the few places that need more than the color scheme (backdrop, previews). */
val LocalThemeSpec = staticCompositionLocalOf { ThemeSpec.Default }

/** Angular corners: cut, not rounded. Small cuts on small things, so text never gets clipped. */
private val SharpShapes =
    Shapes(
        extraSmall = CutCornerShape(2.dp),
        small = CutCornerShape(4.dp),
        medium = CutCornerShape(8.dp),
        large = CutCornerShape(12.dp),
        extraLarge = CutCornerShape(16.dp),
    )

/**
 * App theme, built entirely from the user's [ThemeSpec]: the color scheme is derived from its
 * four colors and the shapes follow its edge style. Light or dark follows the background
 * color, not the system setting: a theme is one look, picked on purpose.
 */
@Composable
fun MomentaTheme(
    spec: ThemeSpec = ThemeSpec.Default,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(spec) { colorSchemeFor(spec) }
    CompositionLocalProvider(LocalThemeSpec provides spec) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = if (spec.edge == Edge.Sharp) SharpShapes else Shapes(),
        ) {
            // Screens are transparent over the backdrop, and a transparent Scaffold or Surface
            // can't pick a content color for itself: text would fall back to black. Set it here.
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
        }
    }
}

/**
 * [target], with its colors easing over when a pinned theme comes or goes ([pinned] changed),
 * so the app changes clothes smoothly between songs. Edits on the Appearance screen still show
 * at once.
 */
@Composable
fun animatedTheme(
    target: ThemeSpec,
    pinned: ThemeSpec?,
): ThemeSpec {
    var lastPinned by remember { mutableStateOf(pinned) }
    val switching = pinned != lastPinned
    SideEffect { lastPinned = pinned }
    val colorSpec: AnimationSpec<Color> = if (switching) tween(THEME_SWITCH_MS) else snap()
    val floatSpec: AnimationSpec<Float> = if (switching) tween(THEME_SWITCH_MS) else snap()
    val background by animateColorAsState(target.background, colorSpec, label = "background")
    val accent by animateColorAsState(target.accent, colorSpec, label = "accent")
    val spark by animateColorAsState(target.spark, colorSpec, label = "spark")
    val patternColor by animateColorAsState(target.patternColor, colorSpec, label = "patternColor")
    val patternStrength by animateFloatAsState(target.patternStrength, floatSpec, label = "patternStrength")
    val aura by animateFloatAsState(target.aura, floatSpec, label = "aura")
    return target.copy(
        background = background,
        accent = accent,
        spark = spark,
        patternColor = patternColor,
        patternStrength = patternStrength,
        aura = aura,
    )
}

private const val THEME_SWITCH_MS = 900

/**
 * The colour of Moment controls (the bolt, marks on the seek bar, notes). The accent, like
 * every other control, so Moments sit in the theme instead of fighting it: the bolt itself
 * says "Moment". The theme's spark colour is kept for the reaction (light, fire, aura).
 */
val ColorScheme.moment: Color get() = primary

/** Moment marks on the seek bar: the accent, quieter than the played part of the track. */
const val MOMENT_MARK_ALPHA = 0.55f
