package io.github.gativrek.momenta.data.playlists

import io.github.gativrek.momenta.data.library.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMatcherTest {
    private fun track(
        id: Long,
        folder: String = "Music/A/",
        name: String = "song.mp3",
        durationMs: Long = 200_000,
        sizeBytes: Long = 5_000_000,
    ) = Track(
        id = id,
        title = "Song",
        artist = "Artist",
        album = "Album",
        albumId = 1,
        artistId = 1,
        durationMs = durationMs,
        trackNumber = null,
        discNumber = null,
        year = null,
        folderPath = folder,
        displayName = name,
        sizeBytes = sizeBytes,
        mimeType = "audio/mpeg",
        dateAddedSeconds = 0,
    )

    private val original = track(id = 10)
    private val entry = TrackMatcher.entryFor(original).copy(id = 1, playlistId = 1)

    @Test
    fun `rule 1, same ID and file name`() {
        assertEquals(
            TrackMatcher.Match.Found(original, relinked = false),
            TrackMatcher(listOf(original)).resolve(entry),
        )
    }

    @Test
    fun `rule 1, same ID moved to another folder refreshes the stored fingerprint`() {
        val moved = track(id = 10, folder = "Music/Moved/")
        assertEquals(TrackMatcher.Match.Found(moved, relinked = true), TrackMatcher(listOf(moved)).resolve(entry))
    }

    @Test
    fun `rule 2, rescanned with a new ID in the same folder`() {
        val rescanned = track(id = 99)
        assertEquals(
            TrackMatcher.Match.Found(rescanned, relinked = true),
            TrackMatcher(listOf(rescanned)).resolve(entry),
        )
    }

    @Test
    fun `an ID reused by a different file is not a match`() {
        val impostor = track(id = 10, name = "other.mp3")
        assertEquals(TrackMatcher.Match.Missing, TrackMatcher(listOf(impostor)).resolve(entry))
    }

    @Test
    fun `rule 3, moved to another folder, same name, duration within 1 s and size`() {
        val moved = track(id = 50, folder = "Music/B/", durationMs = 200_900)
        assertEquals(TrackMatcher.Match.Found(moved, relinked = true), TrackMatcher(listOf(moved)).resolve(entry))
    }

    @Test
    fun `moved file with a different size or duration is not a match`() {
        val resized = track(id = 50, folder = "Music/B/", sizeBytes = 4_000_000)
        val longer = track(id = 51, folder = "Music/C/", durationMs = 202_000)
        assertEquals(TrackMatcher.Match.Missing, TrackMatcher(listOf(resized, longer)).resolve(entry))
    }

    @Test
    fun `rule 4, gone means missing, never dropped`() {
        assertEquals(TrackMatcher.Match.Missing, TrackMatcher(emptyList()).resolve(entry))
    }

    @Test
    fun `relink keeps the entry's identity and position, takes the new fingerprint`() {
        val moved = track(id = 50, folder = "Music/B/")
        val relinked = TrackMatcher.relink(entry.copy(position = 7), moved)
        assertEquals(1L, relinked.id)
        assertEquals(1L, relinked.playlistId)
        assertEquals(7, relinked.position)
        assertEquals(50L, relinked.mediaStoreId)
        assertEquals("Music/B/", relinked.relativePath)
    }
}
