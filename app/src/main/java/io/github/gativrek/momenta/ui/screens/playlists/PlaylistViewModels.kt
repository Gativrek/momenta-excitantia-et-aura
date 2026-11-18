package io.github.gativrek.momenta.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.AppContainer
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.playlists.PlaylistDetail
import io.github.gativrek.momenta.data.playlists.PlaylistItem
import io.github.gativrek.momenta.data.playlists.PlaylistRepository
import io.github.gativrek.momenta.data.playlists.PlaylistSummary
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.ui.components.Loadable
import io.github.gativrek.momenta.ui.screens.NavArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val playlists: PlaylistRepository,
) : ViewModel() {
    val state: StateFlow<Loadable<List<PlaylistSummary>>> =
        playlists.playlists
            .map<List<PlaylistSummary>, Loadable<List<PlaylistSummary>>> { Loadable.Ready(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /** Creates the playlist, then hands its ID to [onCreated] (to open it). */
    fun create(
        name: String,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch { onCreated(playlists.create(name)) }
    }
}

class PlaylistDetailViewModel(
    private val playlists: PlaylistRepository,
    private val player: PlayerConnection,
    private val playlistId: Long,
) : ViewModel() {
    val state: StateFlow<Loadable<PlaylistDetail?>> =
        playlists
            .playlist(playlistId)
            .map<PlaylistDetail?, Loadable<PlaylistDetail?>> { Loadable.Ready(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /** Plays the playlist from [item]; missing entries are skipped. */
    fun play(item: PlaylistItem? = null) {
        val detail = (state.value as? Loadable.Ready)?.value ?: return
        val playable = detail.items.filter { it.track != null }
        val start =
            item?.let { target -> playable.indexOfFirst { it.entryId == target.entryId } }?.coerceAtLeast(0) ?: 0
        player.playTracks(playable.mapNotNull { it.track }, start)
    }

    fun shuffle() {
        val detail = (state.value as? Loadable.Ready)?.value ?: return
        player.shuffleTracks(detail.playable)
    }

    fun rename(name: String) = launch { playlists.rename(playlistId, name) }

    fun delete() = launch { playlists.delete(playlistId) }

    fun move(
        from: Int,
        to: Int,
    ) = launch { playlists.move(playlistId, from, to) }

    fun remove(entryId: Long) = launch { playlists.remove(playlistId, entryId) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** Backs the "Add to playlist" dialog, wherever it's opened from. */
class AddToPlaylistViewModel(
    private val playlists: PlaylistRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    val playlistsState: StateFlow<List<PlaylistSummary>?> =
        playlists.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Adds the tracks with [trackIds] (in that order) to [playlistId]; [onDone] runs after. */
    fun add(
        playlistId: Long,
        trackIds: List<Long>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            playlists.add(playlistId, tracksFor(trackIds))
            onDone()
        }
    }

    fun createAndAdd(
        name: String,
        trackIds: List<Long>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val id = playlists.create(name)
            playlists.add(id, tracksFor(trackIds))
            onDone()
        }
    }

    // Callers pass IDs (the queue only knows MediaStore IDs); the fingerprint needs the full track.
    private suspend fun tracksFor(ids: List<Long>) =
        library.tracks
            .first()
            .associateBy { it.id }
            .let { byId -> ids.mapNotNull(byId::get) }
}

object PlaylistViewModels {
    private val CreationExtras.container: AppContainer get() = (this[APPLICATION_KEY] as App).container

    val Playlists: ViewModelProvider.Factory =
        viewModelFactory { initializer { PlaylistsViewModel(container.playlistRepository) } }

    val Detail: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                PlaylistDetailViewModel(
                    container.playlistRepository,
                    container.playerConnection,
                    requireNotNull(createSavedStateHandle().get<Long>(NavArgs.PLAYLIST_ID)),
                )
            }
        }

    val AddTo: ViewModelProvider.Factory =
        viewModelFactory {
            initializer { AddToPlaylistViewModel(container.playlistRepository, container.libraryRepository) }
        }
}
