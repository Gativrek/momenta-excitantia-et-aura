package io.github.gativrek.momenta.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TrackFieldsTest {
    @Test
    fun `unknown and blank tags become null`() {
        assertNull(TrackFields.tagOrNull("<unknown>"))
        assertNull(TrackFields.tagOrNull("   "))
        assertNull(TrackFields.tagOrNull(null))
        assertEquals("Pendulum", TrackFields.tagOrNull(" Pendulum "))
    }

    @Test
    fun `missing title falls back to file name without extension`() {
        assertEquals("01 - Intro", TrackFields.title(null, "01 - Intro.mp3"))
        assertEquals("No extension", TrackFields.title("", "No extension"))
        assertEquals("Tagged", TrackFields.title("Tagged", "file.flac"))
    }

    @Test
    fun `track column splits into disc and track`() {
        assertEquals(2 to 5, TrackFields.discAndTrack(2005))
        assertEquals(null to 7, TrackFields.discAndTrack(7))
        assertEquals(1 to null, TrackFields.discAndTrack(1000))
        assertEquals(null to null, TrackFields.discAndTrack(0))
        assertEquals(null to null, TrackFields.discAndTrack(null))
    }

    @Test
    fun `relative path is normalised to a single trailing slash`() {
        assertEquals("Music/Foo/", TrackFields.folderFromRelativePath("Music/Foo/"))
        assertEquals("Music/Foo/", TrackFields.folderFromRelativePath("Music/Foo"))
        assertEquals("", TrackFields.folderFromRelativePath(null))
        assertEquals("", TrackFields.folderFromRelativePath("/"))
    }

    @Test
    fun `legacy data path matches relative path shape`() {
        assertEquals("Music/Foo/", TrackFields.folderFromDataPath("/storage/emulated/0/Music/Foo/song.mp3"))
        assertEquals("Music/", TrackFields.folderFromDataPath("/storage/1A2B-3C4D/Music/song.mp3"))
        assertEquals("Download/", TrackFields.folderFromDataPath("/sdcard/Download/a.mp3"))
        assertEquals("", TrackFields.folderFromDataPath("/storage/emulated/0/song.mp3"))
        assertEquals("", TrackFields.folderFromDataPath(null))
    }

    @Test
    fun `songs sort by title ignoring case, then by id`() {
        val tracks =
            listOf(
                track(1, "beta"),
                track(2, "Alpha"),
                track(3, "alpha"),
                track(4, "Árbol"),
                track(5, "Zebra"),
            )
        val sorted = tracks.sortedWith(TrackFields.titleComparator(Locale.ENGLISH)).map { it.id }
        assertEquals(listOf(2L, 3L, 4L, 1L, 5L), sorted)
    }

    @Test
    fun `sort key skips leading punctuation and brackets`() {
        assertEquals("And Justice for All", TrackFields.sortKey("... And Justice for All"))
        assertEquals("con lentitud", TrackFields.sortKey("…con lentitud"))
        assertEquals("Full lyrics] ORISON", TrackFields.sortKey("[Full lyrics] ORISON"))
        assertEquals("72 Seasons", TrackFields.sortKey("72 Seasons"))
        assertEquals("!!!", TrackFields.sortKey("!!!"))
    }

    @Test
    fun `punctuated titles sort among their letters`() {
        val tracks =
            listOf(track(1, "Battery"), track(2, "... And Justice"), track(3, "Zebra"), track(4, "[Live] Fade"))
        val sorted = tracks.sortedWith(TrackFields.titleComparator(Locale.ENGLISH)).map { it.id }
        assertEquals(listOf(2L, 1L, 4L, 3L), sorted)
    }

    private fun track(
        id: Long,
        title: String,
    ) = Track(
        id = id,
        title = title,
        artist = null,
        album = null,
        albumId = 0,
        artistId = 0,
        durationMs = 0,
        trackNumber = null,
        discNumber = null,
        year = null,
        folderPath = "",
        displayName = "$title.mp3",
        sizeBytes = 0,
        mimeType = null,
        dateAddedSeconds = 0,
    )
}
