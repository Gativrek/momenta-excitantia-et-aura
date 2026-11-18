package io.github.gativrek.momenta.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.Artist
import io.github.gativrek.momenta.data.library.SearchResults
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.components.LoadingBox
import io.github.gativrek.momenta.ui.components.PlaceholderScreen
import io.github.gativrek.momenta.ui.components.TrackRow
import io.github.gativrek.momenta.ui.screens.albums.albumArtistLabel
import io.github.gativrek.momenta.ui.screens.artists.ArtistRow

/** Search across songs, albums and artists. Opened from the search button on every tab. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlayStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier.fillMaxSize()) {
        SearchBar(query = query, onQueryChange = viewModel::onQueryChange, onBack = onBack)
        val r = results
        when {
            query.isBlank() ->
                PlaceholderScreen(
                    iconRes = R.drawable.ic_search,
                    title = stringResource(R.string.search_hint_title),
                    subtitle = stringResource(R.string.search_hint_body),
                )
            r == null -> LoadingBox()
            r.isEmpty ->
                PlaceholderScreen(
                    iconRes = R.drawable.ic_search,
                    title = stringResource(R.string.search_no_results_title),
                    subtitle = stringResource(R.string.search_no_results_body, query.trim()),
                )
            else ->
                SearchResultList(
                    results = r,
                    onArtistClick = {
                        keyboard?.hide()
                        onArtistClick(it)
                    },
                    onAlbumClick = {
                        keyboard?.hide()
                        onAlbumClick(it)
                    },
                    onTrackClick = {
                        keyboard?.hide()
                        viewModel.play(it)
                        onPlayStarted()
                    },
                    viewModel = viewModel,
                )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Ready to type on open. Not again after coming back from a result, where the query
    // is already there and the keyboard would hide the results.
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onQueryChange("")
                        focus.requestFocus()
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus),
        )
    }
}

@Composable
private fun SearchResultList(
    results: SearchResults,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onTrackClick: (Track) -> Unit,
    viewModel: SearchViewModel,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        section(R.string.nav_artists, results.artists.isNotEmpty())
        items(results.artists, key = { "artist-${it.id}" }, contentType = { "artist" }) { artist ->
            ArtistRow(artist = artist, onClick = { onArtistClick(artist) })
        }
        section(R.string.nav_albums, results.albums.isNotEmpty())
        items(results.albums, key = { "album-${it.id}" }, contentType = { "album" }) { album ->
            AlbumRow(album = album, onClick = { onAlbumClick(album) })
        }
        section(R.string.nav_songs, results.tracks.isNotEmpty())
        items(results.tracks, key = { "track-${it.id}" }, contentType = { "track" }) { track ->
            TrackRow(track = track, onClick = { onTrackClick(track) }, queueActions = viewModel.queueActions)
        }
    }
}

private fun LazyListScope.section(
    titleRes: Int,
    visible: Boolean,
) {
    if (!visible) return
    item(key = "header-$titleRes", contentType = "header") {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun AlbumRow(
    album: Album,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        album.tracks.firstOrNull()?.let { ArtworkImage(track = it, size = 48.dp) }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = album.title ?: stringResource(R.string.unknown_album),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = albumArtistLabel(album),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
