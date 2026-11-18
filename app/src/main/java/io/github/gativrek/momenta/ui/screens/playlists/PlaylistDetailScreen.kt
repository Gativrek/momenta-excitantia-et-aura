package io.github.gativrek.momenta.ui.screens.playlists

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.playlists.PlaylistDetail
import io.github.gativrek.momenta.data.playlists.PlaylistItem
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.components.DetailTopBar
import io.github.gativrek.momenta.ui.components.DragHandle
import io.github.gativrek.momenta.ui.components.Loadable
import io.github.gativrek.momenta.ui.components.LoadingBox
import io.github.gativrek.momenta.ui.components.PlayShuffleButtons
import io.github.gativrek.momenta.ui.components.ReorderState
import io.github.gativrek.momenta.ui.components.SwipeToRemove
import io.github.gativrek.momenta.ui.components.reorderDragged
import io.github.gativrek.momenta.ui.components.reorderGestures
import io.github.gativrek.momenta.ui.format.formatDuration

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onPlayStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = viewModel(factory = PlaylistViewModels.Detail),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    when (val s = state) {
        Loadable.Loading -> LoadingBox(modifier)
        is Loadable.Ready -> {
            val playlist = s.value
            // Deleted (here or elsewhere): nothing to show.
            if (playlist == null) {
                LaunchedEffect(Unit) { onBack() }
                return
            }
            PlaylistDetailContent(
                playlist = playlist,
                onBack = onBack,
                onPlay = { item ->
                    viewModel.play(item)
                    onPlayStarted()
                },
                onShuffle = {
                    viewModel.shuffle()
                    onPlayStarted()
                },
                onRename = { renaming = true },
                onDelete = { deleting = true },
                onMove = viewModel::move,
                onRemove = viewModel::remove,
                modifier = modifier,
            )
            if (renaming) {
                PlaylistNameDialog(
                    title = stringResource(R.string.playlist_rename_title),
                    confirmLabel = stringResource(R.string.playlist_save),
                    initialName = playlist.name,
                    onConfirm = { name ->
                        renaming = false
                        viewModel.rename(name)
                    },
                    onDismiss = { renaming = false },
                )
            }
            if (deleting) {
                DeletePlaylistDialog(
                    name = playlist.name,
                    onConfirm = {
                        deleting = false
                        viewModel.delete()
                    },
                    onDismiss = { deleting = false },
                )
            }
        }
    }
}

@Composable
fun PlaylistDetailContent(
    playlist: PlaylistDetail,
    onBack: () -> Unit,
    onPlay: (PlaylistItem?) -> Unit,
    onShuffle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (entryId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reorder = remember { ReorderState<PlaylistItem> { it.entryId } }
    // Local copy so drags and swipes show at once; replaced by every database update except mid-drag.
    var items by remember { mutableStateOf(playlist.items) }
    LaunchedEffect(playlist.items) {
        if (reorder.key == null) items = playlist.items
    }

    Column(modifier.fillMaxSize()) {
        DetailTopBar(title = playlist.name, onBack = onBack, actions = { PlaylistMenu(onRename, onDelete) })
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "summary", contentType = "summary") { Summary(playlist) }
            if (playlist.playable.isNotEmpty()) {
                item(key = "actions", contentType = "actions") {
                    PlayShuffleButtons(onPlayAll = { onPlay(null) }, onShuffle = onShuffle)
                }
            }
            if (items.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        text = stringResource(R.string.playlist_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(items, key = { it.entryId }, contentType = { "entry" }) { item ->
                val dragging = reorder.isDragging(item)
                SwipeToRemove(
                    onRemove = {
                        items = items - item
                        onRemove(item.entryId)
                    },
                    modifier = if (dragging) Modifier.reorderDragged(reorder) else Modifier.animateItem(),
                ) {
                    EntryRow(
                        item = item,
                        elevated = dragging,
                        onClick = { if (item.track != null) onPlay(item) },
                        dragHandle = {
                            DragHandle(
                                reorderGestures(
                                    state = reorder,
                                    item = item,
                                    items = { items },
                                    onItemsChange = { items = it },
                                    listState = listState,
                                    scope = scope,
                                    onMoved = onMove,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Summary(playlist: PlaylistDetail) {
    val missing = playlist.items.count { it.track == null }
    val parts =
        listOf(
            pluralStringResource(R.plurals.song_count, playlist.items.size, playlist.items.size),
            formatDuration(playlist.durationMs),
        )
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (missing > 0) {
            Text(
                text = pluralStringResource(R.plurals.playlist_missing_count, missing, missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PlaylistMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_rename)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                onClick = {
                    open = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_delete)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

/** A playlist entry. Missing ones stay in place, dimmed, with a "Missing" label instead of a cover. */
@Composable
private fun EntryRow(
    item: PlaylistItem,
    elevated: Boolean,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val track = item.track
    Surface(shadowElevation = if (elevated) 8.dp else 0.dp) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = track != null, onClick = onClick)
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (track != null) {
                ArtworkImage(track = track, size = 48.dp)
            } else {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_missing),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .alpha(if (track != null) 1f else 0.6f),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        if (track != null) {
                            item.artist ?: stringResource(R.string.unknown_artist)
                        } else {
                            stringResource(R.string.playlist_missing)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (track !=
                            null
                        ) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDuration(item.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            dragHandle()
        }
    }
}
