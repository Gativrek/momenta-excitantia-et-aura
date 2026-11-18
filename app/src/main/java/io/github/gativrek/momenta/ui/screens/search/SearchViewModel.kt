package io.github.gativrek.momenta.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.library.LibrarySearch
import io.github.gativrek.momenta.data.library.SearchResults
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.ui.components.QueueActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** What the search screen shows: the query as typed, and results for it (null until the first search). */
data class SearchUiState(
    val query: String,
    val results: SearchResults?,
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    library: LibraryRepository,
    private val player: PlayerConnection,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    // Kept in SavedStateHandle so the query survives process death and returning from a result.
    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    /**
     * Re-searched when the query settles (150 ms) or the library changes. A search over a
     * few thousand tracks is a few milliseconds, so there's no index: plain scans keep it simple.
     */
    val results: StateFlow<SearchResults?> =
        combine(query.debounce(DEBOUNCE_MS), library.tracksByTitle, library.albums, library.artists) { q, t, al, ar ->
            LibrarySearch.search(q, t, al, ar)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) {
        savedState[KEY_QUERY] = value
    }

    /** Plays [track] with the matching songs as the queue, so "next" stays inside the results. */
    fun play(track: Track) {
        val tracks = results.value?.tracks ?: listOf(track)
        player.playTracks(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    val queueActions =
        QueueActions(
            playNext = { player.playNext(listOf(it)) },
            addToQueue = { player.addToQueue(listOf(it)) },
        )

    companion object {
        private const val KEY_QUERY = "query"
        private const val DEBOUNCE_MS = 150L

        val Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as App).container
                    SearchViewModel(container.libraryRepository, container.playerConnection, createSavedStateHandle())
                }
            }
    }
}
