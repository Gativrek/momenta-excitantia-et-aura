package io.github.gativrek.momenta.data.library

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Content URI of a track's audio, the URI ExoPlayer will play. */
fun Track.contentUri(): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

/**
 * Reads the device's music from MediaStore. This is the only class that knows MediaStore's
 * column names; everything above it works with [Track].
 */
class MediaStoreSource(
    private val resolver: ContentResolver,
) {
    /** Unsorted list of every music file. Throws [SecurityException] without the audio permission. */
    suspend fun queryTracks(): List<Track> =
        withContext(Dispatchers.IO) {
            resolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    PROJECTION,
                    // IS_MUSIC excludes ringtones, alarms, notification sounds and voice notes.
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    null,
                )?.use { cursor -> cursor.readTracks() }
                .orEmpty()
        }

    private fun Cursor.readTracks(): List<Track> {
        // Resolve column indexes once, not per row: this loop runs thousands of times.
        val id = getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val artistId = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
        val duration = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val track = getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val year = getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val displayName = getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val size = getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val mime = getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val dateAdded = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val folder = getColumnIndexOrThrow(FOLDER_COLUMN)

        val tracks = ArrayList<Track>(count)
        while (moveToNext()) {
            val name = getString(displayName).orEmpty()
            val (disc, number) = TrackFields.discAndTrack(intOrNull(track))
            tracks +=
                Track(
                    id = getLong(id),
                    title = TrackFields.title(getString(title), name),
                    artist = TrackFields.tagOrNull(getString(artist)),
                    album = TrackFields.tagOrNull(getString(album)),
                    albumId = getLong(albumId),
                    artistId = getLong(artistId),
                    durationMs = getLong(duration),
                    trackNumber = number,
                    discNumber = disc,
                    year = TrackFields.year(intOrNull(year)),
                    folderPath = folderOf(getString(folder)),
                    displayName = name,
                    sizeBytes = getLong(size),
                    mimeType = getString(mime),
                    dateAddedSeconds = getLong(dateAdded),
                )
        }
        return tracks
    }

    private fun Cursor.intOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)

    private companion object {
        /**
         * The one place the folder-path API split lives. API 29+ exposes RELATIVE_PATH;
         * on 26–28 we read the absolute DATA path (deprecated, but the only option there).
         */
        val FOLDER_COLUMN: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.RELATIVE_PATH
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Audio.Media.DATA
            }

        fun folderOf(raw: String?): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                TrackFields.folderFromRelativePath(raw)
            } else {
                TrackFields.folderFromDataPath(raw)
            }

        val PROJECTION =
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_ADDED,
                FOLDER_COLUMN,
            )
    }
}
