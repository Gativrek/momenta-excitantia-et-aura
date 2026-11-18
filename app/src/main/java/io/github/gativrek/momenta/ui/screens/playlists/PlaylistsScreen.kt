package io.github.gativrek.momenta.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.playlists.PlaylistSummary
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.components.Loadable
import io.github.gativrek.momenta.ui.components.LoadingBox

@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = viewModel(factory = PlaylistViewModels.Playlists),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    when (val s = state) {
        Loadable.Loading -> LoadingBox(modifier)
        is Loadable.Ready ->
            LazyColumn(modifier = modifier.fillMaxSize()) {
                item(key = "new", contentType = "new") { NewPlaylistRow(onClick = { creating = true }) }
                if (s.value.isEmpty()) {
                    item(key = "empty", contentType = "empty") { EmptyPlaylists() }
                }
                items(s.value, key = { it.id }, contentType = { "playlist" }) { playlist ->
                    PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
                }
            }
    }

    if (creating) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = { name ->
                creating = false
                viewModel.create(name, onPlaylistClick)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(R.drawable.ic_plus)
        Text(
            text = stringResource(R.string.playlist_new),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = playlist.cover
        if (cover != null) ArtworkImage(track = cover, size = 56.dp) else IconTile(R.drawable.ic_nav_playlists)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.song_count, playlist.entryCount, playlist.entryCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A line icon on a tinted square, the same size as a cover. */
@Composable
private fun IconTile(iconRes: Int) {
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun EmptyPlaylists() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(stringResource(R.string.playlists_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.playlists_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
