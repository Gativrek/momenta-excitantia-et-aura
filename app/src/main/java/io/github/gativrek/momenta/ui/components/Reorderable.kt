package io.github.gativrek.momenta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.gativrek.momenta.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a LazyColumn section (queue, playlist). The dragged row follows the
 * finger (translationY = [offset]); when its centre crosses a neighbour the two swap in the
 * caller's local list. Only the final move is reported, once, on release.
 */
class ReorderState<T>(
    private val keyOf: (T) -> Any,
) {
    var key by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    private var startIndex = -1

    fun isDragging(item: T): Boolean = key == keyOf(item)

    fun start(item: T) {
        key = keyOf(item)
        offset = 0f
        startIndex = -1
    }

    /** Follows the finger by [delta] px; returns [items], reordered if the row crossed a neighbour. */
    fun dragBy(
        delta: Float,
        items: List<T>,
        listState: LazyListState,
        scope: CoroutineScope,
    ): List<T> {
        val key = key ?: return items
        if (startIndex < 0) startIndex = items.indexOfFirst { keyOf(it) == key }
        offset += delta
        val layout = listState.layoutInfo
        val dragged = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return items

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

        val keys = items.mapTo(HashSet(), keyOf)
        val centre = dragged.offset + offset + dragged.size / 2f
        val target =
            layout.visibleItemsInfo.firstOrNull { info ->
                info.key != key && info.key in keys && centre >= info.offset && centre < info.offset + info.size
            } ?: return items
        val from = items.indexOfFirst { keyOf(it) == key }
        val to = items.indexOfFirst { keyOf(it) == target.key }
        if (from < 0 || to < 0) return items
        // The row will be laid out where the target was; shift the offset so it doesn't jump.
        offset += dragged.offset - target.offset
        // LazyColumn keeps the first visible item anchored; if that's one of the swapped
        // rows, pin the scroll position so the list doesn't jump with it.
        if (dragged.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            val index = listState.firstVisibleItemIndex
            val scrollOffset = listState.firstVisibleItemScrollOffset
            scope.launch { listState.scrollToItem(index, scrollOffset) }
        }
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Ends the drag; returns (from, to) indices in the list if the row moved. */
    fun finish(items: List<T>): Pair<Int, Int>? {
        val key = key
        this.key = null
        offset = 0f
        if (key == null || startIndex < 0) return null
        val end = items.indexOfFirst { keyOf(it) == key }
        return (startIndex to end).takeIf { end >= 0 && it.first != it.second }
    }

    private companion object {
        const val MAX_SCROLL = 40f
    }
}

/** Lifts the dragged row above its neighbours and moves it with the finger. */
fun Modifier.reorderDragged(state: ReorderState<*>): Modifier = zIndex(1f).graphicsLayer { translationY = state.offset }

/**
 * The gesture for a row's drag handle. [items] / [onItemsChange] are the caller's local
 * list; [onMoved] gets the final (from, to) once the finger lifts.
 */
fun <T> reorderGestures(
    state: ReorderState<T>,
    item: T,
    items: () -> List<T>,
    onItemsChange: (List<T>) -> Unit,
    listState: LazyListState,
    scope: CoroutineScope,
    onMoved: (from: Int, to: Int) -> Unit,
): suspend PointerInputScope.() -> Unit =
    {
        detectDragGestures(
            onDragStart = { state.start(item) },
            onDrag = { change, amount ->
                change.consume()
                onItemsChange(state.dragBy(amount.y, items(), listState, scope))
            },
            onDragEnd = { state.finish(items())?.let { (from, to) -> onMoved(from, to) } },
            onDragCancel = { state.finish(items()) },
        )
    }

/** The handle icon, wired to [gestures]. */
@Composable
fun DragHandle(gestures: suspend PointerInputScope.() -> Unit) {
    Box(modifier = Modifier.pointerInput(Unit, gestures).padding(12.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_drag_handle),
            contentDescription = stringResource(R.string.action_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Swipe either way to remove; the row vanishes at once and [onRemove] runs once.
 *
 * Rows are see-through at rest (the theme's backdrop shows behind them), so the red
 * "Removed" layer and a solid row color only appear while a swipe is under way.
 */
@Composable
fun SwipeToRemove(
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
    val swiping = state.dismissDirection != SwipeToDismissBoxValue.Settled
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            if (!swiping) return@SwipeToDismissBox
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.action_removed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Box(
            modifier =
                Modifier.background(
                    if (swiping) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                ),
        ) { content() }
    }
}
