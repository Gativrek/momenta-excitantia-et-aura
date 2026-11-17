package io.github.gativrek.momenta.data.library

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.source
import java.io.FileNotFoundException

/**
 * Coil model for a track's cover art. Pass it straight to `AsyncImage(model = ...)`.
 * Build it with [artworkModel] rather than directly.
 */
data class Artwork(
    val trackId: Long,
    val albumId: Long,
)

fun artworkModel(track: Track): Artwork = Artwork(trackId = track.id, albumId = track.albumId)

/**
 * Loads [Artwork] the right way for each Android version (KICKOFF.md "Album art"):
 * - API 29+: `ContentResolver.loadThumbnail` on the track URI. MediaStore extracts the
 *   embedded cover and caches a scaled thumbnail for us.
 * - API 26–28: the legacy `content://media/external/audio/albumart/{albumId}` URI.
 *
 * Throws when a track has no art; Coil reports that as an error and the UI shows its fallback.
 */
class ArtworkFetcher(
    private val data: Artwork,
    private val options: Options,
    private val resolver: ContentResolver,
) : Fetcher {
    override suspend fun fetch(): FetchResult =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val width = options.size.width.pxOrElse { DEFAULT_SIZE_PX }
                val height = options.size.height.pxOrElse { DEFAULT_SIZE_PX }
                val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, data.trackId)
                val bitmap: Bitmap = resolver.loadThumbnail(trackUri, Size(width, height), null)
                ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
            } else {
                val uri = ContentUris.withAppendedId(LEGACY_ALBUM_ART_URI, data.albumId)
                val stream = resolver.openInputStream(uri) ?: throw FileNotFoundException("No album art for $uri")
                SourceFetchResult(
                    source = ImageSource(stream.source().buffer(), FileSystem.SYSTEM),
                    mimeType = null,
                    dataSource = DataSource.DISK,
                )
            }
        }

    class Factory(
        private val resolver: ContentResolver,
    ) : Fetcher.Factory<Artwork> {
        override fun create(
            data: Artwork,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ArtworkFetcher(data, options, resolver)
    }

    private companion object {
        const val DEFAULT_SIZE_PX = 512
        val LEGACY_ALBUM_ART_URI: Uri = "content://media/external/audio/albumart".toUri()
    }
}

/**
 * Memory-cache key, matching where the art actually comes from:
 * - API 29+ reads each file's own embedded cover, so the key is per track. Keying by album
 *   would be wrong: untagged files are grouped under their folder name as one "album", and
 *   each can carry a different cover (seen on real data: 12 tracks, one album ID, 12 covers).
 * - API 26–28 only has per-album art, so per-album keys let an album's tracks share one bitmap.
 * Coil adds the requested size to the key itself.
 */
class ArtworkKeyer : Keyer<Artwork> {
    override fun key(
        data: Artwork,
        options: Options,
    ): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "track-art:${data.trackId}"
        } else {
            "album-art:${data.albumId}"
        }
}
