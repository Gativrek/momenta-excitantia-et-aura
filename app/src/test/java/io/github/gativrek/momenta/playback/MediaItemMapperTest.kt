package io.github.gativrek.momenta.playback

import androidx.media3.common.MediaItem
import io.github.gativrek.momenta.data.library.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Runs on the JVM against android.jar stubs (returnDefaultValues), so it covers the pure
 * mapping only. The URI side is exercised by the instrumented smoke test and on-device runs.
 */
class MediaItemMapperTest {
    private val track =
        Track(
            id = 42,
            title = "Hangar 18",
            artist = "Megadeth",
            album = "Rust In Peace",
            albumId = 7,
            artistId = 3,
            durationMs = 314_000,
            trackNumber = 2,
            discNumber = 1,
            year = 1990,
            folderPath = "Music/",
            displayName = "02 Hangar 18.mp3",
            sizeBytes = 1,
            mimeType = "audio/mpeg",
            dateAddedSeconds = 0,
        )

    @Test
    fun `metadata carries what car and headset displays show`() {
        val metadata = MediaItemMapper.toMetadata(track)
        assertEquals("Hangar 18", metadata.title)
        assertEquals("Megadeth", metadata.artist)
        assertEquals("Rust In Peace", metadata.albumTitle)
        assertEquals(2, metadata.trackNumber)
        assertEquals(1, metadata.discNumber)
        assertEquals(1990, metadata.releaseYear)
        assertEquals(314_000L, metadata.durationMs)
    }

    @Test
    fun `untagged fields stay null rather than placeholder text`() {
        val metadata = MediaItemMapper.toMetadata(track.copy(artist = null, album = null, year = null))
        assertNull(metadata.artist)
        assertNull(metadata.albumTitle)
        assertNull(metadata.releaseYear)
    }

    @Test
    fun `media id round-trips to the MediaStore id`() {
        val item = MediaItem.Builder().setMediaId("42").build()
        assertEquals(42L, MediaItemMapper.trackId(item))
        assertNull(MediaItemMapper.trackId(MediaItem.Builder().setMediaId("not-ours").build()))
    }
}
