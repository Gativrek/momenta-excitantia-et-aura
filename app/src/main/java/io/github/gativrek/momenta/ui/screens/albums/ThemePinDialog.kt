package io.github.gativrek.momenta.ui.screens.albums

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import io.github.gativrek.momenta.data.settings.AppearanceStore
import io.github.gativrek.momenta.data.settings.ThemePinsStore
import io.github.gativrek.momenta.ui.theme.ThemePreset
import io.github.gativrek.momenta.ui.theme.ThemeSpec

class ThemePinViewModel(
    private val pins: ThemePinsStore,
    appearance: AppearanceStore,
) : ViewModel() {
    val allPins = pins.pins
    val songPins = pins.songPins
    val ownTheme = appearance.theme

    fun pin(
        album: Album,
        spec: ThemeSpec?,
    ) = pins.pin(album, spec)

    fun pinSong(
        track: Track,
        spec: ThemeSpec?,
    ) = pins.pinSong(track, spec)

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val c = (this[APPLICATION_KEY] as App).container
                    ThemePinViewModel(c.themePins, c.appearanceStore)
                }
            }
    }
}

/**
 * Pick the theme the app wears while this album plays: one of the built-in themes, the
 * user's own theme as it is now (a snapshot, so later edits don't change the pin), or none.
 */
@Composable
fun ThemePinDialog(
    album: Album,
    onDismiss: () -> Unit,
    viewModel: ThemePinViewModel = viewModel(factory = ThemePinViewModel.Factory),
) {
    val pins by viewModel.allPins.collectAsStateWithLifecycle()
    val own by viewModel.ownTheme.collectAsStateWithLifecycle()
    PinDialog(
        title = R.string.pin_theme_title,
        body = R.string.pin_theme_body,
        current = ThemePinsStore.keysOf(album).firstNotNullOfOrNull { pins[it] },
        own = own,
        onChoose = { spec ->
            viewModel.pin(album, spec)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/**
 * Pick the theme the app wears while this one song plays. It wins over a theme pinned to the
 * song's album.
 */
@Composable
fun SongThemePinDialog(
    track: Track,
    onDismiss: () -> Unit,
    viewModel: ThemePinViewModel = viewModel(factory = ThemePinViewModel.Factory),
) {
    val pins by viewModel.songPins.collectAsStateWithLifecycle()
    val own by viewModel.ownTheme.collectAsStateWithLifecycle()
    PinDialog(
        title = R.string.pin_song_theme_title,
        body = R.string.pin_song_theme_body,
        current = pins[ThemePinsStore.songKeyOf(track)],
        own = own,
        onChoose = { spec ->
            viewModel.pinSong(track, spec)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun PinDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    current: ThemeSpec?,
    own: ThemeSpec?,
    onChoose: (ThemeSpec?) -> Unit,
    onDismiss: () -> Unit,
) {
    fun choose(spec: ThemeSpec?) = onChoose(spec)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = stringResource(body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item {
                    PinOption(
                        label = stringResource(R.string.pin_theme_none),
                        swatch = null,
                        selected = current == null,
                        onClick = { choose(null) },
                    )
                }
                own?.takeIf { !it.isPristine }?.let { spec ->
                    item {
                        PinOption(
                            label = stringResource(R.string.pin_theme_own),
                            swatch = spec,
                            selected = current != null && current == spec,
                            onClick = { choose(spec) },
                        )
                    }
                }
                items(ThemePreset.entries) { preset ->
                    PinOption(
                        label = stringResource(preset.labelRes),
                        swatch = preset.spec,
                        selected = current == preset.spec,
                        onClick = { choose(preset.spec) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun PinOption(
    label: String,
    swatch: ThemeSpec?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        if (swatch != null) {
            // The theme's background with its accent and spark: enough to recognise it.
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(swatch.background, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Row {
                    Box(Modifier.size(8.dp).background(swatch.accent, CircleShape))
                    Box(Modifier.padding(start = 2.dp).size(8.dp).background(swatch.spark, CircleShape))
                }
            }
        }
        Text(text = label, modifier = Modifier.padding(start = 12.dp))
    }
}
