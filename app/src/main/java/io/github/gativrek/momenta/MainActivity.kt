package io.github.gativrek.momenta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.gativrek.momenta.ui.MomentaApp
import io.github.gativrek.momenta.ui.screens.permission.PermissionGate
import io.github.gativrek.momenta.ui.theme.MomentaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars; Scaffold hands us the insets as padding.
        // (Target SDK 35+ enforces edge-to-edge anyway, this makes older versions match.)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MomentaTheme {
                // Every tab reads the music library, so the whole app sits behind the permission.
                PermissionGate {
                    MomentaApp()
                }
            }
        }
    }
}
