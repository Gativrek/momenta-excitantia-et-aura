package io.github.gativrek.momenta.data.smart

import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.listening.PlayRef
import io.github.gativrek.momenta.data.sorting.PlayMatching

/** The playlists the app keeps by itself, from the library and the listening log. */
enum class SmartKind(
    val key: String,
) {
    /** What you keep coming back to lately: two or more listens in the last two weeks. */
    ON_REPEAT("on_repeat"),

    /** Most listens of all time. */
    MOST_PLAYED("most_played"),

    /** Newest files on the phone. */
    RECENTLY_ADDED("recently_added"),

    /** Songs you played a lot that haven't come up in two months. */
    FORGOTTEN("forgotten"),

    /** In the library, never listened to. */
    NEVER_PLAYED("never_played"),
    ;

    companion object {
        fun fromKey(key: String?): SmartKind? = entries.firstOrNull { it.key == key }
    }
}

data class SmartPlaylist(
    val kind: SmartKind,
    val tracks: List<Track>,
)

object SmartPlaylists {
    const val ON_REPEAT_DAYS = 14
    const val ON_REPEAT_MIN = 2
    const val FORGOTTEN_DAYS = 60
    const val FORGOTTEN_MIN = 3
    const val LIMIT = 50
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Every smart playlist for [tracks] (the library as it is now), given the log's [plays]
     * (tied to tracks by [PlayMatching]; a listen counts as a play by [PlayMatching.counts]).
     */
    fun build(
        tracks: List<Track>,
        plays: List<PlayRef>,
        nowMs: Long,
    ): List<SmartPlaylist> {
        val playsByTrack = PlayMatching.byTrack(tracks, plays)
        val heard = playsByTrack.keys
        val counted =
            playsByTrack
                .mapValues { (_, list) -> list.filter(PlayMatching::counts).map { it.startedAt } }
                .filterValues { it.isNotEmpty() }
        val trackById = tracks.associateBy { it.id }

        // Tracks whose counted listens pass [keep], highest [score] first, then most recently heard.
        fun ranked(
            keep: (List<Long>) -> Boolean,
            score: (List<Long>) -> Int,
        ): List<Track> =
            counted.entries
                .filter { keep(it.value) }
                .sortedWith(
                    compareByDescending<Map.Entry<Long, List<Long>>> { score(it.value) }
                        .thenByDescending { it.value.max() },
                ).mapNotNull { trackById[it.key] }
                .take(LIMIT)

        val repeatSince = nowMs - ON_REPEAT_DAYS * DAY_MS
        val forgottenBefore = nowMs - FORGOTTEN_DAYS * DAY_MS
        val recent: (List<Long>) -> Int = { times -> times.count { it >= repeatSince } }
        return listOf(
            SmartPlaylist(SmartKind.ON_REPEAT, ranked({ recent(it) >= ON_REPEAT_MIN }, recent)),
            SmartPlaylist(SmartKind.MOST_PLAYED, ranked({ true }) { it.size }),
            SmartPlaylist(SmartKind.RECENTLY_ADDED, tracks.sortedByDescending { it.dateAddedSeconds }.take(LIMIT)),
            SmartPlaylist(
                SmartKind.FORGOTTEN,
                ranked({ it.size >= FORGOTTEN_MIN && it.max() < forgottenBefore }) { it.size },
            ),
            SmartPlaylist(
                SmartKind.NEVER_PLAYED,
                tracks.filter { it.id !in heard }.sortedByDescending { it.dateAddedSeconds },
            ),
        )
    }
}
