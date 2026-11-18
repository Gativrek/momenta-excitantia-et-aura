package io.github.gativrek.momenta.ui.screens.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.FolderContents
import io.github.gativrek.momenta.data.library.FolderEntry
import io.github.gativrek.momenta.ui.components.DetailTopBar
import io.github.gativrek.momenta.ui.components.Loadable
import io.github.gativrek.momenta.ui.components.LoadingBox
import io.github.gativrek.momenta.ui.components.PlaceholderScreen
import io.github.gativrek.momenta.ui.components.PlayShuffleButtons
import io.github.gativrek.momenta.ui.components.QueueActions
import io.github.gativrek.momenta.ui.components.TrackRow
import io.github.gativrek.momenta.ui.screens.FolderViewModel
import io.github.gativrek.momenta.ui.screens.LibraryViewModels

/**
 * One level of the folder tree. The same screen serves the root ("") and every subfolder;
 * opening a subfolder pushes another instance, so system Back walks back up the tree.
 */
@Composable
fun FoldersScreen(
    isRoot: Boolean,
    onFolderClick: (FolderEntry) -> Unit,
    onBack: () -> Unit,
    onPlayStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel = viewModel(factory = LibraryViewModels.Folder),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        Loadable.Loading -> LoadingBox(modifier)
        is Loadable.Ready ->
            if (isRoot && s.value.allTracks.isEmpty()) {
                PlaceholderScreen(
                    iconRes = R.drawable.ic_nav_folders,
                    title = stringResource(R.string.songs_empty_title),
                    subtitle = stringResource(R.string.songs_empty_body),
                    modifier = modifier,
                )
            } else {
                FolderContent(
                    contents = s.value,
                    isRoot = isRoot,
                    onFolderClick = onFolderClick,
                    onBack = onBack,
                    onPlay = { index ->
                        viewModel.play(s.value.tracks, index)
                        onPlayStarted()
                    },
                    onPlayAll = {
                        viewModel.play(s.value.allTracks)
                        onPlayStarted()
                    },
                    onShuffle = {
                        viewModel.shuffle(s.value.allTracks)
                        onPlayStarted()
                    },
                    queueActions = viewModel.queueActions,
                    modifier = modifier,
                )
            }
    }
}

@Composable
private fun FolderContent(
    contents: FolderContents,
    isRoot: Boolean,
    onFolderClick: (FolderEntry) -> Unit,
    onBack: () -> Unit,
    onPlay: (index: Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    queueActions: QueueActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (!isRoot) {
            DetailTopBar(title = contents.path.trimEnd('/').substringAfterLast('/'), onBack = onBack)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isRoot) item(key = "library_folders", contentType = "library_folders") { LibraryFoldersEntry() }
            item(key = "actions", contentType = "actions") {
                // Plays everything below this folder, subfolders included.
                PlayShuffleButtons(onPlayAll = onPlayAll, onShuffle = onShuffle)
            }
            items(contents.subfolders, key = { "folder:" + it.path }, contentType = { "folder" }) { folder ->
                FolderRow(folder = folder, onClick = { onFolderClick(folder) })
            }
            itemsIndexed(contents.tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                TrackRow(track = track, onClick = { onPlay(index) }, queueActions = queueActions)
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: FolderEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_folders),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.song_count, folder.trackCount, folder.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
