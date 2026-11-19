package io.github.gativrek.momenta.data.sorting

import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.library.TrackFields
import io.github.gativrek.momenta.data.listening.PlayRef
import io.github.gativrek.momenta.data.moments.MomentEntity

/** What a list of songs can be sorted by. */
enum class SortField(
    val key: String,
    /** Which way it goes when first picked: A to Z for names, the most first for amounts. */
    val descendingFirst: Boolean,
) {
    /** A playlist's own order, as arranged by hand. Playlists only. */
    MANUAL("manual", descendingFirst = false),
    NAME("name", descendingFirst = false),
    ARTIST("artist", descendingFirst = false),
    ALBUM("album", descendingFirst = false),
    DATE_ADDED("date_added", descendingFirst = true),
    LENGTH("length", descendingFirst = true),
    FILE_SIZE("file_size", descendingFirst = true),
    TIME_LISTENED("time_listened", descendingFirst = true),
    TIMES_PLAYED("times_played", descendingFirst = true),
    LAST_PLAYED("last_played", descendingFirst = true),
    MOMENTS("moments", descendingFirst = true),
    MOMENTS_HEARD("moments_heard", descendingFirst = true),
    ;

    companion object {
        fun fromKey(key: String?): SortField? = entries.firstOrNull { it.key == key }

        /** The sorts the Songs list offers (no manual order there). */
        val forSongs: List<SortField> = entries - MANUAL
        val forPlaylists: List<SortField> = entries
    }
}

data class SortOrder(
    val field: SortField,
    val descending: Boolean,
) {
    /** Stored as "field:asc" or "field:desc". */
    fun encode(): String = field.key + if (descending) ":desc" else ":asc"

    companion object {
        val SONGS_DEFAULT = SortOrder(SortField.NAME, descending = false)
        val PLAYLIST_DEFAULT = SortOrder(SortField.MANUAL, descending = false)

        fun decode(raw: String?): SortOrder? {
            val field = SortField.fromKey(raw?.substringBefore(':')) ?: return null
            return SortOrder(field, raw?.substringAfter(':', "asc") == "desc")
        }
    }
}

/** What the listening log and Moments say about one track, for sorting. */
data class TrackStats(
    val listenedMs: Long = 0,
    /** Listens that counted: [PlayMatching.COUNTED_MS] or more, or to the end. */
    val plays: Int = 0,
    /** Epoch ms of the latest listen, 0 if never. */
    val lastPlayedAt: Long = 0,
    val moments: Int = 0,
    /** Times its Moments were heard all the way through, all added up. */
    val momentsHeard: Int = 0,
) {
    companion object {
        val NONE = TrackStats()
    }
}

/** Ties the listening log's plays to tracks in the library as it is now. */
object PlayMatching {
    /** A listen counts as a play once it ran this long, or reached the end of a shorter song. */
    const val COUNTED_MS = 30_000L

    fun counts(play: PlayRef) = play.completed || play.listenedMs >= COUNTED_MS

    /**
     * Each track's plays. A play is tied to a track by MediaStore ID and file name, or else
     * folder and file name, the way the log found the song when it was heard. Plays of files
     * no longer in the library are left out.
     */
    fun byTrack(
        tracks: List<Track>,
        plays: List<PlayRef>,
    ): Map<Long, List<PlayRef>> {
        val byId = tracks.associateBy { it.id to it.displayName }
        val byPath = tracks.associateBy { it.folderPath to it.displayName }
        val out = mutableMapOf<Long, MutableList<PlayRef>>()
        for (play in plays) {
            val track = byId[play.mediaStoreId to play.displayName] ?: byPath[play.folderPath to play.displayName]
            if (track != null) out.getOrPut(track.id) { mutableListOf() } += play
        }
        return out
    }
}

object TrackSorting {
    fun stats(
        tracks: List<Track>,
        plays: List<PlayRef>,
        momentsByTrack: Map<Long, List<MomentEntity>>,
    ): Map<Long, TrackStats> {
        val playsByTrack = PlayMatching.byTrack(tracks, plays)
        return tracks.associate { track ->
            val heard = playsByTrack[track.id].orEmpty()
            val moments = momentsByTrack[track.id].orEmpty()
            track.id to
                TrackStats(
                    listenedMs = heard.sumOf { it.listenedMs },
                    plays = heard.count(PlayMatching::counts),
                    lastPlayedAt = heard.maxOfOrNull { it.startedAt } ?: 0,
                    moments = moments.size,
                    momentsHeard = moments.sumOf { it.heardCount },
                )
        }
    }

    /**
     * [items] in [order]; [trackOf] gives each item's track, null for one that can't be sorted
     * (a missing playlist entry), which goes last. Equal items fall back to the title, A to Z,
     * whichever way the sort runs. [SortField.MANUAL] keeps the given order (reversed if
     * descending).
     */
    fun <T> sort(
        items: List<T>,
        order: SortOrder,
        stats: Map<Long, TrackStats>,
        trackOf: (T) -> Track?,
    ): List<T> {
        if (order.field == SortField.MANUAL) return if (order.descending) items.reversed() else items
        val primary = comparator(order.field, stats, order.descending)
        val byTitle = TrackFields.titleComparator()
        val tracks = Comparator<Track> { a, b -> primary.compare(a, b).takeIf { it != 0 } ?: byTitle.compare(a, b) }
        return items.sortedWith { a, b ->
            val ta = trackOf(a)
            val tb = trackOf(b)
            when {
                ta == null && tb == null -> 0
                ta == null -> 1
                tb == null -> -1
                else -> tracks.compare(ta, tb)
            }
        }
    }

    fun sort(
        tracks: List<Track>,
        order: SortOrder,
        stats: Map<Long, TrackStats>,
    ): List<Track> = sort(tracks, order, stats) { it }

    private fun comparator(
        field: SortField,
        stats: Map<Long, TrackStats>,
        descending: Boolean,
    ): Comparator<Track> {
        fun of(track: Track) = stats[track.id] ?: TrackStats.NONE
        val names = TrackFields.titleComparator()
        val ascending: Comparator<Track> =
            when (field) {
                SortField.MANUAL, SortField.NAME -> names
                // Untagged artists and albums sort after every named one, either way round.
                SortField.ARTIST -> return textComparator(descending) { it.artist }
                SortField.ALBUM -> return textComparator(descending) { it.album }
                SortField.DATE_ADDED -> compareBy { it.dateAddedSeconds }
                SortField.LENGTH -> compareBy { it.durationMs }
                SortField.FILE_SIZE -> compareBy { it.sizeBytes }
                SortField.TIME_LISTENED -> compareBy { of(it).listenedMs }
                SortField.TIMES_PLAYED -> compareBy { of(it).plays }
                SortField.LAST_PLAYED -> compareBy { of(it).lastPlayedAt }
                SortField.MOMENTS -> compareBy { of(it).moments }
                SortField.MOMENTS_HEARD -> compareBy { of(it).momentsHeard }
            }
        return if (descending) ascending.reversed() else ascending
    }

    private fun textComparator(
        descending: Boolean,
        text: (Track) -> String?,
    ): Comparator<Track> {
        val collator = TrackFields.collator()
        return Comparator { a, b ->
            val ta = text(a)
            val tb = text(b)
            when {
                ta == null && tb == null -> 0
                ta == null -> 1
                tb == null -> -1
                else ->
                    collator
                        .compare(
                            TrackFields.sortKey(ta),
                            TrackFields.sortKey(tb),
                        ).let { if (descending) -it else it }
            }
        }
    }
}
