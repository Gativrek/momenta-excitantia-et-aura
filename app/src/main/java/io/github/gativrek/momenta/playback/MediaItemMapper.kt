package io.github.gativrek.momenta.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.gativrek.momenta.data.library.Artwork
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.library.contentUri

/**
 * Track <-> MediaItem. The MediaItem is what travels to the service and on to every
 * controller (notification, lock screen, Bluetooth, car), so its metadata is what they show.
 */
object MediaItemMapper {
    private const val EXTRA_ALBUM_ID = "io.github.gativrek.momenta.ALBUM_ID"

    /** mediaId = MediaStore `_ID` (KICKOFF.md). The URI is set too, for in-process use. */
    fun toMediaItem(track: Track): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(track.id.toString())
            .setUri(track.contentUri())
            .setMediaMetadata(toMetadata(track))
            .build()

    /**
     * Title, artist and album are what AVRCP and car displays read (success criterion 7).
     * No artwork URI: ExoPlayer extracts the cover embedded in the file while playing and
     * hands it to the notification, which is exactly the per-track art we want.
     */
    fun toMetadata(track: Track): MediaMetadata =
        MediaMetadata
            .Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .setReleaseYear(track.year)
            .setDurationMs(track.durationMs)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(Bundle().apply { putLong(EXTRA_ALBUM_ID, track.albumId) })
            .build()

    /** The track ID behind a MediaItem, or null for an item we didn't create. */
    fun trackId(item: MediaItem): Long? = item.mediaId.toLongOrNull()

    /** Cover-art model for the in-app UI, from what the session reports. */
    fun artwork(item: MediaItem): Artwork? {
        val trackId = trackId(item) ?: return null
        val albumId = item.mediaMetadata.extras?.getLong(EXTRA_ALBUM_ID) ?: 0L
        return Artwork(trackId = trackId, albumId = albumId)
    }
}
