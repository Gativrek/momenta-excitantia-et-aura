package io.github.gativrek.momenta.data.smart

import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.listening.PlayRef
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPlaylistsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    private fun track(
        id: Long,
        added: Long = id,
    ) = Track(
        id = id,
        title = "Song $id",
        artist = null,
        album = null,
        albumId = 0,
        artistId = 0,
        durationMs = 200_000,
        trackNumber = null,
        discNumber = null,
        year = null,
        folderPath = "Music/",
        displayName = "$id.mp3",
        sizeBytes = 1,
        mimeType = null,
        dateAddedSeconds = added,
    )

    private fun play(
        id: Long,
        daysAgo: Long,
        listenedMs: Long = 180_000,
        completed: Boolean = true,
        mediaStoreId: Long = id,
    ) = PlayRef(mediaStoreId, "Music/", "$id.mp3", now - daysAgo * day, listenedMs, completed)

    private fun lists(
        tracks: List<Track>,
        plays: List<PlayRef>,
    ) = SmartPlaylists.build(tracks, plays, now).associate { it.kind to it.tracks.map(Track::id) }

    @Test
    fun `on repeat needs two counted listens in the last two weeks`() {
        val tracks = (1L..3L).map { track(it) }
        val plays =
            listOf(
                play(1, 1),
                play(1, 2),
                play(1, 3),
                play(2, 1),
                play(2, 20),
                play(3, 1),
                // A skip doesn't count.
                play(3, 2, listenedMs = 5_000, completed = false),
            )
        assertEquals(listOf(1L), lists(tracks, plays)[SmartKind.ON_REPEAT])
    }

    @Test
    fun `most played ranks by counted listens`() {
        val tracks = (1L..3L).map { track(it) }
        val plays = listOf(play(2, 5), play(2, 6), play(1, 7), play(3, 1, listenedMs = 1_000, completed = false))
        assertEquals(listOf(2L, 1L), lists(tracks, plays)[SmartKind.MOST_PLAYED])
    }

    @Test
    fun `forgotten favourites were loved and then not heard for two months`() {
        val tracks = (1L..2L).map { track(it) }
        val plays = (1..3).map { play(1, 100L + it) } + (1..3).map { play(2, 100L + it) } + play(2, 10)
        assertEquals(listOf(1L), lists(tracks, plays)[SmartKind.FORGOTTEN])
    }

    @Test
    fun `never played leaves out anything heard, even a skip or a file found again after a rescan`() {
        val tracks = (1L..3L).map { track(it) }
        // Song 1's MediaStore ID changed since it was heard: found by folder and file name.
        val plays = listOf(play(1, 3, mediaStoreId = 99), play(2, 3, listenedMs = 2_000, completed = false))
        assertEquals(listOf(3L), lists(tracks, plays)[SmartKind.NEVER_PLAYED])
    }

    @Test
    fun `recently added is newest first`() {
        val tracks = listOf(track(1, added = 10), track(2, added = 30), track(3, added = 20))
        assertEquals(listOf(2L, 3L, 1L), lists(tracks, emptyList())[SmartKind.RECENTLY_ADDED])
    }
}
