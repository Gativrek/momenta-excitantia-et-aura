package io.github.gativrek.momenta.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.MomentSuggestion
import io.github.gativrek.momenta.data.moments.MomentsRepository
import io.github.gativrek.momenta.data.notes.LinerNotesRepository
import io.github.gativrek.momenta.data.settings.MomentsSettings
import io.github.gativrek.momenta.data.settings.MomentsSettingsStore
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.playback.PlayerUiState
import io.github.gativrek.momenta.playback.QueueState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared by the mini-player, Now Playing and the queue. Thin on purpose: the
 * [PlayerConnection] already exposes state as flows, this just scopes it to the UI.
 */
class PlayerViewModel(
    private val connection: PlayerConnection,
    private val moments: MomentsRepository,
    private val momentsSettings: MomentsSettingsStore,
    library: LibraryRepository,
    notes: LinerNotesRepository,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = connection.state

    val queue: StateFlow<QueueState> = connection.queue

    val position: Flow<Long> = connection.positionUpdates()

    /** Finer position for Now Playing, so a Moment's reaction starts on the beat. */
    val finePosition: Flow<Long> = connection.positionUpdates(FINE_POSITION_MS)

    /** The playing song's Moments, by start. */
    val currentMoments: StateFlow<List<MomentEntity>> =
        combine(connection.state, moments.byTrack) { state, byTrack ->
            state.nowPlaying
                ?.trackId
                ?.let(byTrack::get)
                .orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<MomentsSettings> = momentsSettings.settings

    /** The playing song's liner note, else its album's. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val linerNote: StateFlow<String?> =
        combine(connection.state.map { it.nowPlaying?.trackId }.distinctUntilChanged(), library.tracks) { id, tracks ->
            id?.let { tracks.firstOrNull { t -> t.id == it } }
        }.distinctUntilChanged()
            .flatMapLatest { track ->
                if (track == null) flowOf(null) else notes.notesFor(track).map { it.song ?: it.album }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A section of the playing song that keeps getting replayed, offered as a Moment. */
    val suggestion: StateFlow<MomentSuggestion?> =
        combine(connection.state, moments.suggestion) { state, suggestion ->
            suggestion?.takeIf { it.trackId == state.nowPlaying?.trackId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun togglePlayPause() = connection.togglePlayPause()

    fun next() = connection.next()

    fun previous() = connection.previous()

    fun previousTrack() = connection.previousTrack()

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

    /** ⚡: a Moment at the current position. [onSaved] gets its ID, to offer the lightning-gear. */
    fun saveMoment(onSaved: (Long) -> Unit) {
        viewModelScope.launch { connection.saveMoment()?.let(onSaved) }
    }

    /** Saves the offered section as a Moment; [onSaved] gets its ID, to offer the lightning-gear. */
    fun saveSuggestion(onSaved: (Long) -> Unit) {
        val section = suggestion.value ?: return
        viewModelScope.launch { connection.saveMoment(section.startMs..section.endMs)?.let(onSaved) }
    }

    fun dismissSuggestion() = moments.dismissSuggestion()

    fun updateMoment(moment: MomentEntity) {
        viewModelScope.launch { moments.update(moment) }
    }

    fun deleteMoment(moment: MomentEntity) {
        viewModelScope.launch { moments.delete(moment.id) }
    }

    /** Hear a Moment of the playing song with its run-up. */
    fun previewMoment(moment: MomentEntity) {
        connection.seekTo(moment.runUpStartMs)
        if (!state.value.playWhenReady) connection.togglePlayPause()
    }

    fun setBoosts(on: Boolean) = momentsSettings.setBoosts(on)

    fun setChill(on: Boolean) = momentsSettings.setChill(on)

    companion object {
        private const val FINE_POSITION_MS = 100L

        val Factory =
            viewModelFactory {
                initializer {
                    val c = (this[APPLICATION_KEY] as App).container
                    PlayerViewModel(
                        c.playerConnection,
                        c.momentsRepository,
                        c.momentsSettings,
                        c.libraryRepository,
                        c.linerNotes,
                    )
                }
            }
    }
}
