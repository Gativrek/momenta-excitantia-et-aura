package io.github.gativrek.momenta.ui.screens.moments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.MomentsRepository
import io.github.gativrek.momenta.data.settings.MomentsSettingsStore
import io.github.gativrek.momenta.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Moments as any track list sees them: which songs have one, and "Jump to the Moment". */
interface MomentActions {
    fun momentsOf(track: Track): List<MomentEntity>

    /** Plays [track] from [moment]'s run-up (its first Moment if null). */
    fun jumpToMoment(
        track: Track,
        moment: MomentEntity? = null,
    )
}

val LocalMomentActions =
    staticCompositionLocalOf<MomentActions> {
        object : MomentActions {
            override fun momentsOf(track: Track) = emptyList<MomentEntity>()

            override fun jumpToMoment(
                track: Track,
                moment: MomentEntity?,
            ) = Unit
        }
    }

/**
 * How far into a Moment's reaction the app is: 0 normally, rising to 1 while a Moment plays,
 * settling back after. Everything that reacts to a Moment (Now Playing, the backdrop pattern)
 * follows this one value, so they swell and settle together.
 */
val LocalMomentGlow = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** The clip of the Moment playing (or the one just played, while its reaction settles). */
data class MomentClip(
    val uri: String?,
    /** True while inside the Moment; false as the reaction settles. */
    val playing: Boolean,
    /** Plays behind the whole app rather than in the cover. */
    val behind: Boolean = false,
)

val LocalMomentClip = staticCompositionLocalOf<State<MomentClip>> { mutableStateOf(MomentClip(null, false)) }

class MomentsHostViewModel(
    private val connection: PlayerConnection,
    repository: MomentsRepository,
    settings: MomentsSettingsStore,
) : ViewModel() {
    val byTrack = repository.byTrack

    /**
     * The Moment the playing song is inside, or null. Polls the position only while something
     * with Moments is playing and reactions are on (not in Chill mode).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMoment: Flow<MomentEntity?> =
        combine(connection.state, byTrack, settings.settings) { state, byTrack, s ->
            val id = state.nowPlaying?.trackId
            if (s.reactionsActive && state.playWhenReady && id != null) byTrack[id].orEmpty() else emptyList()
        }.distinctUntilChanged()
            .flatMapLatest { moments ->
                if (moments.isEmpty()) {
                    flowOf(null)
                } else {
                    connection.positionUpdates(POSITION_MS).map { pos -> moments.firstOrNull { pos in it } }
                }
            }.distinctUntilChanged()

    fun jump(
        track: Track,
        moment: MomentEntity,
    ) {
        val playing =
            connection.state.value.nowPlaying
                ?.trackId == track.id
        if (playing) {
            connection.seekTo(moment.runUpStartMs)
            if (!connection.state.value.playWhenReady) connection.togglePlayPause()
        } else {
            connection.playTracks(listOf(track), 0, moment.runUpStartMs)
        }
    }

    companion object {
        private const val POSITION_MS = 100L

        val Factory =
            viewModelFactory {
                initializer {
                    val c = (this[APPLICATION_KEY] as App).container
                    MomentsHostViewModel(c.playerConnection, c.momentsRepository, c.momentsSettings)
                }
            }
    }
}

/** Provides [LocalMomentActions] and [LocalMomentGlow] to [content]. */
@Composable
fun MomentsHost(content: @Composable () -> Unit) {
    val viewModel: MomentsHostViewModel = viewModel(factory = MomentsHostViewModel.Factory)
    val byTrack by viewModel.byTrack.collectAsStateWithLifecycle()
    val active by viewModel.activeMoment.collectAsStateWithLifecycle(initialValue = null)
    val inMoment = active != null
    // The clip stays named while the reaction settles, so it fades out instead of vanishing.
    val clip = remember { mutableStateOf(MomentClip(uri = null, playing = false)) }
    val activeClip = active?.clipUri
    val behind = active?.clipBehind == true
    LaunchedEffect(inMoment, activeClip, behind) {
        clip.value =
            if (inMoment) MomentClip(activeClip, playing = true, behind = behind) else clip.value.copy(playing = false)
    }
    val glow =
        animateFloatAsState(
            targetValue = if (inMoment) 1f else 0f,
            animationSpec = tween(if (inMoment) GLOW_IN_MS else GLOW_OUT_MS),
            label = "momentGlow",
        )
    val actions =
        remember(byTrack) {
            object : MomentActions {
                override fun momentsOf(track: Track) = byTrack[track.id].orEmpty()

                override fun jumpToMoment(
                    track: Track,
                    moment: MomentEntity?,
                ) {
                    (moment ?: byTrack[track.id]?.firstOrNull())?.let { viewModel.jump(track, it) }
                }
            }
        }
    CompositionLocalProvider(
        LocalMomentActions provides actions,
        LocalMomentGlow provides glow,
        LocalMomentClip provides clip,
        content = content,
    )
}

/** The reaction swells in quickly and settles slowly, like the feeling does. */
private const val GLOW_IN_MS = 700
private const val GLOW_OUT_MS = 1_400
