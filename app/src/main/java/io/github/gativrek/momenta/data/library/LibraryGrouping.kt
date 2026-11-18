package io.github.gativrek.momenta.data.library

import java.text.Collator
import java.util.Locale

/** An album as the UI shows it. Built from tracks, never stored (MediaStore is the source of truth). */
data class Album(
    /** MediaStore ALBUM_ID. */
    val id: Long,
    val title: String?,
    /** Album artist tag, else the single track artist; null when the tracks disagree (compilation). */
    val artist: String?,
    /** True when tracks have different artists and no album-artist tag says who it's by. */
    val isVarious: Boolean,
    val year: Int?,
    /** Tracks in play order: disc, then track number, then title. */
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }

    /** The track whose cover represents the album. */
    val coverTrack: Track get() = tracks.first()

    val hasMultipleDiscs: Boolean get() = tracks.mapNotNullTo(HashSet()) { it.discNumber }.size > 1

    /** False for untagged "albums" (e.g. MediaStore's folder-name grouping): no numbers to show. */
    val hasTrackNumbers: Boolean get() = tracks.any { it.trackNumber != null }
}

data class Artist(
    /** MediaStore ARTIST_ID. */
    val id: Long,
    /** Null for untagged tracks ("Unknown artist" in the UI). */
    val name: String?,
    /** Albums this artist appears on, newest first. */
    val albums: List<Album>,
    /** Every track by this artist, in album order. */
    val tracks: List<Track>,
)

data class FolderEntry(
    /** Display name, e.g. "Momenta Excitantia et Aura". */
    val name: String,
    /** Full path with trailing slash, e.g. "Music/Momenta Excitantia et Aura/". */
    val path: String,
    /** Tracks in this folder and everything below it. */
    val trackCount: Int,
)

data class FolderContents(
    val path: String,
    val subfolders: List<FolderEntry>,
    /** Tracks directly in this folder, by file name. */
    val tracks: List<Track>,
    /** Everything under this folder, for "Play all": by folder path, then file name. */
    val allTracks: List<Track>,
)

/**
 * Pure functions that group the flat track list into albums, artists and folders.
 * Cheap enough to recompute on every library change: one pass plus sorts over a few
 * thousand tracks takes milliseconds, and it keeps MediaStore the only source of truth.
 */
object LibraryGrouping {
    private fun collator(locale: Locale) = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }

    /** Album play order. Untagged disc counts as disc 1; untagged track numbers go last. */
    val albumTrackOrder: Comparator<Track> =
        compareBy<Track> { it.discNumber ?: 1 }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy { TrackFields.sortKey(it.title).lowercase() }
            .thenBy { it.id }

    fun albums(
        tracks: List<Track>,
        locale: Locale = Locale.getDefault(),
    ): List<Album> {
        val collator = collator(locale)
        return tracks
            .groupBy { it.albumId }
            .map { (id, albumTracks) -> album(id, albumTracks) }
            .sortedWith(
                compareBy(collator) { album: Album -> TrackFields.sortKey(album.title.orEmpty()) }.thenBy { it.id },
            )
    }

    private fun album(
        id: Long,
        tracks: List<Track>,
    ): Album {
        val ordered = tracks.sortedWith(albumTrackOrder)
        val tagged = ordered.firstNotNullOfOrNull { it.albumArtist }
        val distinctArtists = ordered.mapTo(LinkedHashSet()) { it.artist }
        val artist = tagged ?: distinctArtists.singleOrNull()
        return Album(
            id = id,
            title = ordered.firstNotNullOfOrNull { it.album },
            artist = artist,
            isVarious = tagged == null && distinctArtists.size > 1,
            year = ordered.mapNotNull { it.year }.maxOrNull(),
            tracks = ordered,
        )
    }

    fun artists(
        tracks: List<Track>,
        locale: Locale = Locale.getDefault(),
    ): List<Artist> {
        val collator = collator(locale)
        val albumsById = albums(tracks, locale).associateBy { it.id }
        return tracks
            .groupBy { it.artistId }
            .map { (id, artistTracks) ->
                val albums =
                    artistTracks
                        .mapTo(LinkedHashSet()) { it.albumId }
                        .mapNotNull(albumsById::get)
                        .sortedWith(compareByDescending<Album> { it.year ?: 0 }.thenBy(collator) { it.title.orEmpty() })
                val albumRank = albums.withIndex().associate { (index, album) -> album.id to index }
                Artist(
                    id = id,
                    name = artistTracks.firstNotNullOfOrNull { it.artist },
                    albums = albums,
                    tracks =
                        artistTracks.sortedWith(
                            compareBy<Track> { albumRank[it.albumId] ?: Int.MAX_VALUE }.then(albumTrackOrder),
                        ),
                )
            }.sortedWith(
                // Unknown artist last; everyone else alphabetically, ignoring leading punctuation.
                compareBy<Artist> { it.name == null }
                    .thenBy(collator) { TrackFields.sortKey(it.name.orEmpty()) }
                    .thenBy { it.id },
            )
    }

    /**
     * One level of the folder tree. [path] is "" for the root or a folder path with a
     * trailing slash. Subfolders are the next path segment under [path].
     */
    fun folderContents(
        tracks: List<Track>,
        path: String,
        locale: Locale = Locale.getDefault(),
    ): FolderContents {
        val collator = collator(locale)
        val below = tracks.filter { it.folderPath.startsWith(path) }
        val direct = below.filter { it.folderPath == path }.sortedWith(compareBy(collator) { it.displayName })
        val subfolders =
            below
                .filter { it.folderPath != path }
                .groupBy { it.folderPath.removePrefix(path).substringBefore('/') }
                .map { (name, folderTracks) ->
                    FolderEntry(name = name, path = "$path$name/", trackCount = folderTracks.size)
                }.sortedWith(compareBy(collator) { it.name })
        val all =
            below.sortedWith(compareBy(collator) { t: Track -> t.folderPath }.thenBy(collator) { it.displayName })
        return FolderContents(path = path, subfolders = subfolders, tracks = direct, allTracks = all)
    }
}
