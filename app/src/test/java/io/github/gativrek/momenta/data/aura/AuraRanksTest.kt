package io.github.gativrek.momenta.data.aura

import io.github.gativrek.momenta.data.listening.Listen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class AuraRanksTest {
    private val zone = ZoneOffset.UTC
    private val now = at(2026, 11, 27)

    private fun at(
        year: Int,
        month: Int,
        day: Int,
    ) = LocalDateTime.of(year, month, day, 12, 0).toInstant(zone).toEpochMilli()

    private fun listen(
        startedAt: Long,
        minutes: Long,
        album: String = "Album",
    ) = Listen(1, "Song", "Artist", album, "Music/", startedAt, minutes * 60_000)

    @Test
    fun `the ladder climbs from nothing to The Aura Farmer`() {
        val ladder = AuraRanks.LADDER
        assertEquals(0L, ladder.first().minMinutes)
        assertTrue(ladder.zipWithNext().all { (a, b) -> b.minMinutes > a.minMinutes })
        assertTrue(AuraRanks.TOP.isTop)
        assertEquals(ladder.indices.toList(), ladder.map { it.tier })
    }

    @Test
    fun `a month's minutes pick the highest rank they reach`() {
        assertEquals(0, AuraRanks.rankFor(0).tier)
        assertEquals(0, AuraRanks.rankFor(59 * 60_000L).tier)
        assertEquals(1, AuraRanks.rankFor(60 * 60_000L).tier)
        assertEquals(AuraRanks.TOP, AuraRanks.rankFor(AuraRanks.TOP.minMs))
        assertEquals(AuraRanks.TOP, AuraRanks.rankFor(AuraRanks.TOP.minMs * 3))
    }

    @Test
    fun `progress runs from one rank to the next and is full at the top`() {
        val spark = AuraRanks.LADDER[1]
        val glow = AuraRanks.LADDER[2]
        val halfway = (spark.minMs + glow.minMs) / 2
        assertEquals(0.5f, AuraRanks.progress(halfway), 0.001f)
        assertEquals(0f, AuraRanks.progress(spark.minMs), 0.001f)
        assertEquals(1f, AuraRanks.progress(AuraRanks.TOP.minMs * 2), 0.001f)
        assertNull(AuraRanks.next(AuraRanks.TOP))
    }

    @Test
    fun `this month ranks on its own and earlier months are kept, newest first`() {
        val listens =
            listOf(
                listen(at(2026, 9, 3), 30),
                listen(at(2026, 10, 1), 13 * 60),
                listen(at(2026, 10, 31), 13 * 60, album = "Relink OST"),
                listen(at(2026, 11, 2), 2 * 60),
            )
        val profile = AuraStats.profile(listens, now, zone)
        assertEquals(YearMonth.of(2026, 11), profile.thisMonth.month)
        assertEquals(2 * 60 * 60_000L, profile.thisMonth.listenedMs)
        assertEquals(1, profile.thisMonth.rank.tier)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), profile.pastMonths.map { it.month })
        // 26 h in October: Main Character.
        assertEquals(4, profile.pastMonths.first().rank.tier)
        assertEquals(4, profile.bestRank.tier)
        assertEquals(0, profile.pastMonths.last().rank.tier)
    }

    @Test
    fun `a new month starts at the bottom`() {
        val profile = AuraStats.profile(listOf(listen(at(2026, 10, 15), 100 * 60)), now, zone)
        assertEquals(0L, profile.thisMonth.listenedMs)
        assertEquals(0, profile.thisMonth.rank.tier)
        assertEquals(AuraRanks.TOP, profile.bestRank)
    }
}
