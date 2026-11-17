package io.github.gativrek.momenta.ui.screens.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permission that unlocks the music library. Android 13 split storage access
 * by media type, so 13+ asks only for audio; older versions need the broad storage read.
 */
val audioPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun Context.hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

/** What the permission screen should offer. */
enum class PermissionStatus {
    Granted,

    /** Not granted, and the system dialog can still be shown. */
    Requestable,

    /**
     * Denied in a way the system dialog won't show again ("Don't allow" twice, or "don't ask
     * again"). Only the app's system settings page can grant it now.
     */
    PermanentlyDenied,
}

/**
 * Android has no direct "permanently denied" query. The standard inference: right after a
 * denial, if the system no longer wants us to show a rationale, it won't show the dialog either.
 */
fun statusAfterRequest(
    granted: Boolean,
    shouldShowRationale: Boolean,
): PermissionStatus =
    when {
        granted -> PermissionStatus.Granted
        shouldShowRationale -> PermissionStatus.Requestable
        else -> PermissionStatus.PermanentlyDenied
    }
