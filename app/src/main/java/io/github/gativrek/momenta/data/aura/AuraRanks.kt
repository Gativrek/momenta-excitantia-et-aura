package io.github.gativrek.momenta.data.aura

import java.time.YearMonth

/**
 * A rung of the Aura ladder. Climbed by minutes listened in one calendar month, back to the
 * bottom on the 1st. [tier] 0 is the bottom; the names live in strings.xml (`aura_ranks`),
 * in the same order.
 */
data class AuraRank(
    val tier: Int,
    val minMinutes: Long,
) {
    val minMs: Long get() = minMinutes * 60_000

    val isTop: Boolean get() = tier == AuraRanks.LADDER.lastIndex
}

/** One month's listening and the rank it reached. */
data class MonthRank(
    val month: YearMonth,
    val listenedMs: Long,
    val rank: AuraRank,
    /** The album that stood out that month, if one did. */
    val era: Era? = null,
    /**
     * Static for more than [AuraRanks.LARRY_AFTER_DAYS] days: you're Larper Larry, The Aura
     * Farmer's enemy, until you climb out. A whole month at Static is a Larry month.
     */
    val larry: Boolean = false,
)

object AuraRanks {
    /** Days at Static before you turn into Larper Larry. */
    const val LARRY_AFTER_DAYS = 7

    /**
     * Minutes per month for each rank, bottom to top. 80 hours a month is about 2 h 40 min a
     * day: a lot, but a long trip with headphones on gets there.
     */
    val LADDER: List<AuraRank> =
        listOf(0L, 60, 5 * 60, 12 * 60, 25 * 60, 40 * 60, 60 * 60, 80 * 60)
            .mapIndexed { tier, minutes -> AuraRank(tier, minutes) }

    val TOP: AuraRank get() = LADDER.last()

    /** The highest rank [listenedMs] in a month reaches. */
    fun rankFor(listenedMs: Long): AuraRank = LADDER.last { listenedMs >= it.minMs }

    /** The rank after [rank], or null at the top. */
    fun next(rank: AuraRank): AuraRank? = LADDER.getOrNull(rank.tier + 1)

    /** How far from [rank] to the next one [listenedMs] is, 0..1 (1 at the top). */
    fun progress(
        listenedMs: Long,
        rank: AuraRank = rankFor(listenedMs),
    ): Float {
        val next = next(rank) ?: return 1f
        return ((listenedMs - rank.minMs).toFloat() / (next.minMs - rank.minMs)).coerceIn(0f, 1f)
    }
}
