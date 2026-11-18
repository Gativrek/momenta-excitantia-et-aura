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
    private const val EXTRA_SIZE = "io.github.gativrek.momenta.SIZE"

    /** mediaId = MediaStore `_ID`, the one ID every layer shares. The URI is set too, for in-process use. */
    fun toMediaItem(track: Track): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(track.id.toString())
            .setUri(track.contentUri())
            .setMediaMetadata(toMetadata(track))
            .build()

    /**
     * Title, artist and album are what AVRCP and car displays read.
     * No artwork URI: ExoPlayer extracts the cover embedded in the file while playing and
     * hands it to the notification, which is exactly the per-track art we want.
     */
    fun toMetadata(track: Track): MediaMetadata =
        MediaMetadata
            .Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setAlbumArtist(track.albumArtist)
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .setReleaseYear(track.year)
            .setDurationMs(track.durationMs)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(
                Bundle().apply {
                    putLong(EXTRA_ALBUM_ID, track.albumId)
                    putLong(EXTRA_SIZE, track.sizeBytes)
                },
            ).build()

    /**
     * Whether [fresh] (built from the library now) shows anything [old] doesn't: new tags,
     * or a new file size. MediaMetadata.equals ignores extras, hence the explicit checks.
     */
    fun isOutdated(
        old: MediaItem,
        fresh: MediaItem,
    ): Boolean =
        old.mediaMetadata != fresh.mediaMetadata ||
            extra(old, EXTRA_ALBUM_ID) != extra(fresh, EXTRA_ALBUM_ID) ||
            fileChanged(old, fresh)

    /**
     * The file itself was rewritten with a different size (e.g. its tags were edited and no
     * longer fit the old space), so the audio now starts at a different byte offset: a player
     * already reading it must reload it.
     */
    fun fileChanged(
        old: MediaItem,
        fresh: MediaItem,
    ): Boolean = extra(old, EXTRA_SIZE) != extra(fresh, EXTRA_SIZE)

    private fun extra(
        item: MediaItem,
        key: String,
    ): Long? =
        item.mediaMetadata.extras
            ?.takeIf { it.containsKey(key) }
            ?.getLong(key)

    /** The track ID behind a MediaItem, or null for an item we didn't create. */
    fun trackId(item: MediaItem): Long? = item.mediaId.toLongOrNull()

    /** Cover-art model for the in-app UI, from what the session reports. */
    fun artwork(item: MediaItem): Artwork? {
        val trackId = trackId(item) ?: return null
        val albumId = item.mediaMetadata.extras?.getLong(EXTRA_ALBUM_ID) ?: 0L
        return Artwork(trackId = trackId, albumId = albumId)
    }
}
