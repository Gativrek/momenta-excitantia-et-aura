package io.github.gativrek.momenta.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors =
    lightColorScheme(
        primary = VioletLight,
        onPrimary = OnVioletLight,
        primaryContainer = VioletContainerLight,
        onPrimaryContainer = OnVioletContainerLight,
        secondary = MauveLight,
        secondaryContainer = MauveContainerLight,
        onSecondaryContainer = OnMauveContainerLight,
        tertiary = AmberLight,
        tertiaryContainer = AmberContainerLight,
        onTertiaryContainer = OnAmberContainerLight,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = VioletDark,
        onPrimary = OnVioletDark,
        primaryContainer = VioletContainerDark,
        onPrimaryContainer = OnVioletContainerDark,
        secondary = MauveDark,
        secondaryContainer = MauveContainerDark,
        onSecondaryContainer = OnMauveContainerDark,
        tertiary = AmberDark,
        tertiaryContainer = AmberContainerDark,
        onTertiaryContainer = OnAmberContainerDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
    )

/**
 * App theme. Follows the system light/dark setting. On Android 12+ (API 31) colors come
 * from the wallpaper ("Material You" dynamic color); older versions use the fixed palette.
 */
@Composable
fun MomentaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
