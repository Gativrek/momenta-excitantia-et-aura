package io.github.gativrek.momenta.ui.screens.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.playback.QueueEntry
import io.github.gativrek.momenta.playback.QueueState
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.components.DetailTopBar
import io.github.gativrek.momenta.ui.format.formatDuration
import io.github.gativrek.momenta.ui.screens.nowplaying.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        modifier = modifier,
    )
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
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // A local copy, so drags and swipes show instantly. It's replaced whenever the player
    // reports a new queue, except mid-drag.
    var rows by remember { mutableStateOf(rowsOf(queue.upcoming)) }
    val drag = remember { DragState() }
    // The gesture lambdas below are created once per row; read the latest queue through this.
    val latestQueue by rememberUpdatedState(queue)
    LaunchedEffect(queue.upcoming) {
        if (drag.key == null) rows = rowsOf(queue.upcoming)
    }

    Column(modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.action_queue), onBack = onBack)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(
                key = "now-title",
                contentType = "section",
            ) { SectionTitle(stringResource(R.string.queue_now_playing)) }
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
                val dragging = drag.key == row.key
                val itemModifier =
                    if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = drag.offset }
                    } else {
                        Modifier.animateItem()
                    }
                SwipeToRemove(
                    onRemove = {
                        rows = rows - row
                        onRemove(row.entry.itemIndex)
                    },
                    modifier = itemModifier,
                ) {
                    QueueRow(
                        entry = row.entry,
                        highlighted = false,
                        elevated = dragging,
                        onClick = { onJump(row.entry.itemIndex) },
                        dragGestures = {
                            detectDragGestures(
                                onDragStart = { drag.start(row.key) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    rows = drag.dragBy(amount.y, rows, listState, scope)
                                },
                                onDragEnd = {
                                    drag.finish(rows, latestQueue)?.let { (from, to) -> onMove(from, to) }
                                },
                                onDragCancel = { drag.finish(rows, latestQueue) },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Drag-to-reorder for the upcoming rows. The dragged row follows the finger
 * (translationY = [offset]); when its centre crosses a neighbour, the two swap in the local
 * list. Only the final move is sent to the player, as one command, on release.
 */
private class DragState {
    var key by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    private var startPlayPosition = -1

    fun start(key: String) {
        this.key = key
        offset = 0f
        startPlayPosition = -1
    }

    fun dragBy(
        delta: Float,
        rows: List<Row>,
        listState: LazyListState,
        scope: CoroutineScope,
    ): List<Row> {
        val key = key ?: return rows
        if (startPlayPosition < 0) startPlayPosition = rows.first { it.key == key }.entry.playPosition
        offset += delta
        val layout = listState.layoutInfo
        val dragged = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return rows

        // Near the top or bottom edge: scroll the list, and keep the row under the finger.
        val top = dragged.offset + offset
        val bottom = top + dragged.size
        val edge = dragged.size / 2f
        val overflow =
            when {
                bottom > layout.viewportEndOffset - edge -> bottom - (layout.viewportEndOffset - edge)
                top < layout.viewportStartOffset + edge -> top - (layout.viewportStartOffset + edge)
                else -> 0f
            }
        if (overflow != 0f) {
            scope.launch { offset += listState.scrollBy(overflow.coerceIn(-MAX_SCROLL, MAX_SCROLL)) }
        }

        val centre = dragged.offset + offset + dragged.size / 2f
        val target =
            layout.visibleItemsInfo.firstOrNull { info ->
                info.key != key &&
                    (info.key as? String)?.startsWith("row:") == true &&
                    centre >= info.offset &&
                    centre < info.offset + info.size
            } ?: return rows
        val from = rows.indexOfFirst { it.key == key }
        val to = rows.indexOfFirst { it.key == target.key }
        if (from < 0 || to < 0) return rows
        // The row will be laid out where the target was; shift the offset so it doesn't jump.
        offset += dragged.offset - target.offset
        // LazyColumn keeps the first visible item anchored; if that's one of the swapped
        // rows, pin the scroll position so the list doesn't jump with it.
        if (dragged.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            val index = listState.firstVisibleItemIndex
            val scrollOffset = listState.firstVisibleItemScrollOffset
            scope.launch { listState.scrollToItem(index, scrollOffset) }
        }
        return rows.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Ends the drag; returns (from, to) play positions if the row moved. */
    fun finish(
        rows: List<Row>,
        queue: QueueState,
    ): Pair<Int, Int>? {
        val key = key
        this.key = null
        offset = 0f
        val current = queue.current ?: return null
        if (key == null || startPlayPosition < 0) return null
        val to = current.playPosition + 1 + rows.indexOfFirst { it.key == key }
        return (startPlayPosition to to).takeIf { it.first != it.second }
    }

    private companion object {
        const val MAX_SCROLL = 40f
    }
}

@Composable
private fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var removed by remember { mutableStateOf(false) }
    // The confirm callback is remembered by the swipe state; always call the latest onRemove.
    val latestOnRemove by rememberUpdatedState(onRemove)
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled && !removed) {
                    removed = true
                    latestOnRemove()
                    true
                } else {
                    false
                }
            },
        )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.queue_removed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { content() }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    highlighted: Boolean,
    onClick: () -> Unit,
    elevated: Boolean = false,
    dragGestures: (suspend PointerInputScope.() -> Unit)? = null,
) {
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shadowElevation = if (elevated) 8.dp else 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, end = if (dragGestures != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
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
            if (dragGestures != null) {
                Box(modifier = Modifier.pointerInput(Unit, dragGestures).padding(12.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_drag_handle),
                        contentDescription = stringResource(R.string.action_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
