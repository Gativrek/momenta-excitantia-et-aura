package io.github.gativrek.momenta

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gativrek.momenta.ui.MomentaApp
import io.github.gativrek.momenta.ui.components.StartupIntro
import io.github.gativrek.momenta.ui.screens.moments.LocalMomentGlow
import io.github.gativrek.momenta.ui.screens.moments.MomentsHost
import io.github.gativrek.momenta.ui.screens.permission.PermissionGate
import io.github.gativrek.momenta.ui.screens.tags.TagEditorHost
import io.github.gativrek.momenta.ui.theme.MomentaTheme
import io.github.gativrek.momenta.ui.theme.ThemeBackdrop
import io.github.gativrek.momenta.ui.theme.animatedTheme
import io.github.gativrek.momenta.ui.theme.isDarkBackground

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as App).container }
    private val playerConnection by lazy { container.playerConnection }

    /** The launch screen's logo, in window pixels, once it hands over (Android 12+). */
    private var launchLogo by mutableStateOf<Rect?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars; Scaffold hands us the insets as padding.
        // (Target SDK 35+ enforces edge-to-edge anyway, this makes older versions match.)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Android 12+: take the launch screen's logo over as it goes, so the intro can glide it
        // into the title bar from exactly where it was.
        val hasLaunchScreen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && savedInstanceState == null
        if (hasLaunchScreen) {
            splashScreen.setOnExitAnimationListener { view ->
                view.iconView?.let { icon ->
                    val at = IntArray(2).also(icon::getLocationInWindow)
                    launchLogo =
                        Rect(
                            at[0].toFloat(),
                            at[1].toFloat(),
                            (at[0] + icon.width).toFloat(),
                            (at[1] + icon.height).toFloat(),
                        )
                }
                view.remove()
            }
        }
        setContent {
            // Null for the few milliseconds before the saved theme is read: the window
            // background (the default theme's color) shows until then, never a wrong theme.
            val theme by container.appearanceStore.theme.collectAsStateWithLifecycle()
            // A theme pinned to the playing song's album wins while that song is on.
            val pinned by container.pinnedTheme.collectAsStateWithLifecycle()
            val spec = animatedTheme(pinned ?: theme ?: return@setContent, pinned)
            val dark = isDarkBackground(spec.background)
            LaunchedEffect(dark) {
                // Status and navigation bar icons follow the theme, not the system dark mode.
                val style =
                    if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MomentaTheme(spec) {
                MomentsHost {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // The pattern sits behind every screen; screens are transparent over it.
                        // It lights up during a Moment, whichever screen is open.
                        val glow = LocalMomentGlow.current
                        ThemeBackdrop(spec, Modifier.fillMaxSize(), glow = { glow.value })
                        // On launch the app sweeps in from the bottom up, over the backdrop.
                        StartupIntro(launchLogo = launchLogo, hasLaunchScreen = hasLaunchScreen) {
                            // Every tab reads the music library, so the whole app sits behind the permission.
                            PermissionGate {
                                TagEditorHost {
                                    MomentaApp()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bind to the playback service only while visible (see PlayerConnection). Playback itself
    // keeps going after onStop: the service, not the Activity, owns the player.
    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }

    override fun onStop() {
        playerConnection.disconnect()
        super.onStop()
    }
}
