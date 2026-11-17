package io.github.gativrek.momenta.ui.screens.songs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.ui.components.PlaceholderScreen
import io.github.gativrek.momenta.ui.components.TrackRow

@Composable
fun SongsScreen(
    modifier: Modifier = Modifier,
    viewModel: SongsViewModel = viewModel(factory = SongsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SongsContent(
        state = state,
        // Playback arrives in Phase 2; rows are already clickable so the wiring is one line.
        onTrackClick = {},
        modifier = modifier,
    )
}

/** Stateless: renders whatever [state] says. */
@Composable
fun SongsContent(
    state: SongsUiState,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SongsUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is SongsUiState.Loaded ->
            if (state.tracks.isEmpty()) {
                PlaceholderScreen(
                    iconRes = R.drawable.ic_nav_songs,
                    title = stringResource(R.string.songs_empty_title),
                    subtitle = stringResource(R.string.songs_empty_body),
                    modifier = modifier,
                )
            } else {
                LazyColumn(modifier = modifier.fillMaxSize()) {
                    item(key = "header", contentType = "header") {
                        Text(
                            text = pluralStringResource(R.plurals.song_count, state.tracks.size, state.tracks.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }
                    // Stable keys (KICKOFF.md): rows keep their state and animate correctly
                    // when the library changes underneath them.
                    items(state.tracks, key = { it.id }, contentType = { "track" }) { track ->
                        TrackRow(track = track, onClick = { onTrackClick(track) })
                    }
                }
            }
    }
}
