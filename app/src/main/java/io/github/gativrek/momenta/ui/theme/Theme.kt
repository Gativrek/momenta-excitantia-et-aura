package io.github.gativrek.momenta.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
