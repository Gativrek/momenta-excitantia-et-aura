package io.github.gativrek.momenta.data.playlists

import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.library.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val entryCount: Int,
    /** The first entry's track, for the cover; null if empty or missing. */
    val cover: Track?,
)

/** One entry as the UI shows it. [track] is null when the file can't be found ("missing"). */
data class PlaylistItem(
    val entryId: Long,
    val position: Int,
    val track: Track?,
    val title: String,
    val artist: String?,
    val durationMs: Long,
)

data class PlaylistDetail(
    val id: Long,
    val name: String,
    val items: List<PlaylistItem>,
) {
    /** What "Play all" plays: every entry that resolved, in order. */
    val playable: List<Track> get() = items.mapNotNull { it.track }

    val durationMs: Long get() = items.sumOf { it.durationMs }
}

/**
 * Playlists, resolved against the live library. Entries are matched to files on every
 * library change ([TrackMatcher]); when a match needed re-linking (new MediaStore ID, moved
 * file) the new fingerprint is written back. Entries that can't be found stay in the
 * playlist, marked missing, and come back by themselves if the file returns.
 */
class PlaylistRepository(
    private val dao: PlaylistDao,
    library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val tracks = library.tracks

    val playlists: Flow<List<PlaylistSummary>> =
        combine(dao.observeSummaries(), tracks) { rows, tracks ->
            val byId = tracks.associateBy { it.id }
            rows.map { row ->
                PlaylistSummary(
                    id = row.id,
                    name = row.name,
                    entryCount = row.entryCount,
                    cover = row.firstMediaStoreId?.let(byId::get),
                )
            }
        }.flowOn(Dispatchers.Default)

    fun playlist(id: Long): Flow<PlaylistDetail?> =
        combine(dao.observePlaylist(id), dao.observeEntries(id), tracks) { playlist, entries, tracks ->
            if (playlist == null) return@combine null
            val matcher = TrackMatcher(tracks)
            val relinked = mutableListOf<PlaylistEntryEntity>()
            val items =
                entries.map { entry ->
                    when (val match = matcher.resolve(entry)) {
                        is TrackMatcher.Match.Found -> {
                            if (match.relinked) relinked += TrackMatcher.relink(entry, match.track)
                            PlaylistItem(
                                entryId = entry.id,
                                position = entry.position,
                                track = match.track,
                                title = match.track.title,
                                artist = match.track.artist,
                                durationMs = match.track.durationMs,
                            )
                        }
                        TrackMatcher.Match.Missing ->
                            PlaylistItem(
                                entryId = entry.id,
                                position = entry.position,
                                track = null,
                                title = entry.title,
                                artist = entry.artist,
                                durationMs = entry.durationMs,
                            )
                    }
                }
            // Fire and forget: the write triggers one more emission, which then matches by ID.
            if (relinked.isNotEmpty()) scope.launch { dao.updateEntries(relinked) }
            PlaylistDetail(id = playlist.id, name = playlist.name, items = items)
        }.flowOn(Dispatchers.Default)

    /** Returns the new playlist's ID. */
    suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }

    suspend fun rename(
        id: Long,
        name: String,
    ) = dao.rename(id, name.trim(), System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.deletePlaylist(id)

    suspend fun add(
        playlistId: Long,
        tracks: List<Track>,
    ) {
        if (tracks.isEmpty()) return
        dao.append(playlistId, tracks.map(TrackMatcher::entryFor), System.currentTimeMillis())
    }

    suspend fun move(
        playlistId: Long,
        from: Int,
        to: Int,
    ) = dao.move(playlistId, from, to, System.currentTimeMillis())

    suspend fun remove(
        playlistId: Long,
        entryId: Long,
    ) = dao.remove(playlistId, entryId, System.currentTimeMillis())
}
