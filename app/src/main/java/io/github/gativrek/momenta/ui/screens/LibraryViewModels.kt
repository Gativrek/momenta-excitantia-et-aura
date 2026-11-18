package io.github.gativrek.momenta.ui.screens

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
import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.Artist
import io.github.gativrek.momenta.data.library.FolderContents
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.ui.components.Loadable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Base for the browse screens: turns one library flow into UI state and offers the two
 * collection actions. Each screen only says *which* flow it shows.
 */
abstract class LibraryViewModel<T>(
    source: Flow<T>,
    private val player: PlayerConnection,
) : ViewModel() {
    val state: StateFlow<Loadable<T>> =
        source
            .map<T, Loadable<T>> { Loadable.Ready(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun play(
        tracks: List<Track>,
        startIndex: Int = 0,
    ) = player.playTracks(tracks, startIndex)

    fun shuffle(tracks: List<Track>) = player.shuffleTracks(tracks)
}

class AlbumsViewModel(
    c: AppContainer,
) : LibraryViewModel<List<Album>>(c.libraryRepository.albums, c.playerConnection)

class AlbumDetailViewModel(
    c: AppContainer,
    albumId: Long,
) : LibraryViewModel<Album?>(c.libraryRepository.album(albumId), c.playerConnection)

class ArtistsViewModel(
    c: AppContainer,
) : LibraryViewModel<List<Artist>>(c.libraryRepository.artists, c.playerConnection)

class ArtistDetailViewModel(
    c: AppContainer,
    artistId: Long,
) : LibraryViewModel<Artist?>(c.libraryRepository.artist(artistId), c.playerConnection)

class FolderViewModel(
    c: AppContainer,
    path: String,
) : LibraryViewModel<FolderContents>(c.libraryRepository.folder(path), c.playerConnection)

/** Navigation argument names, shared by the nav graph and the factories below. */
object NavArgs {
    const val ALBUM_ID = "albumId"
    const val ARTIST_ID = "artistId"
    const val PATH = "path"
}

/** Factories: pull the container from the Application and nav arguments from SavedStateHandle. */
object LibraryViewModels {
    private val CreationExtras.container: AppContainer get() = (this[APPLICATION_KEY] as App).container

    val Albums: ViewModelProvider.Factory = viewModelFactory { initializer { AlbumsViewModel(container) } }
    val Artists: ViewModelProvider.Factory = viewModelFactory { initializer { ArtistsViewModel(container) } }

    val AlbumDetail: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                AlbumDetailViewModel(container, requireNotNull(createSavedStateHandle().get<Long>(NavArgs.ALBUM_ID)))
            }
        }

    val ArtistDetail: ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ArtistDetailViewModel(container, requireNotNull(createSavedStateHandle().get<Long>(NavArgs.ARTIST_ID)))
            }
        }

    val Folder: ViewModelProvider.Factory =
        viewModelFactory {
            initializer { FolderViewModel(container, createSavedStateHandle().get<String>(NavArgs.PATH).orEmpty()) }
        }
}
