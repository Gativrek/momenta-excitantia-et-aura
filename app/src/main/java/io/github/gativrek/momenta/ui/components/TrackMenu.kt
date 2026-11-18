package io.github.gativrek.momenta.ui.components

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.ui.screens.playlists.AddToPlaylistDialog

/** The per-track queue actions every track list offers. */
class QueueActions(
    val playNext: (Track) -> Unit,
    val addToQueue: (Track) -> Unit,
)

/** Three-dot button with "Play next", "Add to queue" and "Add to playlist". */
@Composable
fun TrackMenuButton(
    track: Track,
    actions: QueueActions,
) {
    var open by remember { mutableStateOf(false) }
    var addingToPlaylist by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.action_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(R.string.action_play_next, R.drawable.ic_play_next) {
                open = false
                actions.playNext(track)
                Toast.makeText(context, R.string.toast_play_next, Toast.LENGTH_SHORT).show()
            }
            MenuItem(R.string.action_add_to_queue, R.drawable.ic_add_to_queue) {
                open = false
                actions.addToQueue(track)
                Toast.makeText(context, R.string.toast_added_to_queue, Toast.LENGTH_SHORT).show()
            }
            MenuItem(R.string.action_add_to_playlist, R.drawable.ic_playlist_add) {
                open = false
                addingToPlaylist = true
            }
        }
    }
    if (addingToPlaylist) {
        AddToPlaylistDialog(trackIds = listOf(track.id), onDismiss = { addingToPlaylist = false })
    }
}

@Composable
private fun MenuItem(
    @StringRes text: Int,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
    )
}
