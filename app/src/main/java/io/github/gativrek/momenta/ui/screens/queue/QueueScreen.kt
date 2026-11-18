package io.github.gativrek.momenta.ui.screens.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.playback.QueueEntry
import io.github.gativrek.momenta.playback.QueueState
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.components.DetailTopBar
import io.github.gativrek.momenta.ui.components.DragHandle
import io.github.gativrek.momenta.ui.components.ReorderState
import io.github.gativrek.momenta.ui.components.SwipeToRemove
import io.github.gativrek.momenta.ui.components.reorderDragged
import io.github.gativrek.momenta.ui.components.reorderGestures
import io.github.gativrek.momenta.ui.format.formatDuration
import io.github.gativrek.momenta.ui.screens.nowplaying.PlayerViewModel
import io.github.gativrek.momenta.ui.screens.playlists.AddToPlaylistDialog

@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var saving by rememberSaveable { mutableStateOf(false) }
    // Queue gone (cleared, or service stopped): nothing to show.
    LaunchedEffect(state.connected, queue.current) {
        if (state.connected && queue.current == null) onBack()
    }
    QueueContent(
        queue = queue,
        onBack = onBack,
        onJump = viewModel::jumpTo,
        onRemove = viewModel::remove,
        onMove = viewModel::move,
        onSaveAsPlaylist = { saving = true },
        modifier = modifier,
    )
    if (saving) {
        // Current track, then everything after it, in play order.
        val entries = listOfNotNull(queue.current) + queue.upcoming
        AddToPlaylistDialog(trackIds = entries.mapNotNull { it.nowPlaying.trackId }, onDismiss = { saving = false })
    }
}

/** An upcoming entry with a key that survives reordering (the same track may be queued twice). */
private data class Row(
    val key: String,
    val entry: QueueEntry,
)

private fun rowsOf(entries: List<QueueEntry>): List<Row> {
    val seen = HashMap<Long?, Int>()
    return entries.map { entry ->
        val id = entry.nowPlaying.trackId
        val occurrence = seen.merge(id, 1, Int::plus)
        Row(key = "row:$id:$occurrence", entry = entry)
    }
}

@Composable
fun QueueContent(
    queue: QueueState,
    onBack: () -> Unit,
    onJump: (itemIndex: Int) -> Unit,
    onRemove: (itemIndex: Int) -> Unit,
    onMove: (fromPlayPosition: Int, toPlayPosition: Int) -> Unit,
    onSaveAsPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reorder = remember { ReorderState<Row> { it.key } }

    // A local copy, so drags and swipes show instantly. It's replaced whenever the player
    // reports a new queue, except mid-drag.
    var rows by remember { mutableStateOf(rowsOf(queue.upcoming)) }
    LaunchedEffect(queue.upcoming) {
        if (reorder.key == null) rows = rowsOf(queue.upcoming)
    }
    // The drag gesture is created once per row; read the latest queue through this.
    val latestQueue by rememberUpdatedState(queue)

    Column(modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.action_queue),
            onBack = onBack,
            actions = {
                IconButton(onClick = onSaveAsPlaylist) {
                    Icon(
                        painterResource(R.drawable.ic_playlist_add),
                        contentDescription = stringResource(R.string.action_save_queue),
                    )
                }
            },
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "now-title", contentType = "section") {
                SectionTitle(stringResource(R.string.queue_now_playing))
            }
            queue.current?.let { current ->
                item(key = "current", contentType = "current") {
                    QueueRow(entry = current, highlighted = true, onClick = { onJump(current.itemIndex) })
                }
            }
            item(key = "next-title", contentType = "section") { SectionTitle(stringResource(R.string.queue_up_next)) }
            if (rows.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        text = stringResource(R.string.queue_empty_upcoming),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(rows, key = { it.key }, contentType = { "row" }) { row ->
                val dragging = reorder.isDragging(row)
                SwipeToRemove(
                    onRemove = {
                        rows = rows - row
                        onRemove(row.entry.itemIndex)
                    },
                    modifier = if (dragging) Modifier.reorderDragged(reorder) else Modifier.animateItem(),
                ) {
                    QueueRow(
                        entry = row.entry,
                        highlighted = false,
                        elevated = dragging,
                        onClick = { onJump(row.entry.itemIndex) },
                        dragHandle = {
                            DragHandle(
                                reorderGestures(
                                    state = reorder,
                                    item = row,
                                    items = { rows },
                                    onItemsChange = { rows = it },
                                    listState = listState,
                                    scope = scope,
                                    // Rows are "up next": play positions start right after the current track.
                                    onMoved = { from, to ->
                                        latestQueue.current?.let { current ->
                                            val base = current.playPosition + 1
                                            onMove(base + from, base + to)
                                        }
                                    },
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
private fun QueueRow(
    entry: QueueEntry,
    highlighted: Boolean,
    onClick: () -> Unit,
    elevated: Boolean = false,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    Surface(
        color =
            when {
                highlighted -> MaterialTheme.colorScheme.secondaryContainer
                elevated -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> Color.Transparent
            },
        shadowElevation = if (elevated) 8.dp else 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, end = if (dragHandle != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(artwork = entry.nowPlaying.artwork, size = 48.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    entry.nowPlaying.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.nowPlaying.artist ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDuration(entry.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            dragHandle?.invoke()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}
