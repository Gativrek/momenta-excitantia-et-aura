package io.github.gativrek.momenta.data.moments

import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.listening.ListeningDao
import io.github.gativrek.momenta.data.playlists.TrackMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MomentsRepository(
    private val dao: MomentsDao,
    private val listeningDao: ListeningDao,
    library: LibraryRepository,
    scope: CoroutineScope,
) {
    /**
     * Every Moment, grouped by the MediaStore ID of the file it belongs to now, sorted by start.
     * Moments are stored against the song row, so they follow a file through rescans and moves
     * (the same matching as playlists); a Moment whose file is gone is simply left out.
     */
    val byTrack: StateFlow<Map<Long, List<MomentEntity>>> =
        combine(library.tracks, dao.observeAll()) { tracks, rows -> group(tracks, rows) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** A new Moment around [positionMs] (see Moments.windowAt). Returns it, with its ID. */
    suspend fun saveAt(
        track: Track,
        positionMs: Long,
    ): MomentEntity {
        val window = Moments.windowAt(positionMs, track.durationMs)
        val moment =
            MomentEntity(
                songId = listeningDao.songFor(track),
                startMs = window.first,
                endMs = window.last,
                createdAt = System.currentTimeMillis(),
            )
        return moment.copy(id = dao.insert(moment))
    }

    suspend fun update(moment: MomentEntity) = dao.update(moment)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun countHeard(id: Long) = dao.countHeard(id)

    companion object {
        fun group(
            tracks: List<Track>,
            rows: List<MomentRow>,
        ): Map<Long, List<MomentEntity>> {
            if (rows.isEmpty()) return emptyMap()
            val matcher = TrackMatcher(tracks)
            return rows
                .mapNotNull { row -> matcher.find(row.song)?.let { it.id to row.moment } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, moments) -> moments.sortedBy { it.startMs } }
        }
    }
}
