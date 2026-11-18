package io.github.gativrek.momenta.ui.screens.moments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.MomentsRepository
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.theme.moment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One Moment with the song it belongs to. */
data class SavedMoment(
    val track: Track,
    val moment: MomentEntity,
)

class MomentsViewModel(
    private val connection: PlayerConnection,
    library: LibraryRepository,
    repository: MomentsRepository,
) : ViewModel() {
    /** Every Moment, newest first. */
    val moments: StateFlow<List<SavedMoment>> =
        combine(library.tracks, repository.byTrack) { tracks, byTrack ->
            val byId = tracks.associateBy { it.id }
            byTrack
                .flatMap { (trackId, moments) ->
                    val track = byId[trackId] ?: return@flatMap emptyList()
                    moments.map { SavedMoment(track, it) }
                }.sortedByDescending { it.moment.createdAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Plays [saved]'s song from that Moment's run-up. */
    fun play(saved: SavedMoment) = connection.playTracks(listOf(saved.track), 0, saved.moment.runUpStartMs)

    /**
     * Moments mode: every song with a Moment, shuffled, each playing only its Moments with their
     * run-ups, fading from one to the next.
     */
    fun playReel() {
        val songs =
            moments.value
                .groupBy { it.track.id }
                .values
                .map { group -> group.first().track to group.minOf { it.moment.runUpStartMs } }
                .shuffled()
        if (songs.isEmpty()) return
        connection.playTracks(songs.map { it.first }, 0, songs.first().second, reel = true)
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val c = (this[APPLICATION_KEY] as App).container
                    MomentsViewModel(c.playerConnection, c.libraryRepository, c.momentsRepository)
                }
            }
    }
}

/** Your Moments: every one you've saved, and the reel that plays them all back to back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    onBack: () -> Unit,
    onPlayStarted: () -> Unit,
    viewModel: MomentsViewModel = viewModel(factory = MomentsViewModel.Factory),
) {
    val moments by viewModel.moments.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moments_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        if (moments.isEmpty()) {
            EmptyMoments(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                ReelButton(
                    onClick = {
                        viewModel.playReel()
                        onPlayStarted()
                    },
                )
            }
            items(moments, key = { it.moment.id }) { saved ->
                MomentRow(
                    saved = saved,
                    onClick = {
                        viewModel.play(saved)
                        onPlayStarted()
                    },
                )
            }
        }
    }
}

@Composable
private fun ReelButton(onClick: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painterResource(R.drawable.ic_moment),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(
                stringResource(R.string.moments_play_reel),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
            )
        }
        Text(
            text = stringResource(R.string.moments_reel_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MomentRow(
    saved: SavedMoment,
    onClick: () -> Unit,
) {
    val moment = saved.moment
    val named = !moment.name.isNullOrBlank()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(track = saved.track, size = 48.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            // A named Moment leads with its name; otherwise the song does.
            Text(
                text = if (named) moment.name.orEmpty() else saved.track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (named) saved.track.title else saved.track.artist ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatMomentTime(moment.startMs).substringBefore('.'),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.moment,
            )
            if (moment.heardCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.moments_heard_short, moment.heardCount, moment.heardCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyMoments(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_moment),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.moment,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.moments_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.moments_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
