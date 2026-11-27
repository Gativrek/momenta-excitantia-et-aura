package io.github.gativrek.momenta.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleWeightsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    @Test
    fun `weight grows with time since the last play and caps at a month`() {
        assertEquals(1.0, ShuffleWeights.weight(now, now), 1e-9)
        assertEquals(5.5, ShuffleWeights.weight(now - 15 * day, now), 1e-9)
        assertEquals(ShuffleWeights.MAX_WEIGHT, ShuffleWeights.weight(now - 30 * day, now), 1e-9)
        assertEquals(ShuffleWeights.MAX_WEIGHT, ShuffleWeights.weight(now - 400 * day, now), 1e-9)
        assertEquals(ShuffleWeights.MAX_WEIGHT, ShuffleWeights.weight(null, now), 1e-9)
    }

    @Test
    fun `weighted order still has every item once`() {
        val weights = DoubleArray(50) { 1.0 + it % 10 }
        assertEquals((0 until 50).toList(), ShuffleWeights.order(weights, Random(1)).sorted())
    }

    @Test
    fun `heavier items tend to come first`() {
        // Item 0 was just heard; items 1 to 9 haven't been in a month.
        val weights = DoubleArray(10) { if (it == 0) 1.0 else ShuffleWeights.MAX_WEIGHT }
        val random = Random(7)
        val runs = 2_000
        val firstCount = (1..runs).count { ShuffleWeights.order(weights, random).first() == 0 }
        // Expected share 1 / 91, about 22 of 2,000; an even shuffle would give 200.
        assertTrue("first $firstCount times", firstCount < 60)
        val averagePosition = (1..runs).sumOf { ShuffleWeights.order(weights, random).indexOf(0) }.toDouble() / runs
        assertTrue("average position $averagePosition", averagePosition > 6.0)
    }

    @Test
    fun `weighted fresh shuffle keeps the current track first`() {
        val weights = DoubleArray(20) { if (it % 2 == 0) 1.0 else 10.0 }
        val order = PlayOrder.shuffledStartingWith(first = 4, count = 20, random = Random(3), weights = weights)
        assertEquals(4, order.first())
        assertEquals((0 until 20).toList(), order.sorted())
    }
}
