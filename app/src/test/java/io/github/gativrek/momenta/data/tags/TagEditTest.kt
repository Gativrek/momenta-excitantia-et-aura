package io.github.gativrek.momenta.data.tags

import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagEditTest {
    private fun track(
        id: Long,
        title: String = "Song $id",
        artist: String? = "Megadeth",
        album: String? = "Rust in Peace",
        albumArtist: String? = null,
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = 1,
        artistId = 1,
        durationMs = 1000,
        trackNumber = id.toInt(),
        discNumber = null,
        year = null,
        folderPath = "Music/",
        displayName = "$id.mp3",
        sizeBytes = 1,
        mimeType = "audio/mpeg",
        dateAddedSeconds = 0,
        albumArtist = albumArtist,
    )

    private fun album(vararg tracks: Track) =
        Album(
            id = 1,
            title = "Rust in Peace",
            artist = "Megadeth",
            isVarious = false,
            year = null,
            tracks = tracks.toList(),
        )

    @Test
    fun `untouched form changes nothing`() {
        val t = track(1, albumArtist = null)
        assertTrue(TagEdit.forTrack(t, "Song 1", "Megadeth", "Rust in Peace", "").isEmpty)
    }

    @Test
    fun `only changed fields are written, trimmed`() {
        val edit = TagEdit.forTrack(track(1), "  Hangar 18 ", "Megadeth", "Rust in Peace", "")
        assertEquals(TagEdit(title = "Hangar 18"), edit)
    }

    @Test
    fun `clearing a field removes the tag`() {
        val edit = TagEdit.forTrack(track(1, albumArtist = "Megadeth"), "Song 1", "Megadeth", "Rust in Peace", " ")
        assertEquals("", edit.albumArtist)
        val applied = edit.applyTo(mapOf("ALBUMARTIST" to arrayOf("Megadeth"), "TITLE" to arrayOf("Song 1")))
        assertFalse(applied.containsKey("ALBUMARTIST"))
        assertTrue(edit.isAppliedIn(applied))
    }

    @Test
    fun `applying keeps every other tag`() {
        val original = mapOf("TITLE" to arrayOf("Old"), "GENRE" to arrayOf("Thrash"), "DATE" to arrayOf("1990"))
        val applied = TagEdit(title = "New").applyTo(original)
        assertEquals(listOf("New"), applied["TITLE"]?.toList())
        assertEquals(listOf("Thrash"), applied["GENRE"]?.toList())
        assertEquals(listOf("1990"), applied["DATE"]?.toList())
    }

    @Test
    fun `verification catches a write that did not land`() {
        val edit = TagEdit(title = "New", artist = "")
        assertFalse(edit.isAppliedIn(mapOf("TITLE" to arrayOf("Old"))))
        assertFalse(edit.isAppliedIn(mapOf("TITLE" to arrayOf("New"), "ARTIST" to arrayOf("Still here"))))
        assertTrue(edit.isAppliedIn(mapOf("TITLE" to arrayOf("New"))))
    }

    @Test
    fun `album edit renames every track and skips unchanged ones`() {
        val a = album(track(1), track(2, album = "Rust In Peace"))
        val edits = TagEdit.forAlbum(a, title = "Rust In Peace", artist = null, albumArtist = null)
        assertEquals(listOf(1L), edits.map { it.first.id })
        assertEquals(TagEdit(album = "Rust In Peace"), edits.single().second)
    }

    @Test
    fun `album artist left on keep is not touched, typed one is set everywhere`() {
        val a = album(track(1, artist = "A"), track(2, artist = "B"))
        assertTrue(TagEdit.forAlbum(a, "Rust in Peace", artist = null, albumArtist = null).isEmpty())
        val edits = TagEdit.forAlbum(a, "Rust in Peace", artist = null, albumArtist = "Various")
        assertEquals(2, edits.size)
        assertTrue(edits.all { it.second == TagEdit(albumArtist = "Various") })
    }

    @Test
    fun `shared value only when all tracks agree`() {
        assertEquals("A", TagEdit.shared(listOf("A", "A")))
        assertNull(TagEdit.shared(listOf("A", "B")))
        assertNull(TagEdit.shared(listOf(null, null)))
    }
}
