package io.github.gativrek.momenta.data.sorting

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.listening.ListeningDao
import io.github.gativrek.momenta.data.moments.MomentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * How each list is sorted (kept across launches: Songs has one order, each playlist its
 * own), and the per-track numbers the listening sorts need.
 */
class SortingRepository(
    private val dataStore: DataStore<Preferences>,
    library: LibraryRepository,
    listening: ListeningDao,
    moments: MomentsRepository,
    private val scope: CoroutineScope,
) {
    /** Every track's listening and Moment numbers, refreshed as the log and Moments change. */
    val stats: Flow<Map<Long, TrackStats>> =
        combine(library.tracks, listening.observePlays(), moments.byTrack, TrackSorting::stats)
            .flowOn(Dispatchers.Default)
            .shareIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), replay = 1)

    val songsOrder: Flow<SortOrder> = order(SONGS_KEY, SortOrder.SONGS_DEFAULT)

    fun playlistOrder(playlistId: Long): Flow<SortOrder> =
        order(PLAYLIST_PREFIX + playlistId, SortOrder.PLAYLIST_DEFAULT)

    fun setSongsOrder(order: SortOrder) = set(SONGS_KEY, order)

    fun setPlaylistOrder(
        playlistId: Long,
        order: SortOrder,
    ) = set(PLAYLIST_PREFIX + playlistId, order)

    private fun order(
        key: String,
        default: SortOrder,
    ): Flow<SortOrder> =
        dataStore.data
            .map { SortOrder.decode(it[stringPreferencesKey(key)]) ?: default }
            .distinctUntilChanged()

    private fun set(
        key: String,
        order: SortOrder,
    ) {
        scope.launch { dataStore.edit { it[stringPreferencesKey(key)] = order.encode() } }
    }

    private companion object {
        const val SONGS_KEY = "songs"
        const val PLAYLIST_PREFIX = "playlist:"
        const val KEEP_ALIVE_MS = 5_000L
    }
}
