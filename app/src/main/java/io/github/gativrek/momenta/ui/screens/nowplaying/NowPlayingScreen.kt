package io.github.gativrek.momenta.ui.screens.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.playback.PlayerUiState
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.components.PlayPauseButton
import io.github.gativrek.momenta.ui.components.SkipButton
import io.github.gativrek.momenta.ui.format.formatDuration

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle(initialValue = 0L)

    // Nothing left to show (queue cleared, or service gone): close instead of an empty screen.
    LaunchedEffect(state.connected, state.nowPlaying) {
        if (state.connected && state.nowPlaying == null) onCollapse()
    }

    NowPlayingContent(
        state = state,
        positionMs = position,
        onCollapse = onCollapse,
        onTogglePlayPause = viewModel::togglePlayPause,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onSeek = viewModel::seekTo,
        modifier = modifier,
    )
}

@Composable
fun NowPlayingContent(
    state: PlayerUiState,
    positionMs: Long,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowPlaying = state.nowPlaying
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            // No systemBarsPadding here: the root Scaffold already pads its content for the bars.
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        painter = painterResource(R.drawable.ic_collapse),
                        contentDescription = stringResource(R.string.action_collapse),
                    )
                }
            }

            // Square art filling the width. BoxWithConstraints gives the real width, so the
            // thumbnail is requested at display size instead of a guess.
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                ArtworkImage(artwork = nowPlaying?.artwork, size = maxWidth)
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = nowPlaying?.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text =
                    stringResource(
                        R.string.track_subtitle,
                        nowPlaying?.artist ?: stringResource(R.string.unknown_artist),
                        nowPlaying?.album ?: stringResource(R.string.unknown_album),
                    ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            SeekBar(
                positionMs = positionMs,
                durationMs = state.durationMs,
                onSeek = onSeek,
                modifier = Modifier.padding(top = 24.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipButton(
                    next = false,
                    enabled = state.hasPrevious,
                    onClick = onPrevious,
                    modifier = Modifier.size(64.dp),
                    iconSize = 32.dp,
                )
                PlayPauseButton(
                    playWhenReady = state.playWhenReady,
                    onClick = onTogglePlayPause,
                    filled = true,
                    iconSize = 36.dp,
                    modifier = Modifier.size(80.dp),
                )
                SkipButton(
                    next = true,
                    enabled = state.hasNext,
                    onClick = onNext,
                    modifier = Modifier.size(64.dp),
                    iconSize = 32.dp,
                )
            }
        }
    }
}

/**
 * Slider over the track. While the user drags, it shows the drag position and the player
 * keeps playing; the seek is sent once, on release, instead of on every pixel.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1)
    val fraction = dragFraction ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * duration).toLong()) }
                dragFraction = null
            },
            enabled = durationMs > 0,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeLabel((fraction * duration).toLong())
            TimeLabel(durationMs)
        }
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(
        text = formatDuration(ms),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
