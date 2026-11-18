package io.github.gativrek.momenta.ui.screens.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.ui.theme.MomentaTheme

/**
 * Shows [content] only once the audio permission is granted; otherwise an explanation
 * screen with a way to grant it, never a crash.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var status by rememberSaveable {
        mutableStateOf(if (context.hasAudioPermission()) PermissionStatus.Granted else PermissionStatus.Requestable)
    }

    // The user may grant (or revoke) access in system settings and come back, so re-check
    // every time the screen resumes. A denied-forever state stays put until settings grant it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (context.hasAudioPermission()) {
            status = PermissionStatus.Granted
        } else if (status == PermissionStatus.Granted) {
            status = PermissionStatus.Requestable
        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val rationale =
                activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, audioPermission) } ?: true
            status = statusAfterRequest(granted, rationale)
        }

    when (status) {
        PermissionStatus.Granted -> content()
        else ->
            PermissionScreen(
                permanentlyDenied = status == PermissionStatus.PermanentlyDenied,
                onRequest = { launcher.launch(audioPermission) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )
    }
}

@Composable
fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier.systemBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_library_access),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (permanentlyDenied) {
                Text(
                    text = stringResource(R.string.permission_denied_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequest,
                modifier = Modifier.padding(top = 32.dp),
            ) {
                Text(
                    stringResource(
                        if (permanentlyDenied) R.string.permission_open_settings else R.string.permission_grant,
                    ),
                )
            }
        }
    }
}

@Preview
@Composable
private fun PermissionScreenPreview() {
    MomentaTheme { PermissionScreen(permanentlyDenied = false, onRequest = {}, onOpenSettings = {}) }
}

@Preview
@Composable
private fun PermissionScreenDeniedPreview() {
    MomentaTheme { PermissionScreen(permanentlyDenied = true, onRequest = {}, onOpenSettings = {}) }
}
