package io.github.gativrek.momenta.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.playback.PlayerUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared by the mini-player and the Now Playing screen. Thin on purpose: the
 * [PlayerConnection] already exposes state as flows, this just scopes it to the UI.
 */
class PlayerViewModel(
    private val connection: PlayerConnection,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = connection.state

    val position: Flow<Long> = connection.positionUpdates()

    fun togglePlayPause() = connection.togglePlayPause()

    fun next() = connection.next()

    fun previous() = connection.previous()

    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as App
                    PlayerViewModel(app.container.playerConnection)
                }
            }
    }
}
