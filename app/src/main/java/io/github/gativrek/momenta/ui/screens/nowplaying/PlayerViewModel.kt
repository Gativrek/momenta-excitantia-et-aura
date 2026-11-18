package io.github.gativrek.momenta.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.playback.PlayerUiState
import io.github.gativrek.momenta.playback.QueueState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared by the mini-player, Now Playing and the queue. Thin on purpose: the
 * [PlayerConnection] already exposes state as flows, this just scopes it to the UI.
 */
class PlayerViewModel(
    private val connection: PlayerConnection,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = connection.state

    val queue: StateFlow<QueueState> = connection.queue

    val position: Flow<Long> = connection.positionUpdates()

    fun togglePlayPause() = connection.togglePlayPause()

    fun next() = connection.next()

    fun previous() = connection.previous()

    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)

    fun toggleShuffle() = connection.toggleShuffle()

    fun cycleRepeat() = connection.cycleRepeat()

    fun jumpTo(itemIndex: Int) = connection.jumpTo(itemIndex)

    fun remove(itemIndex: Int) = connection.removeFromQueue(itemIndex)

    fun move(
        fromPlayPosition: Int,
        toPlayPosition: Int,
    ) = connection.moveInQueue(fromPlayPosition, toPlayPosition)

    fun setSleepTimer(
        minutes: Int,
        finishTrack: Boolean,
    ) = connection.setSleepTimer(minutes * 60_000L, finishTrack)

    fun cancelSleepTimer() = connection.cancelSleepTimer()

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
