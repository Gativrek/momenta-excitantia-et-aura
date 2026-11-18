package io.github.gativrek.momenta

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gativrek.momenta.ui.MomentaApp
import io.github.gativrek.momenta.ui.screens.permission.PermissionGate
import io.github.gativrek.momenta.ui.screens.tags.TagEditorHost
import io.github.gativrek.momenta.ui.theme.MomentaTheme
import io.github.gativrek.momenta.ui.theme.ThemeBackdrop
import io.github.gativrek.momenta.ui.theme.isDarkBackground

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as App).container }
    private val playerConnection by lazy { container.playerConnection }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars; Scaffold hands us the insets as padding.
        // (Target SDK 35+ enforces edge-to-edge anyway, this makes older versions match.)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Null for the few milliseconds before the saved theme is read: the window
            // background (the default theme's color) shows until then, never a wrong theme.
            val theme by container.appearanceStore.theme.collectAsStateWithLifecycle()
            val spec = theme ?: return@setContent
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
                Box(modifier = Modifier.fillMaxSize()) {
                    // The pattern sits behind every screen; screens are transparent over it.
                    ThemeBackdrop(spec, Modifier.fillMaxSize())
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
