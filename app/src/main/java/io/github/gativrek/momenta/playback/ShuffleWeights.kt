package io.github.gativrek.momenta.playback

import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.listening.PlayRef
import io.github.gativrek.momenta.data.sorting.PlayMatching
import kotlin.math.ln
import kotlin.random.Random

/**
 * Shuffle that leans toward songs you haven't heard in a while. Still random, still every
 * song once: a song's weight only changes how likely it is to come up early.
 *
 * A song played just now weighs 1; the weight climbs evenly to [MAX_WEIGHT] over
 * [FULL_WEIGHT_DAYS] since its last play, and a song never played weighs [MAX_WEIGHT] too.
 * So something unheard for a month is ten times as likely as yesterday's song to be next.
 */
object ShuffleWeights {
    const val MAX_WEIGHT = 10.0
    const val FULL_WEIGHT_DAYS = 30.0
    private const val DAY_MS = 24.0 * 60 * 60 * 1000

    fun weight(
        lastPlayedAt: Long?,
        nowMs: Long,
    ): Double {
        if (lastPlayedAt == null) return MAX_WEIGHT
        val days = (nowMs - lastPlayedAt).coerceAtLeast(0) / DAY_MS
        return 1.0 + (MAX_WEIGHT - 1.0) * (days / FULL_WEIGHT_DAYS).coerceAtMost(1.0)
    }

    /**
     * Indices 0 until weights.size in a random order where heavier ones tend to come first
     * (Efraimidis–Spirakis: each gets the key ln(u) / w for a uniform u, sorted high to low;
     * the same as drawing one at a time without replacement, in proportion to weight).
     */
    fun order(
        weights: DoubleArray,
        random: Random = Random,
    ): List<Int> =
        weights.indices
            .map { i -> i to ln(1.0 - random.nextDouble()) / weights[i].coerceAtLeast(MIN_WEIGHT) }
            .sortedByDescending { it.second }
            .map { it.first }

    /** [items] shuffled by [order], each weighted by [weightOf]. */
    fun <T> shuffled(
        items: List<T>,
        random: Random = Random,
        weightOf: (T) -> Double,
    ): List<T> = order(DoubleArray(items.size) { weightOf(items[it]) }, random).map(items::get)

    /** When each track was last heard (a counted play, not a skip), epoch ms, by track ID. */
    fun lastPlayed(
        tracks: List<Track>,
        plays: List<PlayRef>,
    ): Map<Long, Long> =
        PlayMatching
            .byTrack(tracks, plays)
            .mapNotNull { (id, list) -> list.filter(PlayMatching::counts).maxOfOrNull { it.startedAt }?.let { id to it } }
            .toMap()

    private const val MIN_WEIGHT = 1e-6
}
