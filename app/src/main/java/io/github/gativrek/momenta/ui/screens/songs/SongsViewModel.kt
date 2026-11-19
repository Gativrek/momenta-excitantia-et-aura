package io.github.gativrek.momenta.ui.screens.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.sorting.SortOrder
import io.github.gativrek.momenta.data.sorting.SortingRepository
import io.github.gativrek.momenta.data.sorting.TrackSorting
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.ui.components.QueueActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

sealed interface SongsUiState {
    data object Loading : SongsUiState

    data class Loaded(
        val tracks: List<Track>,
        val order: SortOrder,
    ) : SongsUiState
}

class SongsViewModel(
    libraryRepository: LibraryRepository,
    private val sorting: SortingRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {
    val uiState: StateFlow<SongsUiState> =
        combine(libraryRepository.tracks, sorting.stats, sorting.songsOrder) { tracks, stats, order ->
            SongsUiState.Loaded(TrackSorting.sort(tracks, order, stats), order) as SongsUiState
        }.flowOn(Dispatchers.Default)
            // WhileSubscribed(5 s): stop collecting shortly after the screen leaves, but survive
            // a configuration change (which re-subscribes within milliseconds).
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsUiState.Loading)

    fun setOrder(order: SortOrder) = sorting.setSongsOrder(order)

    /** Play [track], with the whole list (in its on-screen order) as the queue. */
    fun play(track: Track) {
        val tracks = (uiState.value as? SongsUiState.Loaded)?.tracks ?: return
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerConnection.playTracks(tracks, index)
    }

    fun playAll() {
        val tracks = (uiState.value as? SongsUiState.Loaded)?.tracks ?: return
        playerConnection.playTracks(tracks)
    }

    fun shuffleAll() {
        val tracks = (uiState.value as? SongsUiState.Loaded)?.tracks ?: return
        playerConnection.shuffleTracks(tracks)
    }

    val queueActions =
        QueueActions(
            playNext = { playerConnection.playNext(listOf(it)) },
            addToQueue = { playerConnection.addToQueue(listOf(it)) },
        )

    companion object {
        /** Manual DI: pulls dependencies from the App's container. */
        val Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as App).container
                    SongsViewModel(container.libraryRepository, container.sorting, container.playerConnection)
                }
            }
    }
}
