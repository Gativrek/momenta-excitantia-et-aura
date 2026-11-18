package io.github.gativrek.momenta.data.playlists

import io.github.gativrek.momenta.data.library.Track
import kotlin.math.abs

/**
 * Finds the file a playlist entry points at, even after MediaStore handed out new IDs
 * (criterion 14). Resolution order, from KICKOFF.md:
 *
 * 1. ID matches and the file name matches: the same file.
 * 2. Folder + file name match: re-scanned, new ID. Re-link.
 * 3. File name + duration (within 1 s) + size match: moved to another folder. Re-link.
 * 4. Otherwise missing. The entry is kept, never dropped.
 *
 * Build once per library snapshot; [resolve] is then a few map lookups.
 */
class TrackMatcher(
    tracks: List<Track>,
) {
    private val byId = tracks.associateBy { it.id }
    private val byPathAndName = tracks.associateBy { it.folderPath to it.displayName }
    private val byName = tracks.groupBy { it.displayName }

    fun resolve(entry: PlaylistEntryEntity): Match {
        byId[entry.mediaStoreId]?.let { track ->
            // Same file, but it may have moved (MediaStore keeps the ID for a move on one volume)
            // or been re-tagged: refresh the fingerprint so rules 2 and 3 work after a later rescan.
            if (track.displayName == entry.displayName) {
                val drifted = relink(entry, track) != entry
                return Match.Found(track, relinked = drifted)
            }
        }
        byPathAndName[entry.relativePath to entry.displayName]?.let { return Match.Found(it, relinked = true) }
        byName[entry.displayName]
            ?.firstOrNull {
                abs(it.durationMs - entry.durationMs) <= DURATION_TOLERANCE_MS &&
                    it.sizeBytes == entry.sizeBytes
            }?.let { return Match.Found(it, relinked = true) }
        return Match.Missing
    }

    sealed interface Match {
        data class Found(
            val track: Track,
            /** True when the entry's stored fingerprint should be updated to [track]. */
            val relinked: Boolean,
        ) : Match

        data object Missing : Match
    }

    companion object {
        const val DURATION_TOLERANCE_MS = 1_000L

        /** A new entry's fingerprint, taken from the track as it is now. */
        fun entryFor(track: Track): PlaylistEntryEntity =
            PlaylistEntryEntity(
                playlistId = 0,
                position = 0,
                mediaStoreId = track.id,
                relativePath = track.folderPath,
                displayName = track.displayName,
                durationMs = track.durationMs,
                sizeBytes = track.sizeBytes,
                title = track.title,
                artist = track.artist,
            )

        /** [entry] re-pointed at [track], keeping its playlist and position. */
        fun relink(
            entry: PlaylistEntryEntity,
            track: Track,
        ): PlaylistEntryEntity =
            entryFor(track).copy(id = entry.id, playlistId = entry.playlistId, position = entry.position)
    }
}
