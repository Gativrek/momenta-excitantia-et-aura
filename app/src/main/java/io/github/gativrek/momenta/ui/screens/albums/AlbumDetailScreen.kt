package io.github.gativrek.momenta.ui.screens.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.components.DetailTopBar
import io.github.gativrek.momenta.ui.components.Loadable
import io.github.gativrek.momenta.ui.components.LoadingBox
import io.github.gativrek.momenta.ui.components.PlayShuffleButtons
import io.github.gativrek.momenta.ui.format.formatDuration
import io.github.gativrek.momenta.ui.screens.AlbumDetailViewModel
import io.github.gativrek.momenta.ui.screens.LibraryViewModels

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onPlayStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = viewModel(factory = LibraryViewModels.AlbumDetail),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        Loadable.Loading -> LoadingBox(modifier)
        is Loadable.Ready -> {
            val album = s.value
            // The album vanished (files deleted or moved): nothing to show, go back.
            if (album == null) {
                LaunchedEffect(Unit) { onBack() }
                return
            }
            AlbumDetailContent(
                album = album,
                onBack = onBack,
                onPlay = { index ->
                    viewModel.play(album.tracks, index)
                    onPlayStarted()
                },
                onShuffle = {
                    viewModel.shuffle(album.tracks)
                    onPlayStarted()
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun AlbumDetailContent(
    album: Album,
    onBack: () -> Unit,
    onPlay: (index: Int) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        DetailTopBar(title = album.title ?: stringResource(R.string.unknown_album), onBack = onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header", contentType = "header") { AlbumHeader(album) }
            item(key = "actions", contentType = "actions") {
                PlayShuffleButtons(onPlayAll = { onPlay(0) }, onShuffle = onShuffle)
            }
            itemsIndexed(album.tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                // Disc-aware: a "Disc N" label where a new disc starts, only for multi-disc albums.
                val previousDisc = album.tracks.getOrNull(index - 1)?.discNumber
                if (album.hasMultipleDiscs && (index == 0 || track.discNumber != previousDisc)) {
                    Text(
                        text = stringResource(R.string.disc_number, track.discNumber ?: 1),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                AlbumTrackRow(
                    track = track,
                    showNumber = album.hasTrackNumbers,
                    showArtist = album.isVarious || (track.artist != null && track.artist != album.artist),
                    onClick = { onPlay(index) },
                )
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: Album) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtworkImage(track = album.coverTrack, size = 128.dp)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = album.title ?: stringResource(R.string.unknown_album),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = albumArtistLabel(album),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text =
                    listOfNotNull(
                        album.year?.toString(),
                        pluralStringResource(R.plurals.song_count, album.tracks.size, album.tracks.size),
                        formatDuration(album.durationMs),
                    ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Leading slot: the track number for a properly tagged album (every row would show the same
 * cover). When the album has no track numbers at all, typically untagged files MediaStore
 * grouped by folder, each row shows its own cover instead, since those usually differ.
 */
@Composable
private fun AlbumTrackRow(
    track: Track,
    showNumber: Boolean,
    showArtist: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNumber) {
            Text(
                text = track.trackNumber?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(28.dp),
            )
        } else {
            ArtworkImage(track = track, size = 40.dp)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showArtist) {
                Text(
                    text = track.artist ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
