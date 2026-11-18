package io.github.gativrek.momenta.ui.screens.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.notes.LinerNotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LinerNotesViewModel(
    val repository: LinerNotesRepository,
) : ViewModel() {
    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    LinerNotesViewModel((this[APPLICATION_KEY] as App).container.linerNotes)
                }
            }
    }
}

/** Write or change [track]'s liner note. Opens once the current note has loaded. */
@Composable
fun SongNoteDialog(
    track: Track,
    onDismiss: () -> Unit,
    viewModel: LinerNotesViewModel = viewModel(factory = LinerNotesViewModel.Factory),
) {
    var initial by remember(track.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(track.id) {
        initial =
            viewModel.repository
                .notesFor(track)
                .first()
                .song
                .orEmpty()
    }
    val scope = rememberCoroutineScope()
    initial?.let { text ->
        NoteEditor(
            title = track.title,
            initial = text,
            onSave = { scope.launch { viewModel.repository.saveSong(track, it) } },
            onDismiss = onDismiss,
        )
    }
}

/** The album's liner note under its header, with a way to write or change it. */
@Composable
fun AlbumNoteSection(
    album: Album,
    viewModel: LinerNotesViewModel = viewModel(factory = LinerNotesViewModel.Factory),
) {
    val note by remember(album) { viewModel.repository.albumNote(album) }.collectAsStateWithLifecycle(null)
    var editing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val text = note
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { editing = true }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    } else {
        Row(
            modifier =
                Modifier
                    .clickable { editing = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.note_add),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    if (editing) {
        NoteEditor(
            title = album.title ?: stringResource(R.string.unknown_album),
            initial = text.orEmpty(),
            onSave = { scope.launch { viewModel.repository.saveAlbum(album, it) } },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun NoteEditor(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.note_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_NOTE_LENGTH) },
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(text)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.note_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val MAX_NOTE_LENGTH = 1_000
