package io.github.gativrek.momenta.ui.screens.playlists

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.playlists.PlaylistSummary
import io.github.gativrek.momenta.ui.components.LoadingBox

private const val MAX_NAME_LENGTH = 100

/** Asks for a playlist name: used to create and to rename. */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
) {
    // Cursor at the end, so renaming edits the current name instead of prepending to it.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    val name = field.text
    val valid = name.isNotBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { if (it.text.length <= MAX_NAME_LENGTH) field = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name.trim()) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun DeletePlaylistDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_delete_title)) },
        text = { Text(stringResource(R.string.playlist_delete_body, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.playlist_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * "Add to playlist": pick a playlist (or make a new one) for the tracks with [trackIds].
 * Shows a short confirmation once they're added.
 */
@Composable
fun AddToPlaylistDialog(
    trackIds: List<Long>,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = viewModel(factory = PlaylistViewModels.AddTo),
) {
    val playlists by viewModel.playlistsState.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    var creating by rememberSaveable { mutableStateOf(false) }
    val confirm: (String) -> () -> Unit = { name ->
        {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.toast_added_to_playlist, name),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = { name ->
                viewModel.createAndAdd(name, trackIds, confirm(name))
                onDismiss()
            },
            onDismiss = { creating = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_to_playlist)) },
        text = {
            val list = playlists
            if (list == null) {
                Box(Modifier.heightIn(min = 96.dp)) { LoadingBox() }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    item(key = "new") {
                        PickerRow(iconRes = R.drawable.ic_plus, text = stringResource(R.string.playlist_new)) {
                            creating = true
                        }
                    }
                    items(list, key = { it.id }) { playlist ->
                        PickerRow(
                            iconRes = R.drawable.ic_nav_playlists,
                            text = playlist.name,
                            subtitle = songCount(playlist),
                        ) {
                            viewModel.add(playlist.id, trackIds, confirm(playlist.name))
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun songCount(playlist: PlaylistSummary) =
    pluralStringResource(R.plurals.song_count, playlist.entryCount, playlist.entryCount)

@Composable
private fun PickerRow(
    iconRes: Int,
    text: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
