package io.github.gativrek.momenta.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LibraryGroupingTest {
    private val en = Locale.ENGLISH

    @Test
    fun `album tracks play in disc then track order, untagged numbers last`() {
        val tracks =
            listOf(
                track(1, "B-side", albumId = 1, disc = 2, number = 1),
                track(2, "Opener", albumId = 1, disc = 1, number = 1),
                track(3, "Bonus", albumId = 1, disc = 1, number = null),
                track(4, "Second", albumId = 1, disc = 1, number = 2),
            )
        val album = LibraryGrouping.albums(tracks, en).single()
        assertEquals(listOf(2L, 4L, 3L, 1L), album.tracks.map { it.id })
        assertTrue(album.hasMultipleDiscs)
    }

    @Test
    fun `album artist tag wins, otherwise single artist, otherwise various`() {
        val tagged =
            listOf(
                track(1, "a", albumId = 1, artist = "X", albumArtist = "Compiler"),
                track(2, "b", albumId = 1, artist = "Y"),
            )
        assertEquals("Compiler", LibraryGrouping.albums(tagged, en).single().artist)

        val single = listOf(track(1, "a", albumId = 1, artist = "X"), track(2, "b", albumId = 1, artist = "X"))
        val one = LibraryGrouping.albums(single, en).single()
        assertEquals("X", one.artist)
        assertFalse(one.isVarious)

        val mixed = listOf(track(1, "a", albumId = 1, artist = "X"), track(2, "b", albumId = 1, artist = "Y"))
        val various = LibraryGrouping.albums(mixed, en).single()
        assertNull(various.artist)
        assertTrue(various.isVarious)
    }

    @Test
    fun `albums sort by title ignoring leading punctuation, year is the latest`() {
        val tracks =
            listOf(
                track(1, "t", albumId = 1, album = "Ride the Lightning", year = 1984),
                track(2, "t", albumId = 2, album = "... And Justice for All", year = 1988),
                track(3, "t", albumId = 2, album = "... And Justice for All", year = 2018),
            )
        val albums = LibraryGrouping.albums(tracks, en)
        assertEquals(listOf(2L, 1L), albums.map { it.id })
        assertEquals(2018, albums.first().year)
    }

    @Test
    fun `artists group by id with albums newest first and unknown last`() {
        val tracks =
            listOf(
                track(1, "Fuel", albumId = 10, album = "Reload", artistId = 1, artist = "Metallica", year = 1997),
                track(2, "Battery", albumId = 11, album = "Master", artistId = 1, artist = "Metallica", year = 1986),
                track(3, "Untagged", albumId = 12, artistId = 9, artist = null),
                track(4, "Hangar 18", albumId = 13, artistId = 2, artist = "Megadeth"),
            )
        val artists = LibraryGrouping.artists(tracks, en)
        assertEquals(listOf("Megadeth", "Metallica", null), artists.map { it.name })
        val metallica = artists[1]
        assertEquals(listOf(10L, 11L), metallica.albums.map { it.id })
        assertEquals(listOf(1L, 2L), metallica.tracks.map { it.id })
    }

    @Test
    fun `folder root lists top folders with recursive counts`() {
        val tracks =
            listOf(
                track(1, "a", folder = "Music/Rock/", file = "b.mp3"),
                track(2, "b", folder = "Music/Rock/Live/", file = "a.mp3"),
                track(3, "c", folder = "Music/", file = "z.mp3"),
                track(4, "d", folder = "Download/", file = "d.mp3"),
            )
        val root = LibraryGrouping.folderContents(tracks, "", en)
        assertEquals(listOf("Download" to 1, "Music" to 3), root.subfolders.map { it.name to it.trackCount })
        assertTrue(root.tracks.isEmpty())

        val music = LibraryGrouping.folderContents(tracks, "Music/", en)
        assertEquals(listOf(FolderEntry("Rock", "Music/Rock/", 2)), music.subfolders)
        assertEquals(listOf(3L), music.tracks.map { it.id })
        // Play all: folder by folder, file name within each.
        assertEquals(listOf(3L, 1L, 2L), music.allTracks.map { it.id })
    }

    @Test
    fun `folder prefix does not match sibling folders with the same start`() {
        val tracks = listOf(track(1, "a", folder = "Music/Rock/"), track(2, "b", folder = "Music/Rockabilly/"))
        val rock = LibraryGrouping.folderContents(tracks, "Music/Rock/", en)
        assertEquals(listOf(1L), rock.allTracks.map { it.id })
    }

    private fun track(
        id: Long,
        title: String,
        albumId: Long = 0,
        album: String? = "Album",
        artistId: Long = 0,
        artist: String? = "Artist",
        albumArtist: String? = null,
        disc: Int? = null,
        number: Int? = null,
        year: Int? = null,
        folder: String = "Music/",
        file: String = "$title.mp3",
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        artistId = artistId,
        durationMs = 1_000,
        trackNumber = number,
        discNumber = disc,
        year = year,
        folderPath = folder,
        displayName = file,
        sizeBytes = 0,
        mimeType = null,
        dateAddedSeconds = 0,
        albumArtist = albumArtist,
    )
}
