package io.github.gativrek.momenta.data.aura

import io.github.gativrek.momenta.data.listening.Listen
import io.github.gativrek.momenta.data.moments.MomentEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** A song and how much of it was heard. */
data class SongTotal(
    val songId: Long,
    val title: String,
    val artist: String?,
    val listenedMs: Long,
    val plays: Int,
)

/** An artist and how long they were heard. */
data class ArtistTotal(
    val name: String,
    val listenedMs: Long,
)

/**
 * An era: the album a stretch of time belonged to. [name] is the album as an era is called
 * ("the GRANBLUE FANTASY: Relink era"), without "Original Soundtrack", "(Deluxe)" and the like.
 */
data class Era(
    val name: String,
    val album: String,
    val artist: String?,
    val listenedMs: Long,
    /** Share of the stretch's listening, 0..1. */
    val share: Float,
)

/** The era of one calendar month. */
data class MonthEra(
    val month: YearMonth,
    val era: Era,
)

/** What was playing on this date [yearsAgo] years back. */
data class OnThisDay(
    val yearsAgo: Int,
    val songs: List<SongTotal>,
)

/** Everything the Aura profile shows that comes from the listening log. */
data class AuraProfile(
    val totalListenedMs: Long = 0,
    val songsHeard: Int = 0,
    /** When the log starts, epoch milliseconds, or null with no listens. */
    val since: Long? = null,
    /** The album of the last [AuraStats.ERA_WINDOW_DAYS] days, if one stands out. */
    val currentEra: Era? = null,
    /** One era per month, newest first, for months where one album stood out. */
    val eras: List<MonthEra> = emptyList(),
    val topSongs: List<SongTotal> = emptyList(),
    val topArtists: List<ArtistTotal> = emptyList(),
    val onThisDay: List<OnThisDay> = emptyList(),
    /** This calendar month so far, and the rank it has reached. */
    val thisMonth: MonthRank = MonthRank(YearMonth.of(1970, 1), 0, AuraRanks.LADDER.first()),
    /** Earlier months with any listening, newest first, each with its rank and era. */
    val pastMonths: List<MonthRank> = emptyList(),
) {
    /** The highest rank any month has reached, this one included. */
    val bestRank: AuraRank get() = (pastMonths.map { it.rank } + thisMonth.rank).maxBy { it.tier }

    val isEmpty: Boolean get() = totalListenedMs == 0L
}

/** The Aura profile's numbers, worked out from the listening log on the phone. Pure logic. */
object AuraStats {
    const val ERA_WINDOW_DAYS = 30L

    /** An album needs this much listening in a stretch to name it. */
    const val ERA_MIN_MS = 10 * 60_000L

    /** ...and this share of the stretch's listening. */
    const val ERA_MIN_SHARE = 0.2f

    const val TOP_SONGS = 5
    const val TOP_ARTISTS = 3
    const val ON_THIS_DAY_SONGS = 3

    fun profile(
        listens: List<Listen>,
        now: Long,
        zone: ZoneId,
    ): AuraProfile {
        val today = date(now, zone)
        val currentMonth = YearMonth.from(today)
        // Static since the 1st for more than a week, or a whole earlier month at Static.
        fun larry(
            month: YearMonth,
            rank: AuraRank,
        ) = rank.tier == 0 && (month != currentMonth || today.dayOfMonth > AuraRanks.LARRY_AFTER_DAYS)
        if (listens.isEmpty()) {
            val bottom = AuraRanks.LADDER.first()
            return AuraProfile(thisMonth = MonthRank(currentMonth, 0, bottom, larry = larry(currentMonth, bottom)))
        }
        val windowStart = now - ERA_WINDOW_DAYS * DAY_MS
        val byMonth = listens.groupBy { YearMonth.from(date(it.startedAt, zone)) }

        fun monthRank(month: YearMonth): MonthRank {
            val inMonth = byMonth[month].orEmpty()
            val ms = inMonth.sumOf { it.listenedMs }
            val rank = AuraRanks.rankFor(ms)
            return MonthRank(month, ms, rank, eraOf(inMonth), larry(month, rank))
        }
        return AuraProfile(
            totalListenedMs = listens.sumOf { it.listenedMs },
            songsHeard = listens.distinctBy { it.songId }.size,
            since = listens.minOf { it.startedAt },
            currentEra = eraOf(listens.filter { it.startedAt in windowStart..now }),
            eras =
                listens
                    .groupBy { YearMonth.from(date(it.startedAt, zone)) }
                    .mapNotNull { (month, inMonth) -> eraOf(inMonth)?.let { MonthEra(month, it) } }
                    .sortedByDescending { it.month },
            topSongs = songTotals(listens).take(TOP_SONGS),
            topArtists = artistTotals(listens).take(TOP_ARTISTS),
            onThisDay = onThisDay(listens, date(now, zone), zone),
            thisMonth = monthRank(currentMonth),
            pastMonths = byMonth.keys.filter { it < currentMonth }.sortedDescending().map(::monthRank),
        )
    }

    /** Time spent inside Moments: each Moment's length, times how often it was heard through. */
    fun hypeMs(moments: List<MomentEntity>): Long = moments.sumOf { it.heardCount * it.durationMs }

    /**
     * An album title as an era is called: no "(Deluxe)", "[Remastered]" or "Original
     * Soundtrack" / "OST" at the end. Falls back to the title as it is if nothing is left.
     */
    fun eraName(album: String): String {
        val name =
            album
                .replace(BRACKETED, " ")
                .replace(SOUNDTRACK_SUFFIX, "")
                .replace(SPACES, " ")
                .trim()
                .trimEnd(':', '-', '–', ',')
                .trim()
        return name.ifEmpty { album.trim() }
    }

    private fun eraOf(listens: List<Listen>): Era? {
        val total = listens.sumOf { it.listenedMs }
        if (total <= 0) return null
        val (key, inAlbum) =
            listens
                .filter { !it.album.isNullOrBlank() }
                .groupBy { it.album!!.trim().lowercase(Locale.ROOT) }
                .maxByOrNull { (_, l) -> l.sumOf { it.listenedMs } } ?: return null
        val listened = inAlbum.sumOf { it.listenedMs }
        val share = listened.toFloat() / total
        if (listened < ERA_MIN_MS || share < ERA_MIN_SHARE || key.isEmpty()) return null
        val album = inAlbum.last().album!!.trim()
        val artist =
            inAlbum
                .mapNotNull { it.artist?.takeIf(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        return Era(eraName(album), album, artist, listened, share)
    }

    private fun songTotals(listens: List<Listen>): List<SongTotal> =
        listens
            .groupBy { it.songId }
            .map { (id, l) -> SongTotal(id, l.last().title, l.last().artist, l.sumOf { it.listenedMs }, l.size) }
            .sortedByDescending { it.listenedMs }

    /** Credits a listen to every artist on it ("A, B" or "A; B"), each once. */
    private fun artistTotals(listens: List<Listen>): List<ArtistTotal> {
        val totals = mutableMapOf<String, Long>()
        val names = mutableMapOf<String, String>()
        listens.forEach { listen ->
            artistsOf(listen.artist).forEach { name ->
                val key = name.lowercase(Locale.ROOT)
                names.putIfAbsent(key, name)
                totals[key] = (totals[key] ?: 0L) + listen.listenedMs
            }
        }
        return totals.map { (key, ms) -> ArtistTotal(names.getValue(key), ms) }.sortedByDescending { it.listenedMs }
    }

    fun artistsOf(artist: String?): List<String> =
        artist
            .orEmpty()
            .split(',', ';', '/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }

    private fun onThisDay(
        listens: List<Listen>,
        today: LocalDate,
        zone: ZoneId,
    ): List<OnThisDay> =
        listens
            .filter {
                val day = date(it.startedAt, zone)
                day.year < today.year && day.monthValue == today.monthValue && day.dayOfMonth == today.dayOfMonth
            }.groupBy { today.year - date(it.startedAt, zone).year }
            .map { (yearsAgo, l) -> OnThisDay(yearsAgo, songTotals(l).take(ON_THIS_DAY_SONGS)) }
            .sortedBy { it.yearsAgo }

    private fun date(
        epochMs: Long,
        zone: ZoneId,
    ): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private const val DAY_MS = 24 * 60 * 60_000L
    private val BRACKETED = Regex("""\([^)]*\)|\[[^]]*]""")
    private val SOUNDTRACK_SUFFIX =
        Regex(
            """[\s:\-–]*\b(original\s+(game\s+)?(motion\s+picture\s+)?soundtrack|soundtrack|ost)\s*$""",
            RegexOption.IGNORE_CASE,
        )
    private val SPACES = Regex("""\s+""")
}
