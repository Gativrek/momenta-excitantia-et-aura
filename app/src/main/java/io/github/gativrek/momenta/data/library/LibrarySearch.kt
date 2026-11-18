package io.github.gativrek.momenta.data.library

import java.text.Normalizer
import java.util.Locale

data class SearchResults(
    val artists: List<Artist>,
    val albums: List<Album>,
    val tracks: List<Track>,
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Search across the library. Pure, so it runs in JVM tests and off the main thread.
 *
 * Matching ignores case, accents and punctuation ("beyonce" finds "Beyoncé", "aeterna" finds
 * "Æterna"; see [fold]). Every word of the query must appear somewhere in the item's text,
 * in any order: "metallica seasons" finds the song "72 Seasons" by Metallica.
 *
 * Ranking: items whose name starts with the query come first, then items where a word
 * of the name starts with it, then the rest; ties keep the library's own order.
 */
object LibrarySearch {
    fun search(
        query: String,
        tracks: List<Track>,
        albums: List<Album>,
        artists: List<Artist>,
    ): SearchResults {
        val q = fold(query)
        val words = q.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return SearchResults.EMPTY
        return SearchResults(
            artists = rank(artists, q, words, name = { it.name.orEmpty() }, text = { it.name.orEmpty() }),
            albums =
                rank(
                    albums,
                    q,
                    words,
                    name = { it.title.orEmpty() },
                    text = { "${it.title.orEmpty()} ${it.artist.orEmpty()}" },
                ),
            tracks =
                rank(
                    tracks,
                    q,
                    words,
                    name = { it.title },
                    text = { "${it.title} ${it.artist.orEmpty()} ${it.album.orEmpty()}" },
                ),
        )
    }

    private inline fun <T> rank(
        items: List<T>,
        query: String,
        words: List<String>,
        name: (T) -> String,
        text: (T) -> String,
    ): List<T> =
        items
            .mapNotNull { item ->
                val haystack = fold(text(item))
                if (words.all { haystack.contains(it) }) item to score(fold(name(item)), query) else null
            }.sortedBy { it.second } // stable: equal scores keep library order
            .map { it.first }

    private fun score(
        name: String,
        query: String,
    ): Int =
        when {
            name.startsWith(query) -> 0
            name.contains(" $query") -> 1
            else -> 2
        }

    private val marks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Lowercase, strip accents, expand the ligatures NFD leaves alone, and turn punctuation
     * into single spaces, so "Lux Æterna", "lux aeterna" and "LUX-AETERNA" all fold alike.
     */
    internal fun fold(s: String): String =
        Normalizer
            .normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace("æ", "ae")
            .replace("œ", "oe")
            .replace("ß", "ss")
            .replace("ø", "o")
            .replace(separators, " ")
            .trim()
}
