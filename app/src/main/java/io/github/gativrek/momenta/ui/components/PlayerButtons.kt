package io.github.gativrek.momenta.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.gativrek.momenta.R

/** Play/pause showing the action it will take: the pause icon while playback is wanted. */
@Composable
fun PlayPauseButton(
    playWhenReady: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    iconSize: Dp = 24.dp,
) {
    val icon = if (playWhenReady) R.drawable.ic_pause else R.drawable.ic_play
    val label = stringResource(if (playWhenReady) R.string.action_pause else R.string.action_play)
    if (filled) {
        FilledIconButton(onClick = onClick, modifier = modifier) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(iconSize))
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun SkipButton(
    next: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(
            painter = painterResource(if (next) R.drawable.ic_skip_next else R.drawable.ic_skip_previous),
            contentDescription = stringResource(if (next) R.string.action_next else R.string.action_previous),
            modifier = Modifier.size(iconSize),
        )
    }
}
