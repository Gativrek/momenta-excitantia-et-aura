package io.github.gativrek.momenta.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayTrackerTest {
    private var now = 0L
    private val listens = mutableListOf<Listen>()
    private val tracker = PlayTracker({ now }, { 1_000_000 + now }) { listens += it }

    @Test
    fun `auto advance records a completed listen`() {
        tracker.onTrackChanged(1, completed = false, isPlaying = false)
        tracker.onPlayingChanged(true, 1)
        now = 200_000
        tracker.onTrackChanged(2, completed = true, isPlaying = true)
        assertEquals(listOf(Listen(1, 1_000_000, 200_000, completed = true)), listens)
    }

    @Test
    fun `pauses do not count`() {
        tracker.onTrackChanged(1, completed = false, isPlaying = true)
        now = 10_000
        tracker.onPlayingChanged(false, 1)
        now = 500_000
        tracker.onPlayingChanged(true, 1)
        now = 505_000
        tracker.onTrackChanged(2, completed = false, isPlaying = true)
        assertEquals(15_000, listens.single().listenedMs)
        assertEquals(false, listens.single().completed)
    }

    @Test
    fun `a restored queue that never played is not a listen`() {
        tracker.onTrackChanged(1, completed = false, isPlaying = false)
        now = 60_000
        tracker.onTrackChanged(2, completed = false, isPlaying = false)
        tracker.flush()
        assertTrue(listens.isEmpty())
    }

    @Test
    fun `flicking past a track is not a listen`() {
        tracker.onTrackChanged(1, completed = false, isPlaying = true)
        now = 300
        tracker.onTrackChanged(2, completed = false, isPlaying = true)
        now = 30_300
        tracker.flush()
        assertEquals(listOf(2L), listens.map { it.trackId })
    }

    @Test
    fun `playing again after the queue ended starts a new listen`() {
        tracker.onTrackChanged(1, completed = false, isPlaying = true)
        now = 100_000
        tracker.onPlayingChanged(false, 1)
        tracker.onEnded()
        now = 200_000
        tracker.onPlayingChanged(true, 1)
        now = 250_000
        tracker.flush()
        assertEquals(listOf(true, false), listens.map { it.completed })
        assertEquals(listOf(100_000L, 50_000L), listens.map { it.listenedMs })
    }

    @Test
    fun `repeat one gives one listen per loop`() {
        tracker.onTrackChanged(7, completed = false, isPlaying = true)
        now = 180_000
        tracker.onTrackChanged(7, completed = true, isPlaying = true)
        now = 360_000
        tracker.onTrackChanged(7, completed = true, isPlaying = true)
        assertEquals(2, listens.size)
        assertTrue(listens.all { it.completed && it.listenedMs == 180_000L })
    }
}
