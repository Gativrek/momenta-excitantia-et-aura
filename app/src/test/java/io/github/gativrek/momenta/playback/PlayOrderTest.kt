package io.github.gativrek.momenta.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class PlayOrderTest {
    @Test
    fun `move shifts the entries in between`() {
        assertEquals(listOf(0, 2, 3, 1, 4), PlayOrder.move(listOf(0, 1, 2, 3, 4), from = 1, to = 3))
        assertEquals(listOf(3, 0, 1, 2), PlayOrder.move(listOf(0, 1, 2, 3), from = 3, to = 0))
    }

    @Test
    fun `fresh shuffle starts with the current track and contains every track once`() {
        val order = PlayOrder.shuffledStartingWith(first = 7, count = 20, random = Random(42))
        assertEquals(7, order.first())
        assertEquals((0 until 20).toList(), order.sorted())
    }

    @Test
    fun `fresh shuffle of nothing is empty`() {
        assertEquals(emptyList<Int>(), PlayOrder.shuffledStartingWith(first = 0, count = 0))
    }

    @Test
    fun `play next with shuffle on goes right after the current track in play order`() {
        // Shuffled order of 4 items, current item is 1 (play position 2).
        val order = listOf(3, 0, 1, 2)
        // Two new items inserted at item index 2 (after the current item in item order).
        val result = PlayOrder.insert(order, at = 2, count = 2, afterPosition = 2)
        // Old 2 and 3 shift to 4 and 5; new 2 and 3 follow the current item 1.
        assertEquals(listOf(5, 0, 1, 2, 3, 4), result)
    }

    @Test
    fun `add to queue goes to the end of the play order`() {
        val order = listOf(2, 0, 1)
        assertEquals(listOf(2, 0, 1, 3), PlayOrder.insert(order, at = 3, count = 1, afterPosition = order.lastIndex))
    }

    @Test
    fun `insert before everything`() {
        assertEquals(listOf(0, 1, 2), PlayOrder.insert(listOf(0, 1), at = 0, count = 1, afterPosition = -1))
    }
}
