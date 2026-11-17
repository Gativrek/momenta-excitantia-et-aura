package io.github.gativrek.momenta.ui.screens.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.library.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SongsUiState {
    data object Loading : SongsUiState

    data class Loaded(
        val tracks: List<Track>,
    ) : SongsUiState
}

class SongsViewModel(
    libraryRepository: LibraryRepository,
) : ViewModel() {
    val uiState: StateFlow<SongsUiState> =
        libraryRepository.tracksByTitle
            .map<List<Track>, SongsUiState> { SongsUiState.Loaded(it) }
            // WhileSubscribed(5 s): stop collecting shortly after the screen leaves, but survive
            // a configuration change (which re-subscribes within milliseconds).
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsUiState.Loading)

    companion object {
        /** Manual DI: pulls the repository from the App's container. */
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as App
                    SongsViewModel(app.container.libraryRepository)
                }
            }
    }
}
