package io.github.gativrek.momenta.ui.screens.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.AudioFolder
import io.github.gativrek.momenta.data.library.LibraryFoldersStore
import io.github.gativrek.momenta.data.library.LibraryRepository

class LibraryFoldersViewModel(
    library: LibraryRepository,
    private val store: LibraryFoldersStore,
) : ViewModel() {
    val folders = library.audioFolders

    fun setIncluded(
        folder: AudioFolder,
        included: Boolean,
    ) = store.setIncluded(folder.path, included)

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val c = (this[APPLICATION_KEY] as App).container
                    LibraryFoldersViewModel(c.libraryRepository, c.libraryFolders)
                }
            }
    }
}

/**
 * "Library folders" at the top of the Folders tab: which folders' audio is in the library.
 * Says how many are left out, and opens the list of every folder with audio, each with a switch.
 */
@Composable
fun LibraryFoldersEntry(viewModel: LibraryFoldersViewModel = viewModel(factory = LibraryFoldersViewModel.Factory)) {
    val folders by viewModel.folders.collectAsStateWithLifecycle(emptyList())
    var open by rememberSaveable { mutableStateOf(false) }
    val hidden = folders.count { !it.included }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_library_access),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 20.dp)) {
            Text(stringResource(R.string.library_folders), style = MaterialTheme.typography.bodyLarge)
            Text(
                text =
                    if (hidden == 0) {
                        stringResource(R.string.library_folders_all)
                    } else {
                        pluralStringResource(R.plurals.library_folders_hidden, hidden, hidden)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.library_folders)) },
            text = {
                LazyColumn {
                    item {
                        Text(
                            stringResource(R.string.library_folders_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    items(folders, key = { it.path }) { folder ->
                        FolderSwitch(folder, onChange = { viewModel.setIncluded(folder, it) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun FolderSwitch(
    folder: AudioFolder,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!folder.included) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text =
                    folder.path
                        .trimEnd('/')
                        .substringAfterLast('/')
                        .ifEmpty { "/" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folder.path.trimEnd('/'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.song_count, folder.trackCount, folder.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = folder.included, onCheckedChange = onChange)
    }
}
