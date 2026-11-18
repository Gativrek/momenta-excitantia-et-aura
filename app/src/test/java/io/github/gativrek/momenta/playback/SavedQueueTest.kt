package io.github.gativrek.momenta.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedQueueTest {
    private val queue =
        SavedQueue(
            trackIds = listOf(10, 20, 30, 40),
            index = 2,
            positionMs = 61_000,
            shuffle = true,
            playOrder = listOf(3, 2, 0, 1),
        )

    @Test
    fun `everything still there restores unchanged`() {
        assertEquals(queue, queue.keepOnly(setOf(10, 20, 30, 40)))
    }

    @Test
    fun `missing tracks are dropped and indices remapped`() {
        val result = queue.keepOnly(setOf(20, 30, 40))!!
        assertEquals(listOf(20L, 30L, 40L), result.trackIds)
        // Track 30 is now item 1; position kept.
        assertEquals(1, result.index)
        assertEquals(61_000L, result.positionMs)
        // Old order 3,2,0,1 without item 0, remapped: 40->2, 30->1, 20->0.
        assertEquals(listOf(2, 1, 0), result.playOrder)
    }

    @Test
    fun `current track gone resumes at the next survivor in play order from its start`() {
        // Current is item 2 (track 30); in play order it's followed by item 0 (track 10).
        val result = queue.keepOnly(setOf(10, 20, 40))!!
        assertEquals(listOf(10L, 20L, 40L), result.trackIds)
        assertEquals(0, result.index)
        assertEquals(0L, result.positionMs)
    }

    @Test
    fun `nothing left means nothing to restore`() {
        assertNull(queue.keepOnly(emptySet()))
    }

    @Test
    fun `lists round-trip through text`() {
        assertEquals(
            listOf(5L, 123456789012L),
            SavedQueue.decodeLongs(SavedQueue.encodeList(listOf(5L, 123456789012L))),
        )
        assertEquals(listOf(2, 0, 1), SavedQueue.decodeInts("2,0,1"))
        assertNull(SavedQueue.decodeLongs(""))
        assertNull(SavedQueue.decodeLongs("1,x"))
    }
}
