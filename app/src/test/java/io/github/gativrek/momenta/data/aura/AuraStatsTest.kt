package io.github.gativrek.momenta.data.aura

import io.github.gativrek.momenta.data.listening.Listen
import io.github.gativrek.momenta.data.moments.MomentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class AuraStatsTest {
    private val zone = ZoneOffset.UTC
    private val now = at(2026, 11, 18, 20)

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
    ) = LocalDateTime.of(year, month, day, hour, 0).toInstant(zone).toEpochMilli()

    private fun listen(
        songId: Long,
        startedAt: Long,
        minutes: Long,
        album: String? = "Album $songId",
        artist: String? = "Artist",
    ) = Listen(songId, "Song $songId", artist, album, "Music/", startedAt, minutes * 60_000)

    @Test
    fun `era names drop soundtrack and edition suffixes`() {
        assertEquals("GRANBLUE FANTASY: Relink", AuraStats.eraName("GRANBLUE FANTASY: Relink ORIGINAL SOUNDTRACK"))
        assertEquals("Hardwired…To Self-Destruct", AuraStats.eraName("Hardwired…To Self-Destruct (Deluxe)"))
        assertEquals("Doom Eternal", AuraStats.eraName("Doom Eternal (Original Game Soundtrack)"))
        assertEquals("Calamity", AuraStats.eraName("Calamity OST"))
        assertEquals("Ghost", AuraStats.eraName("Ghost"))
        assertEquals("Lost Souls", AuraStats.eraName("Lost Souls"))
        assertEquals("OST", AuraStats.eraName("OST"))
    }

    @Test
    fun `the current era is the album that stands out in the last 30 days`() {
        val listens =
            listOf(
                listen(1, at(2026, 11, 10), 25, album = "Calamity OST"),
                listen(2, at(2026, 11, 11), 5, album = "Other"),
                // Older than the window: doesn't count for the current era.
                listen(3, at(2026, 9, 1), 300, album = "Old Love"),
            )
        val era = AuraStats.profile(listens, now, zone).currentEra
        assertEquals("Calamity", era?.name)
        assertEquals(25 * 60_000L, era?.listenedMs)
    }

    @Test
    fun `no era when nothing stands out`() {
        val spread = (1L..10L).map { listen(it, at(2026, 11, 10), 6) }
        assertNull(AuraStats.profile(spread, now, zone).currentEra)
        val little = listOf(listen(1, at(2026, 11, 10), 3))
        assertNull(AuraStats.profile(little, now, zone).currentEra)
    }

    @Test
    fun `eras by month, newest first`() {
        val listens =
            listOf(
                listen(1, at(2026, 9, 3), 40, album = "September"),
                listen(2, at(2026, 10, 3), 40, album = "October"),
                listen(3, at(2026, 11, 3), 40, album = "November"),
            )
        val eras = AuraStats.profile(listens, now, zone).eras
        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9)),
            eras.map { it.month },
        )
        assertEquals("November", eras.first().era.name)
    }

    @Test
    fun `totals, top songs and artists split on commas`() {
        val listens =
            listOf(
                listen(1, at(2026, 11, 1), 10, artist = "A, B, A"),
                listen(1, at(2026, 11, 2), 10, artist = "A, B, A"),
                listen(2, at(2026, 11, 3), 5, artist = "B"),
            )
        val profile = AuraStats.profile(listens, now, zone)
        assertEquals(25 * 60_000L, profile.totalListenedMs)
        assertEquals(2, profile.songsHeard)
        assertEquals(listOf(1L, 2L), profile.topSongs.map { it.songId })
        assertEquals(2, profile.topSongs.first().plays)
        assertEquals(listOf("B" to 25L, "A" to 20L), profile.topArtists.map { it.name to it.listenedMs / 60_000 })
    }

    @Test
    fun `on this day looks at the same date in earlier years`() {
        val listens =
            listOf(
                listen(1, at(2025, 11, 18), 4),
                listen(2, at(2024, 11, 18), 4),
                listen(3, at(2025, 11, 17), 4),
                listen(4, at(2026, 11, 18), 4),
            )
        val days = AuraStats.profile(listens, now, zone).onThisDay
        assertEquals(listOf(1, 2), days.map { it.yearsAgo })
        assertEquals(listOf(1L), days.first().songs.map { it.songId })
    }

    @Test
    fun `time in hype is each Moment heard through times its length`() {
        val moments =
            listOf(
                MomentEntity(songId = 1, startMs = 0, endMs = 15_000, heardCount = 4, createdAt = 0),
                MomentEntity(songId = 2, startMs = 10_000, endMs = 20_000, heardCount = 0, createdAt = 0),
            )
        assertEquals(60_000L, AuraStats.hypeMs(moments))
    }

    @Test
    fun `nothing heard yet`() {
        assertTrue(AuraStats.profile(emptyList(), now, zone).isEmpty)
    }
}
