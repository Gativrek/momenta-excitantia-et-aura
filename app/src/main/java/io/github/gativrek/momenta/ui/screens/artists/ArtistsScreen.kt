package io.github.gativrek.momenta.ui.screens.artists

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.gativrek.momenta.data.library.Artist
import io.github.gativrek.momenta.ui.components.Loadable
import io.github.gativrek.momenta.ui.components.LoadingBox
import io.github.gativrek.momenta.ui.components.PlaceholderScreen
import io.github.gativrek.momenta.ui.screens.ArtistsViewModel
import io.github.gativrek.momenta.ui.screens.LibraryViewModels

@Composable
fun ArtistsScreen(
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistsViewModel = viewModel(factory = LibraryViewModels.Artists),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        Loadable.Loading -> LoadingBox(modifier)
        is Loadable.Ready ->
            if (s.value.isEmpty()) {
                PlaceholderScreen(
                    iconRes = R.drawable.ic_nav_artists,
                    title = stringResource(R.string.songs_empty_title),
                    subtitle = stringResource(R.string.songs_empty_body),
                    modifier = modifier,
                )
            } else {
                LazyColumn(modifier = modifier.fillMaxSize()) {
                    items(s.value, key = { it.id }, contentType = { "artist" }) { artist ->
                        ArtistRow(artist = artist, onClick = { onArtistClick(artist) })
                    }
                }
            }
    }
}

@Composable
private fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No artist photos offline, so a line-icon avatar instead of a picture.
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_artists),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = artist.name ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistSummary(artist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "3 albums · 27 songs". */
@Composable
fun artistSummary(artist: Artist): String =
    pluralStringResource(R.plurals.album_count, artist.albums.size, artist.albums.size) + " · " +
        pluralStringResource(R.plurals.song_count, artist.tracks.size, artist.tracks.size)
