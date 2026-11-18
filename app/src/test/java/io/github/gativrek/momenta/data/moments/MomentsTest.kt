package io.github.gativrek.momenta.data.moments

import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.listening.SongEntity
import io.github.gativrek.momenta.playback.MomentTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsTest {
    private fun moment(
        id: Long,
        start: Long,
        end: Long,
    ) = MomentEntity(id = id, songId = 1, startMs = start, endMs = end, createdAt = 0)

    @Test
    fun `saving at the hit reaches back for the build-up`() {
        assertEquals(455_000L..470_000L, Moments.windowAt(460_000, 600_000))
    }

    @Test
    fun `saved window stays inside the song`() {
        assertEquals(0L..12_000L, Moments.windowAt(2_000, 600_000))
        assertEquals(195_000L..200_000L, Moments.windowAt(200_000, 200_000))
    }

    @Test
    fun `boost gain is about 2_3 dB at the maximum`() {
        assertEquals(0, Moments.boostGainMb(0))
        assertEquals(227, Moments.boostGainMb(30))
        assertTrue(Moments.boostGainMb(10) in 80..85)
    }

    @Test
    fun `run-up never starts before the song`() {
        assertEquals(0L, moment(1, 3_000, 9_000).runUpStartMs)
        assertEquals(452_000L, moment(1, 460_000, 476_000).runUpStartMs)
    }

    @Test
    fun `tracker reports the active moment and one heard all the way through`() {
        val tracker = MomentTracker()
        val m = listOf(moment(1, 10_000, 20_000))
        assertNull(tracker.update(5, 9_900, m).active)
        assertEquals(1L, tracker.update(5, 10_050, m).active?.id)
        var t = 10_050L
        while (t < 19_950) {
            t += 100
            assertNull(tracker.update(5, t, m).heard)
        }
        val after = tracker.update(5, 20_050, m)
        assertNull(after.active)
        assertEquals(1L, after.heard?.id)
    }

    @Test
    fun `seeking into the middle or out early is not heard through`() {
        val tracker = MomentTracker()
        val m = listOf(moment(1, 10_000, 20_000))
        tracker.update(5, 15_000, m) // seeked into the middle
        assertNull(tracker.update(5, 20_100, m).heard)

        tracker.update(5, 10_000, m)
        tracker.update(5, 12_000, m)
        assertNull(tracker.update(5, 40_000, m).heard) // seeked away
    }

    @Test
    fun `changing track forgets the entered moment`() {
        val tracker = MomentTracker()
        val m = listOf(moment(1, 10_000, 20_000))
        tracker.update(5, 10_000, m)
        tracker.update(5, 19_900, m)
        assertNull(tracker.update(6, 20_000, emptyList()).heard)
    }

    @Test
    fun `moments follow their file after a rescan`() {
        val song =
            SongEntity(
                id = 1,
                mediaStoreId = 46,
                relativePath = "Music/",
                displayName = "END OF RAGNAROK.mp3",
                durationMs = 400_000,
                sizeBytes = 9_000_000,
                title = "END OF RAGNAROK",
                artist = null,
                album = null,
            )
        val rescanned = track(id = 999, name = "END OF RAGNAROK.mp3")
        val grouped = MomentsRepository.group(listOf(rescanned), listOf(MomentRow(moment(7, 1, 2), song)))
        assertEquals(listOf(7L), grouped[999]?.map { it.id })
        assertTrue(
            MomentsRepository.group(listOf(track(5, "other.mp3")), listOf(MomentRow(moment(7, 1, 2), song))).isEmpty(),
        )
    }

    private fun track(
        id: Long,
        name: String,
    ) = Track(
        id = id,
        title = name,
        artist = null,
        album = null,
        albumId = 1,
        artistId = 1,
        durationMs = 400_000,
        trackNumber = null,
        discNumber = null,
        year = null,
        folderPath = "Music/",
        displayName = name,
        sizeBytes = 9_000_000,
        mimeType = "audio/mpeg",
        dateAddedSeconds = 0,
    )
}
