package io.github.gativrek.momenta.data.library

import java.util.Locale

/**
 * A rescan-proof name for an album: its title and folder, not its MediaStore ID (which can
 * change). An album spread over several folders has one key per folder.
 */
object AlbumKey {
    fun of(track: Track): String =
        track.album
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty() + "|" + track.folderPath

    fun of(album: Album): Set<String> = album.tracks.mapTo(mutableSetOf(), ::of)
}
