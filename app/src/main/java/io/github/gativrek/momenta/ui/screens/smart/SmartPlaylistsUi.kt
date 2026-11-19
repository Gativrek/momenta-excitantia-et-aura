package io.github.gativrek.momenta.ui.screens.smart

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gativrek.momenta.App
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.LibraryRepository
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.listening.ListeningDao
import io.github.gativrek.momenta.data.smart.SmartKind
import io.github.gativrek.momenta.data.smart.SmartPlaylist
import io.github.gativrek.momenta.data.smart.SmartPlaylists
import io.github.gativrek.momenta.playback.PlayerConnection
import io.github.gativrek.momenta.ui.components.ArtworkImage
import io.github.gativrek.momenta.ui.components.DetailTopBar
import io.github.gativrek.momenta.ui.components.Loadable
import io.github.gativrek.momenta.ui.components.LoadingBox
import io.github.gativrek.momenta.ui.components.PlayShuffleButtons
import io.github.gativrek.momenta.ui.components.QueueActions
import io.github.gativrek.momenta.ui.components.TrackRow
import io.github.gativrek.momenta.ui.screens.playlists.AddToPlaylistDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** The smart playlists, worked out from the library (folders left out stay out) and the log. */
class SmartPlaylistsViewModel(
    library: LibraryRepository,
    listening: ListeningDao,
    private val player: PlayerConnection,
) : ViewModel() {
    val lists: StateFlow<Loadable<List<SmartPlaylist>>> =
        combine(library.tracks, listening.observePlays()) { tracks, plays ->
            Loadable.Ready(SmartPlaylists.build(tracks, plays, System.currentTimeMillis()))
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun play(
        tracks: List<Track>,
        index: Int,
    ) = player.playTracks(tracks, index)

    fun shuffle(tracks: List<Track>) = player.shuffleTracks(tracks)

    val queueActions =
        QueueActions(
            playNext = { player.playNext(listOf(it)) },
            addToQueue = { player.addToQueue(listOf(it)) },
        )

    companion object {
        /** Route argument naming the smart playlist a detail screen shows. */
        const val KIND = "smartKind"

        val Factory =
            viewModelFactory {
                initializer {
                    val c = (this[APPLICATION_KEY] as App).container
                    SmartPlaylistsViewModel(c.libraryRepository, c.database.listeningDao(), c.playerConnection)
                }
            }
    }
}

@get:StringRes
private val SmartKind.title: Int
    get() =
        when (this) {
            SmartKind.ON_REPEAT -> R.string.smart_on_repeat
            SmartKind.MOST_PLAYED -> R.string.smart_most_played
            SmartKind.RECENTLY_ADDED -> R.string.smart_recently_added
            SmartKind.FORGOTTEN -> R.string.smart_forgotten
            SmartKind.NEVER_PLAYED -> R.string.smart_never_played
        }

/** Only for the lists whose name can't say what's in them (the time windows); the rest are self-explanatory. */
@get:StringRes
private val SmartKind.description: Int?
    get() =
        when (this) {
            SmartKind.ON_REPEAT -> R.string.smart_on_repeat_body
            SmartKind.FORGOTTEN -> R.string.smart_forgotten_body
            SmartKind.MOST_PLAYED, SmartKind.RECENTLY_ADDED, SmartKind.NEVER_PLAYED -> null
        }

@get:DrawableRes
private val SmartKind.icon: Int
    get() =
        when (this) {
            SmartKind.ON_REPEAT -> R.drawable.ic_repeat
            SmartKind.MOST_PLAYED -> R.drawable.ic_aura
            SmartKind.RECENTLY_ADDED -> R.drawable.ic_plus
            SmartKind.FORGOTTEN -> R.drawable.ic_sleep_timer
            SmartKind.NEVER_PLAYED -> R.drawable.ic_play
        }

/** The smart playlists as rows, for the top of the Playlists tab. Nothing until they're worked out. */
@Composable
fun SmartPlaylistRows(
    onOpen: (SmartKind) -> Unit,
    viewModel: SmartPlaylistsViewModel = viewModel(factory = SmartPlaylistsViewModel.Factory),
) {
    val state by viewModel.lists.collectAsStateWithLifecycle()
    val lists = (state as? Loadable.Ready)?.value ?: return
    Column {
        SectionLabel(R.string.smart_playlists)
        lists.forEach { list -> SmartRow(list, onClick = { onOpen(list.kind) }) }
        SectionLabel(R.string.playlists_yours)
    }
}

@Composable
private fun SectionLabel(
    @StringRes text: Int,
) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SmartRow(
    list: SmartPlaylist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = list.tracks.firstOrNull()
        if (cover != null) {
            ArtworkImage(track = cover, size = 56.dp)
        } else {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(list.kind.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(list.kind.title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text =
                    if (list.tracks.isEmpty()) {
                        stringResource(R.string.smart_nothing_yet)
                    } else {
                        pluralStringResource(R.plurals.song_count, list.tracks.size, list.tracks.size)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One smart playlist: what it is, Play / Shuffle, its songs, and "Save as playlist". */
@Composable
fun SmartPlaylistScreen(
    kind: SmartKind,
    onBack: () -> Unit,
    onPlayStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmartPlaylistsViewModel = viewModel(factory = SmartPlaylistsViewModel.Factory),
) {
    val state by viewModel.lists.collectAsStateWithLifecycle()
    var saving by rememberSaveable { mutableStateOf(false) }
    val tracks = (state as? Loadable.Ready)?.value?.firstOrNull { it.kind == kind }?.tracks
    Column(modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(kind.title),
            onBack = onBack,
            actions = {
                if (!tracks.isNullOrEmpty()) {
                    IconButton(onClick = { saving = true }) {
                        Icon(
                            painterResource(R.drawable.ic_playlist_add),
                            contentDescription = stringResource(R.string.smart_save),
                        )
                    }
                }
            },
        )
        if (tracks == null) {
            LoadingBox()
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            kind.description?.let { description ->
                item(key = "about", contentType = "about") {
                    Text(
                        text = stringResource(description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (tracks.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        text = stringResource(R.string.smart_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                item(key = "actions", contentType = "actions") {
                    PlayShuffleButtons(
                        onPlayAll = {
                            viewModel.play(tracks, 0)
                            onPlayStarted()
                        },
                        onShuffle = {
                            viewModel.shuffle(tracks)
                            onPlayStarted()
                        },
                    )
                }
                itemsIndexed(tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = {
                            viewModel.play(tracks, index)
                            onPlayStarted()
                        },
                        queueActions = viewModel.queueActions,
                    )
                }
            }
        }
    }
    if (saving && tracks != null) {
        AddToPlaylistDialog(trackIds = tracks.map { it.id }, onDismiss = { saving = false })
    }
}

/** For a missing or unknown kind in the route: go back. */
@Composable
fun UnknownSmartPlaylist(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}
