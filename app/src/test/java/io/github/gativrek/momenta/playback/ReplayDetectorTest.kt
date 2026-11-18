package io.github.gativrek.momenta.playback

import io.github.gativrek.momenta.data.moments.MomentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayDetectorTest {
    @Test
    fun `seeking back to the same spot twice offers that section`() {
        val detector = ReplayDetector()
        assertNull(detector.onSeek(1, 80_000, 60_000, emptyList()))
        assertEquals(62_000L..82_000L, detector.onSeek(1, 82_000, 62_000, emptyList()))
    }

    @Test
    fun `a section is offered once`() {
        val detector = ReplayDetector()
        detector.onSeek(1, 80_000, 60_000, emptyList())
        detector.onSeek(1, 80_000, 60_000, emptyList())
        assertNull(detector.onSeek(1, 80_000, 60_000, emptyList()))
    }

    @Test
    fun `nudges, restarts and other songs don't count`() {
        val detector = ReplayDetector()
        detector.onSeek(1, 61_000, 60_000, emptyList()) // a nudge
        assertNull(detector.onSeek(1, 61_500, 60_000, emptyList()))
        detector.onSeek(1, 200_000, 60_000, emptyList()) // starting over, far back
        assertNull(detector.onSeek(2, 80_000, 60_000, emptyList())) // another song: starts afresh
    }

    @Test
    fun `nothing is offered where a Moment already is`() {
        val detector = ReplayDetector()
        val moment = MomentEntity(id = 1, songId = 1, startMs = 65_000, endMs = 75_000, createdAt = 0)
        detector.onSeek(1, 80_000, 60_000, listOf(moment))
        assertNull(detector.onSeek(1, 80_000, 60_000, listOf(moment)))
    }

    @Test
    fun `long sections are capped`() {
        val detector = ReplayDetector()
        detector.onSeek(1, 140_000, 60_000, emptyList())
        assertEquals(60_000L..90_000L, detector.onSeek(1, 140_000, 60_000, emptyList()))
    }
}
