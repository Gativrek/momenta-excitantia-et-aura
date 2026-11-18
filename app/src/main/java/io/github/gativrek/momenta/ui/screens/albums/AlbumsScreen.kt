package io.github.gativrek.momenta.ui.screens.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.components.Loadable
import io.github.gativrek.momenta.ui.components.LoadingBox
import io.github.gativrek.momenta.ui.components.PlaceholderScreen
import io.github.gativrek.momenta.ui.screens.AlbumsViewModel
import io.github.gativrek.momenta.ui.screens.LibraryViewModels

@Composable
fun AlbumsScreen(
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = viewModel(factory = LibraryViewModels.Albums),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        Loadable.Loading -> LoadingBox(modifier)
        is Loadable.Ready ->
            if (s.value.isEmpty()) {
                PlaceholderScreen(
                    iconRes = R.drawable.ic_nav_albums,
                    title = stringResource(R.string.songs_empty_title),
                    subtitle = stringResource(R.string.songs_empty_body),
                    modifier = modifier,
                )
            } else {
                AlbumGrid(albums = s.value, onAlbumClick = onAlbumClick, modifier = modifier)
            }
    }
}

@Composable
fun AlbumGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        // Adaptive: two columns on a phone, more on wider screens, without a breakpoint table.
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick).padding(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            ArtworkImage(track = album.coverTrack, size = maxWidth)
        }
        Text(
            text = album.title ?: stringResource(R.string.unknown_album),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = albumArtistLabel(album),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** "Metallica", "Various artists" or "Unknown artist". */
@Composable
fun albumArtistLabel(album: Album): String =
    when {
        album.artist != null -> album.artist
        album.isVarious -> stringResource(R.string.various_artists)
        else -> stringResource(R.string.unknown_artist)
    }
