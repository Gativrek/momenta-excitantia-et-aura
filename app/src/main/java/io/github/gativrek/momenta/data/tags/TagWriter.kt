package io.github.gativrek.momenta.data.tags

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.kyant.taglib.TagLib
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.library.contentUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.resume

/** What happened to a batch of edits. */
data class TagWriteResult(
    val written: Int,
    val failed: List<Track>,
)

/**
 * Writes tags into audio files, safely.
 *
 * For each file: copy it into private storage (kept as a backup), edit a second copy with
 * TagLib, read that copy back to check the edit landed, then overwrite the original with it
 * and read the original back too. If anything fails after the original was touched, the
 * backup is written back. A journal entry marks the moment the original is being
 * overwritten, so a crash or power cut mid-write is repaired by [recover] on the next start.
 * Finally the files are rescanned so MediaStore (and so the library, playlists and the
 * queue) sees the new tags.
 *
 * Write access must already be granted (see WriteAccess); this class only does the I/O.
 */
class TagWriter(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    /** Not cache: the system may clear cache at any time, and these backups must survive until written back. */
    private val workDir = File(appContext.noBackupFilesDir, "tag-edits")
    private val lock = Mutex()

    suspend fun write(
        edits: List<Pair<Track, TagEdit>>,
        onProgress: (done: Int) -> Unit = {},
    ): TagWriteResult =
        withContext(Dispatchers.IO) {
            lock.withLock {
                recoverLocked()
                val failed = mutableListOf<Track>()
                val paths = mutableListOf<String>()
                edits.forEachIndexed { index, (track, edit) ->
                    val ok =
                        try {
                            writeOne(track.contentUri(), track.id, edit)
                            true
                        } catch (e: Exception) {
                            // SecurityException, IOException, or a failed verification: all mean "not written".
                            Log.w(TAG, "Couldn't write tags for ${track.displayName}", e)
                            false
                        }
                    if (ok) dataPath(track.contentUri())?.let(paths::add) else failed += track
                    onProgress(index + 1)
                }
                rescan(paths)
                TagWriteResult(written = edits.size - failed.size, failed = failed)
            }
        }

    /** Repairs any file left half-written by a crash during [write]. Cheap when there's nothing to do. */
    suspend fun recover() = withContext(Dispatchers.IO) { lock.withLock { recoverLocked() } }

    private fun writeOne(
        uri: Uri,
        id: Long,
        edit: TagEdit,
    ) {
        workDir.mkdirs()
        val backup = File(workDir, "$id$BACKUP")
        val work = File(workDir, "$id$WORK")
        val journal = File(workDir, "$id$JOURNAL")
        try {
            // 1. Backup, and a working copy to edit.
            val input = resolver.openInputStream(uri) ?: throw IOException("Can't open $uri")
            input.use { inp -> backup.outputStream().use { inp.copyTo(it) } }
            backup.copyTo(work, overwrite = true)

            // 2. Edit the copy and check it.
            val properties = readProperties(work) ?: throw IOException("TagLib can't read this file's tags")
            if (!withFd(work, write = true) { TagLib.savePropertyMap(it, edit.applyTo(properties)) }) {
                throw IOException("TagLib couldn't save the tags")
            }
            keepId3Version(backup, work)
            val copyProperties = readProperties(work)
            if (copyProperties == null ||
                !edit.isAppliedIn(copyProperties)
            ) {
                throw IOException("Edited copy didn't verify")
            }

            // 3. Replace the original. From here until the journal is gone, a crash is repaired by recover().
            journal.writeText(uri.toString())
            try {
                overwrite(uri, work)
                val originalProperties = readProperties(uri)
                if (originalProperties == null || !edit.isAppliedIn(originalProperties)) {
                    throw IOException("Original didn't verify after writing")
                }
            } catch (e: Exception) {
                restore(uri, backup)
                throw e
            }
            journal.delete()
        } finally {
            // The backup is only kept while a journal entry still needs it.
            if (!journal.exists()) backup.delete()
            work.delete()
        }
    }

    /**
     * TagLib saves MP3 tags as ID3v2.4, which Android's scanner reads without the year. If the
     * file was v2.3, put the edited tag back to v2.3 (see Id3v23). Same size, rewritten in place.
     */
    private fun keepId3Version(
        original: File,
        edited: File,
    ) {
        if (Id3v23.version(readHead(original)) != 3) return
        RandomAccessFile(edited, "rw").use { file ->
            val head = ByteArray(Id3v23.HEADER_SIZE).also { file.readFully(it) }
            if (Id3v23.version(head) != 4) return
            val length = Id3v23.tagLength(head)
            if (length <= 0 || length > file.length()) return
            val tag =
                ByteArray(length).also {
                    file.seek(0)
                    file.readFully(it)
                }
            val downgraded = Id3v23.downgrade(tag)
            if (downgraded == null) {
                Log.i(TAG, "Kept ID3v2.4 for ${edited.name}: tag can't be expressed as v2.3")
                return
            }
            file.seek(0)
            file.write(downgraded)
            file.fd.sync()
        }
    }

    private fun readHead(file: File): ByteArray =
        FileInputStream(file).use { inp -> ByteArray(Id3v23.HEADER_SIZE).also { inp.read(it) } }

    private fun recoverLocked() {
        val files = workDir.listFiles() ?: return
        files.filter { it.name.endsWith(JOURNAL) }.forEach { journal ->
            val id = journal.name.removeSuffix(JOURNAL)
            val backup = File(workDir, "$id$BACKUP")
            val uri = journal.readText().toUri()
            try {
                if (backup.exists()) restore(uri, backup)
                journal.delete()
                backup.delete()
                Log.i(TAG, "Restored $uri after an interrupted tag write")
            } catch (e: Exception) {
                // Leave the journal and backup in place: try again on the next start.
                Log.w(TAG, "Couldn't restore $uri yet", e)
            }
        }
        // Copies with no journal entry never touched an original: just clean them up.
        workDir
            .listFiles()
            ?.filter { f ->
                !f.name.endsWith(JOURNAL) && !File(workDir, f.name.substringBefore('.') + JOURNAL).exists()
            }?.forEach { it.delete() }
    }

    private fun restore(
        uri: Uri,
        backup: File,
    ) {
        overwrite(uri, backup)
        val restored = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        if (restored != backup.length()) throw IOException("Restore of $uri didn't verify")
    }

    /** Replaces the whole content of [uri] with [source], truncating any tail, and syncs it to disk. */
    private fun overwrite(
        uri: Uri,
        source: File,
    ) {
        val pfd = resolver.openFileDescriptor(uri, "rwt") ?: throw IOException("Can't open $uri for writing")
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
            val channel = out.channel
            FileInputStream(source).use { inp ->
                val size = source.length()
                var position = 0L
                while (position < size) position += inp.channel.transferTo(position, size - position, channel)
                // "t" truncates on open on most versions, but not all: make sure no old tail remains.
                channel.truncate(size)
            }
            channel.force(true)
        }
    }

    private fun readProperties(file: File): Map<String, Array<String>>? =
        withFd(file, write = false) { readProperties(it) }

    private fun readProperties(uri: Uri): Map<String, Array<String>>? =
        resolver.openFileDescriptor(uri, "r")?.use { readProperties(it.dup().detachFd()) }

    /** [fd] is handed over to TagLib, which closes it. */
    private fun readProperties(fd: Int): Map<String, Array<String>>? = TagLib.getMetadata(fd, false)?.propertyMap

    private fun <T> withFd(
        file: File,
        write: Boolean,
        block: (fd: Int) -> T,
    ): T {
        val mode = if (write) ParcelFileDescriptor.MODE_READ_WRITE else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(file, mode).use { block(it.dup().detachFd()) }
    }

    /** The file's path, which the media scanner needs. Deprecated column, but still filled in for audio. */
    @Suppress("DEPRECATION")
    private fun dataPath(uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** Asks MediaStore to re-read the files, and waits for it (bounded, so a stuck scanner can't hang the UI). */
    private suspend fun rescan(paths: List<String>) {
        if (paths.isEmpty()) return
        val finished =
            withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    var remaining = paths.size
                    MediaScannerConnection.scanFile(appContext, paths.toTypedArray(), null) { _, _ ->
                        synchronized(this@TagWriter) {
                            remaining--
                            if (remaining == 0 && cont.isActive) cont.resume(Unit)
                        }
                    }
                }
            }
        if (finished == null) Log.w(TAG, "Media scan didn't finish in time; MediaStore will catch up on its own")
    }

    private companion object {
        const val TAG = "TagWriter"
        const val BACKUP = ".orig"
        const val WORK = ".new"
        const val JOURNAL = ".journal"
        const val SCAN_TIMEOUT_MS = 30_000L
    }
}
