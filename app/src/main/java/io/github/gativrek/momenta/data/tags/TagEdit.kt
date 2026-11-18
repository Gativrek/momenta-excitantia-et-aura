package io.github.gativrek.momenta.data.tags

import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.Track

/**
 * A change to a file's tags. For every field: null leaves the tag as it is, "" removes it,
 * anything else sets it (trimmed). Pure Kotlin, so the rules are unit-tested.
 *
 * Applied to TagLib's property map, the format-independent view of a file's tags
 * (ID3 frames, Vorbis comments and MP4 atoms all map to the same keys).
 */
data class TagEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
) {
    private val fields: Map<String, String?>
        get() = mapOf(TITLE to title, ARTIST to artist, ALBUM to album, ALBUM_ARTIST to albumArtist)

    /** True when this edit changes nothing at all. */
    val isEmpty: Boolean get() = fields.values.all { it == null }

    /**
     * [properties] with this edit applied. Every other key is carried over untouched:
     * TagLib replaces the whole property map on save, so dropping a key would delete that tag.
     */
    fun applyTo(properties: Map<String, Array<String>>): HashMap<String, Array<String>> {
        val result = HashMap(properties)
        fields.forEach { (key, value) ->
            when {
                value == null -> Unit
                value.isBlank() -> result.remove(key)
                else -> result[key] = arrayOf(value.trim())
            }
        }
        return result
    }

    /** Whether [properties] (as read back from a file) show this edit applied. */
    fun isAppliedIn(properties: Map<String, Array<String>>): Boolean =
        fields.all { (key, value) ->
            when {
                value == null -> true
                value.isBlank() -> properties[key].isNullOrEmpty()
                else -> properties[key]?.firstOrNull() == value.trim()
            }
        }

    companion object {
        const val TITLE = "TITLE"
        const val ARTIST = "ARTIST"
        const val ALBUM = "ALBUM"
        const val ALBUM_ARTIST = "ALBUMARTIST"

        /**
         * The edit for one song from what was typed: only fields that differ from what the
         * library shows are written, so saving an untouched form changes nothing.
         */
        fun forTrack(
            track: Track,
            title: String,
            artist: String,
            album: String,
            albumArtist: String,
        ) = TagEdit(
            title = changed(track.title, title),
            artist = changed(track.artist, artist),
            album = changed(track.album, album),
            albumArtist = changed(track.albumArtist, albumArtist),
        )

        /**
         * Per-track edits for a whole album. [artist] and [albumArtist] are null when the
         * field was left on "keep each song's own" (the tracks disagree and nothing was typed).
         * Tracks that end up unchanged are left out, so they aren't rewritten.
         */
        fun forAlbum(
            album: Album,
            title: String,
            artist: String?,
            albumArtist: String?,
        ): List<Pair<Track, TagEdit>> =
            album.tracks
                .map { track ->
                    track to
                        TagEdit(
                            album = changed(track.album, title),
                            artist = artist?.let { changed(track.artist, it) },
                            albumArtist = albumArtist?.let { changed(track.albumArtist, it) },
                        )
                }.filterNot { it.second.isEmpty }

        /** The value both sides of an album agree on, or null when the tracks differ. */
        fun shared(values: List<String?>): String? = values.distinct().singleOrNull()

        /** [typed] if it differs from [current] (both trimmed, absent = ""), else null. */
        private fun changed(
            current: String?,
            typed: String,
        ): String? = typed.trim().takeIf { it != current.orEmpty().trim() }
    }
}
