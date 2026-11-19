package io.github.gativrek.momenta.data.sorting

import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.listening.PlayRef
import io.github.gativrek.momenta.data.moments.MomentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSortingTest {
    private fun track(
        id: Long,
        title: String,
        artist: String? = null,
        sizeBytes: Long = 1,
        durationMs: Long = 1,
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = null,
        albumId = 0,
        artistId = 0,
        durationMs = durationMs,
        trackNumber = null,
        discNumber = null,
        year = null,
        folderPath = "Music/",
        displayName = "$id.mp3",
        sizeBytes = sizeBytes,
        mimeType = null,
        dateAddedSeconds = id,
    )

    private val a = track(1, "Alpha", artist = "Zed", sizeBytes = 30)
    private val b = track(2, "beta", artist = null, sizeBytes = 10)
    private val c = track(3, "Charlie", artist = "Abba", sizeBytes = 20)
    private val tracks = listOf(c, a, b)

    private fun ids(list: List<Track>) = list.map { it.id }

    @Test
    fun `name sorts like a person reads, both ways`() {
        assertEquals(listOf(1L, 2L, 3L), ids(TrackSorting.sort(tracks, SortOrder(SortField.NAME, false), emptyMap())))
        assertEquals(listOf(3L, 2L, 1L), ids(TrackSorting.sort(tracks, SortOrder(SortField.NAME, true), emptyMap())))
    }

    @Test
    fun `songs without an artist go last either way`() {
        assertEquals(listOf(3L, 1L, 2L), ids(TrackSorting.sort(tracks, SortOrder(SortField.ARTIST, false), emptyMap())))
        assertEquals(listOf(1L, 3L, 2L), ids(TrackSorting.sort(tracks, SortOrder(SortField.ARTIST, true), emptyMap())))
    }

    @Test
    fun `file size`() {
        assertEquals(
            listOf(1L, 3L, 2L),
            ids(TrackSorting.sort(tracks, SortOrder(SortField.FILE_SIZE, true), emptyMap())),
        )
    }

    @Test
    fun `listening and Moment numbers come from the log, ties by name`() {
        val plays =
            listOf(
                PlayRef(2, "Music/", "2.mp3", startedAt = 100, listenedMs = 200_000, completed = true),
                PlayRef(2, "Music/", "2.mp3", startedAt = 300, listenedMs = 5_000, completed = false),
                PlayRef(3, "Music/", "3.mp3", startedAt = 200, listenedMs = 100_000, completed = true),
            )
        val moments =
            mapOf(
                1L to
                    listOf(
                        MomentEntity(songId = 1, startMs = 0, endMs = 1, heardCount = 2, createdAt = 0),
                        MomentEntity(songId = 1, startMs = 5, endMs = 6, heardCount = 3, createdAt = 0),
                    ),
                3L to listOf(MomentEntity(songId = 3, startMs = 0, endMs = 1, heardCount = 9, createdAt = 0)),
            )
        val stats = TrackSorting.stats(tracks, plays, moments)
        assertEquals(TrackStats(205_000, 1, 300, 0, 0), stats[2])

        fun sorted(field: SortField) = ids(TrackSorting.sort(tracks, SortOrder(field, true), stats))
        assertEquals(listOf(2L, 3L, 1L), sorted(SortField.TIME_LISTENED))
        // 2 and 3 have one counted play each: the tie goes by name.
        assertEquals(listOf(2L, 3L, 1L), sorted(SortField.TIMES_PLAYED))
        assertEquals(listOf(2L, 3L, 1L), sorted(SortField.LAST_PLAYED))
        assertEquals(listOf(1L, 3L, 2L), sorted(SortField.MOMENTS))
        assertEquals(listOf(3L, 1L, 2L), sorted(SortField.MOMENTS_HEARD))
    }

    @Test
    fun `manual keeps the given order, reversed when descending, and missing entries go last otherwise`() {
        val entries = listOf("x" to c, "missing" to null, "y" to a)

        fun keys(order: SortOrder) = TrackSorting.sort(entries, order, emptyMap()) { it.second }.map { it.first }
        assertEquals(listOf("x", "missing", "y"), keys(SortOrder(SortField.MANUAL, false)))
        assertEquals(listOf("y", "missing", "x"), keys(SortOrder(SortField.MANUAL, true)))
        assertEquals(listOf("y", "x", "missing"), keys(SortOrder(SortField.NAME, false)))
        assertEquals(listOf("x", "y", "missing"), keys(SortOrder(SortField.NAME, true)))
    }

    @Test
    fun `orders are stored and read back`() {
        val order = SortOrder(SortField.MOMENTS_HEARD, descending = true)
        assertEquals(order, SortOrder.decode(order.encode()))
        assertEquals(null, SortOrder.decode("nonsense:desc"))
    }
}
