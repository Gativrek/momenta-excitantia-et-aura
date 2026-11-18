package io.github.gativrek.momenta.ui.screens.nowplaying

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.gativrek.momenta.playback.NowPlaying
import io.github.gativrek.momenta.ui.components.ArtworkImage
import kotlin.math.abs

/**
 * The Now Playing cover, following the queue: on a skip the old cover slides out to the left
 * as the new one comes in from the right, and the other way round going back. The cover can
 * be dragged too: right to left skips, left to right goes back a song. A drag that doesn't go
 * far or fast enough springs back; at either end of the queue the cover resists.
 *
 * [backAt] is when the user last asked to go back (uptime ms), so a song change right after it
 * slides the other way; every other change (a skip, a song ending) moves forward.
 */
@Composable
fun SwipeCover(
    nowPlaying: NowPlaying?,
    size: Dp,
    hasNext: Boolean,
    hasPrevious: Boolean,
    backAt: () -> Long,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { size.toPx() }
    val flingPx = with(density) { FLING_SPEED.toPx() }
    val gapPx = with(density) { COVER_GAP.roundToPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    val dragState =
        rememberDraggableState { delta ->
            val next = offset + delta
            // Past an end of the queue: the cover follows the finger only reluctantly.
            val blocked = (next < 0 && !hasNext) || (next > 0 && !hasPrevious)
            offset += if (blocked) delta * EDGE_RESISTANCE else delta
        }
    Box(
        modifier =
            modifier
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val far = abs(offset) > widthPx * COMMIT_FRACTION
                        when {
                            offset < 0 && hasNext && (far || velocity < -flingPx) -> onSwipeNext()
                            offset > 0 && hasPrevious && (far || velocity > flingPx) -> onSwipePrevious()
                        }
                        // Settles back to the middle while the song change slides the covers.
                        animate(offset, 0f, animationSpec = spring(stiffness = SETTLE_STIFFNESS)) { value, _ ->
                            offset = value
                        }
                    },
                ).graphicsLayer {
                    translationX = offset
                    rotationZ = offset / widthPx * MAX_TILT_DEGREES
                },
    ) {
        AnimatedContent(
            targetState = nowPlaying,
            contentKey = { it?.trackId },
            transitionSpec = {
                val sign = if (SystemClock.uptimeMillis() - backAt() < BACK_WINDOW_MS) -1 else 1
                val slide = tween<IntOffset>(SLIDE_MS)
                (slideInHorizontally(slide) { sign * (it + gapPx) } + fadeIn(tween(SLIDE_MS)))
                    .togetherWith(slideOutHorizontally(slide) { -sign * (it + gapPx) } + fadeOut(tween(SLIDE_MS)))
                    .using(SizeTransform(clip = false))
            },
            label = "cover",
        ) { playing -> ArtworkImage(artwork = playing?.artwork, size = size) }
    }
}

/** A drag past this share of the cover's width changes the song on release. */
private const val COMMIT_FRACTION = 0.3f

/** ...as does a flick at least this fast. */
private val FLING_SPEED = 900.dp

/** Space between the leaving cover and the arriving one. */
private val COVER_GAP = 24.dp

/** How much a drag past the end of the queue moves the cover. */
private const val EDGE_RESISTANCE = 0.25f

/** The cover tilts a little as it's dragged, up to this at a full width. */
private const val MAX_TILT_DEGREES = 6f

private const val SLIDE_MS = 380
private const val SETTLE_STIFFNESS = 400f

/** A song change this soon after asking to go back slides the covers backwards. */
private const val BACK_WINDOW_MS = 2_000L
