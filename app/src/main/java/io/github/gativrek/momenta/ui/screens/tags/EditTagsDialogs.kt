package io.github.gativrek.momenta.ui.screens.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.library.Album
import io.github.gativrek.momenta.data.library.Track
import io.github.gativrek.momenta.data.tags.TagEdit

/** Title, artist, album and album artist of one song, written into its file. */
@Composable
fun EditTrackTagsDialog(
    track: Track,
    onDismiss: () -> Unit,
) {
    val editor = LocalTagEditor.current
    var title by rememberSaveable { mutableStateOf(track.title) }
    var artist by rememberSaveable { mutableStateOf(track.artist.orEmpty()) }
    var album by rememberSaveable { mutableStateOf(track.album.orEmpty()) }
    var albumArtist by rememberSaveable { mutableStateOf(track.albumArtist.orEmpty()) }
    TagsDialog(
        title = stringResource(R.string.tags_edit_song),
        onSave = {
            onDismiss()
            val edit = TagEdit.forTrack(track, title, artist, album, albumArtist)
            if (!edit.isEmpty) editor.save(listOf(track to edit))
        },
        onDismiss = onDismiss,
    ) {
        TagField(R.string.tag_title, title) { title = it }
        TagField(R.string.tag_artist, artist) { artist = it }
        TagField(R.string.tag_album, album) { album = it }
        TagField(R.string.tag_album_artist, albumArtist, last = true) { albumArtist = it }
    }
}

/**
 * Album name, album artist and artist for every song of an album at once: one save, one
 * permission prompt. Fields where the songs disagree start empty, and left empty they keep
 * each song's own value.
 */
@Composable
fun EditAlbumTagsDialog(
    album: Album,
    onDismiss: () -> Unit,
) {
    val editor = LocalTagEditor.current
    val sharedArtist = TagEdit.shared(album.tracks.map { it.artist })
    val sharedAlbumArtist = TagEdit.shared(album.tracks.map { it.albumArtist })
    val mixedArtist =
        album.tracks
            .map { it.artist }
            .distinct()
            .size > 1
    val mixedAlbumArtist =
        album.tracks
            .map { it.albumArtist }
            .distinct()
            .size > 1
    var title by rememberSaveable { mutableStateOf(album.title.orEmpty()) }
    var artist by rememberSaveable { mutableStateOf(sharedArtist.orEmpty()) }
    var albumArtist by rememberSaveable { mutableStateOf(sharedAlbumArtist.orEmpty()) }
    TagsDialog(
        title = stringResource(R.string.tags_edit_album),
        onSave = {
            onDismiss()
            editor.save(
                TagEdit.forAlbum(
                    album = album,
                    title = title,
                    artist = artist.takeUnless { mixedArtist && it.isBlank() },
                    albumArtist = albumArtist.takeUnless { mixedAlbumArtist && it.isBlank() },
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        TagField(R.string.tag_album, title) { title = it }
        TagField(R.string.tag_album_artist, albumArtist, mixed = mixedAlbumArtist) { albumArtist = it }
        TagField(R.string.tag_artist_all, artist, mixed = mixedArtist, last = true) { artist = it }
    }
}

@Composable
private fun TagsDialog(
    title: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    fields: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fields()
                Text(stringResource(R.string.tags_written_to_files))
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TagField(
    label: Int,
    value: String,
    mixed: Boolean = false,
    last: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = if (mixed) ({ Text(stringResource(R.string.tags_mixed_placeholder)) }) else null,
        supportingText = if (mixed && value.isBlank()) ({ Text(stringResource(R.string.tags_mixed_hint)) }) else null,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}
