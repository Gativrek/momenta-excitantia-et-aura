package io.github.gativrek.momenta.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    private fun track(
        id: Long,
        title: String,
        artist: String? = null,
        album: String? = null,
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = 1,
        artistId = 1,
        durationMs = 1_000,
        trackNumber = null,
        discNumber = null,
        year = null,
        folderPath = "Music/",
        displayName = "$title.mp3",
        sizeBytes = 1,
        mimeType = null,
        dateAddedSeconds = 0,
    )

    private val seasons = track(1, "72 Seasons", "Metallica", "72 Seasons")
    private val lux = track(2, "Lux Æterna", "Metallica", "72 Seasons")
    private val aizo = track(3, "AIZO", "King Gnu", "AIZO")
    private val halo = track(4, "Halo", "Beyoncé", "I Am... Sasha Fierce")
    private val tracks = listOf(seasons, lux, aizo, halo)

    private fun songs(query: String) = LibrarySearch.search(query, tracks, emptyList(), emptyList()).tracks

    @Test
    fun `blank query finds nothing`() {
        assertTrue(LibrarySearch.search("  ", tracks, emptyList(), emptyList()).isEmpty)
    }

    @Test
    fun `case and accents are ignored`() {
        assertEquals(listOf(halo), songs("beyonce"))
        assertEquals(listOf(lux), songs("lux aeterna"))
        assertEquals(listOf(aizo), songs("aizo"))
    }

    @Test
    fun `every word must match, in any field and order`() {
        assertEquals(listOf(seasons), songs("seasons 72 metallica").filter { it.title == "72 Seasons" })
        assertEquals(listOf(lux), songs("metallica lux"))
        assertTrue(songs("metallica gnu").isEmpty())
    }

    @Test
    fun `name prefix ranks above a match elsewhere`() {
        // "Lux Æterna" matches by title; "72 Seasons" only by album/artist text.
        val both = LibrarySearch.search("metallica", tracks, emptyList(), emptyList()).tracks
        assertEquals(listOf(seasons, lux), both) // equal score keeps library order
        assertEquals(listOf(seasons, lux), songs("seasons")) // word-start beats album-only match
    }

    @Test
    fun `punctuation is a word break`() {
        assertEquals(listOf(halo), songs("sasha fierce"))
        assertEquals("i am sasha fierce", LibrarySearch.fold("I Am... Sasha Fierce"))
    }
}
