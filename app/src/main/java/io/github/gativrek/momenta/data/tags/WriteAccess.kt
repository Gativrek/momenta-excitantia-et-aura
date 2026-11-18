package io.github.gativrek.momenta.data.tags

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.library.contentUri

/**
 * How the app gets permission to write into music files it didn't create. The one place
 * the API split lives:
 *
 * - Android 11+ (API 30): `MediaStore.createWriteRequest`. One system prompt per batch,
 *   so editing a whole album asks once.
 * - Android 8–10 (API 26–29): the storage write permission, asked once. On Android 10 it
 *   works together with `requestLegacyExternalStorage` in the manifest; that's simpler than
 *   catching a `RecoverableSecurityException` per file and asks once instead of per song.
 */
object WriteAccess {
    /** Android 11+: the system dialog asking to modify [tracks]. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun writeRequest(
        resolver: ContentResolver,
        tracks: List<Track>,
    ): IntentSender = MediaStore.createWriteRequest(resolver, tracks.map { it.contentUri() }).intentSender

    /** Whether to use [writeRequest] (true) or the runtime [LEGACY_PERMISSION] (false). */
    val usesWriteRequest: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** The runtime permission used below Android 11. */
    const val LEGACY_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE

    fun hasLegacyPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, LEGACY_PERMISSION) == PackageManager.PERMISSION_GRANTED
}
