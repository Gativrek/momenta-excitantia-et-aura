package io.github.gativrek.momenta.ui.screens.artists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.Artist
import io.github.gativrek.momenta.ui.components.DetailTopBar
import io.github.gativrek.momenta.ui.components.Loadable
import io.github.gativrek.momenta.ui.components.LoadingBox
import io.github.gativrek.momenta.ui.components.PlayShuffleButtons
import io.github.gativrek.momenta.ui.components.QueueActions
import io.github.gativrek.momenta.ui.components.TrackRow
import io.github.gativrek.momenta.ui.screens.ArtistDetailViewModel
import io.github.gativrek.momenta.ui.screens.LibraryViewModels
import io.github.gativrek.momenta.ui.screens.albums.AlbumCard

@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlayStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = viewModel(factory = LibraryViewModels.ArtistDetail),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        Loadable.Loading -> LoadingBox(modifier)
        is Loadable.Ready -> {
            val artist = s.value
            if (artist == null) {
                LaunchedEffect(Unit) { onBack() }
                return
            }
            ArtistDetailContent(
                artist = artist,
                onBack = onBack,
                onAlbumClick = onAlbumClick,
                onPlay = { index ->
                    viewModel.play(artist.tracks, index)
                    onPlayStarted()
                },
                onShuffle = {
                    viewModel.shuffle(artist.tracks)
                    onPlayStarted()
                },
                queueActions = viewModel.queueActions,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun ArtistDetailContent(
    artist: Artist,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlay: (index: Int) -> Unit,
    onShuffle: () -> Unit,
    queueActions: QueueActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        DetailTopBar(title = artist.name ?: stringResource(R.string.unknown_artist), onBack = onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "summary", contentType = "summary") {
                Text(
                    text = artistSummary(artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
            item(key = "actions", contentType = "actions") {
                PlayShuffleButtons(onPlayAll = { onPlay(0) }, onShuffle = onShuffle)
            }
            item(
                key = "albums-title",
                contentType = "section",
            ) { SectionTitle(stringResource(R.string.section_albums)) }
            item(key = "albums", contentType = "albums") {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(artist.albums, key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = { onAlbumClick(album) }, modifier = Modifier.width(140.dp))
                    }
                }
            }
            item(key = "songs-title", contentType = "section") { SectionTitle(stringResource(R.string.section_songs)) }
            itemsIndexed(artist.tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                TrackRow(track = track, onClick = { onPlay(index) }, queueActions = queueActions)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}
