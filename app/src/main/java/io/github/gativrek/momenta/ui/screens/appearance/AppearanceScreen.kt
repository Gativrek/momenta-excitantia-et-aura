package io.github.gativrek.momenta.ui.screens.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.ui.components.DetailTopBar
import io.github.gativrek.momenta.ui.theme.Edge
import io.github.gativrek.momenta.ui.theme.LocalThemeSpec
import io.github.gativrek.momenta.ui.theme.MomentaLogo
import io.github.gativrek.momenta.ui.theme.MomentaTheme
import io.github.gativrek.momenta.ui.theme.Pattern
import io.github.gativrek.momenta.ui.theme.ThemePreset
import io.github.gativrek.momenta.ui.theme.ThemeSpec
import io.github.gativrek.momenta.ui.theme.themeBackdrop

/** Which of the four theme colors is being edited. */
private enum class ColorSlot(
    val labelRes: Int,
    val descriptionRes: Int?,
    val get: (ThemeSpec) -> Color,
    val set: (ThemeSpec, Color) -> ThemeSpec,
) {
    Background(R.string.color_background, null, { it.background }, {
        s,
        c,
        ->
        s.copy(background = c)
    }),
    Accent(R.string.color_accent, R.string.color_accent_desc, { it.accent }, { s, c -> s.copy(accent = c) }),
    Spark(R.string.color_spark, R.string.color_spark_desc, { it.spark }, { s, c -> s.copy(spark = c) }),
    PatternInk(R.string.color_pattern, null, { it.patternColor }, {
        s,
        c,
        ->
        s.copy(patternColor = c)
    }),
}

/**
 * Theme settings. The whole app, this screen included, re-themes live as you edit, so
 * the screen is its own preview; the sample player at the top shows the accents at work.
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = viewModel(factory = AppearanceViewModel.Factory),
) {
    val spec by viewModel.theme.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ColorSlot?>(null) }

    Column(modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.appearance_title),
            onBack = onBack,
            actions = {
                if (!spec.isPristine) {
                    TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.appearance_reset)) }
                }
            },
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Header(spec) }
            item { SamplePlayer() }
            item { SectionTitle(R.string.appearance_themes) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ThemePreset.entries) { preset ->
                        PresetTile(
                            preset,
                            selected = spec.preset == preset,
                            onClick = { viewModel.choosePreset(preset) },
                        )
                    }
                }
            }
            item { SectionTitle(R.string.appearance_pattern) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(Pattern.entries) { pattern ->
                        PatternTile(
                            spec = spec,
                            pattern = pattern,
                            selected = spec.pattern == pattern,
                            onClick = { viewModel.update { it.copy(pattern = pattern) } },
                        )
                    }
                }
            }
            item { SectionTitle(R.string.appearance_colors) }
            items(ColorSlot.entries) { slot ->
                ColorRow(
                    label = stringResource(slot.labelRes),
                    description = slot.descriptionRes?.let { stringResource(it) },
                    color = slot.get(spec),
                    onClick = { editing = slot },
                )
            }
            item { SectionTitle(R.string.appearance_feel) }
            item {
                LabeledSlider(
                    label = stringResource(R.string.appearance_pattern_strength),
                    value = spec.patternStrength,
                    onValueChange = { v -> viewModel.update { it.copy(patternStrength = v) } },
                )
            }
            item {
                LabeledSlider(
                    label = stringResource(R.string.appearance_aura),
                    value = spec.aura,
                    onValueChange = { v -> viewModel.update { it.copy(aura = v) } },
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.appearance_corners), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Edge.entries.forEach { edge ->
                            FilterChip(
                                selected = spec.edge == edge,
                                onClick = { viewModel.update { it.copy(edge = edge) } },
                                label = { Text(stringResource(edge.labelRes)) },
                            )
                        }
                    }
                }
            }
            item { AppSignature() }
        }
    }

    editing?.let { slot ->
        ColorEditorDialog(
            title = stringResource(slot.labelRes),
            initial = slot.get(spec),
            // Every preset's color for this slot: a quick way to borrow from another theme.
            suggestions = ThemePreset.entries.map { slot.get(it.spec) },
            onApply = { color ->
                viewModel.update { slot.set(it, color) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Header(spec: ThemeSpec) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentaLogo(size = 72.dp)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(spec.preset.labelRes), style = MaterialTheme.typography.headlineSmall)
            Text(
                text =
                    stringResource(
                        if (spec.isPristine) R.string.appearance_as_built else R.string.appearance_customized,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A stand-in mini-player and buttons, so accent changes are visible without music playing. */
@Composable
private fun SamplePlayer() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
            Column {
                LinearProgressIndicator(
                    progress = { 0.62f },
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                // The sample's "album art" is the theme itself.
                                .themeBackdrop(LocalThemeSpec.current),
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.sample_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.sample_artist),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.ic_pause),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text(stringResource(R.string.action_play_all)) }
            OutlinedButton(onClick = {}) { Text(stringResource(R.string.action_shuffle)) }
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** A preset as a little phone screen in its own theme: backdrop, name, accent dots. */
@Composable
private fun PresetTile(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spec = preset.spec
    // The tile is drawn in the preset's own theme, not the current one.
    MomentaTheme(spec) {
        val colors = MaterialTheme.colorScheme
        Column(
            modifier =
                Modifier
                    .width(108.dp)
                    .height(156.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .themeBackdrop(spec)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) colors.primary else colors.outlineVariant,
                        shape = MaterialTheme.shapes.medium,
                    ).clickable(role = Role.RadioButton, onClick = onClick)
                    .semantics { this.selected = selected }
                    .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MomentaLogo(size = 40.dp)
                if (selected) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(preset.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(colors.primary, colors.tertiary).forEach {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(it))
                    }
                }
            }
        }
    }
}

/** A pattern drawn in the current colors, with its name under it. */
@Composable
private fun PatternTile(
    spec: ThemeSpec,
    pattern: Pattern,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Full strength in the tile so the pattern is recognizable at this size.
    val sample = remember(spec, pattern) { spec.copy(pattern = pattern, patternStrength = 1f) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics { this.selected = selected },
    ) {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .themeBackdrop(sample)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) colors.primary else colors.outlineVariant,
                        shape = MaterialTheme.shapes.medium,
                    ),
        )
        Text(
            text = stringResource(pattern.labelRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.percent, (value * 100).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onValueChange)
    }
}

/** The app's full name and version, quietly at the end of the list. */
@Composable
private fun AppSignature() {
    val context = LocalContext.current
    val version =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MomentaLogo(size = 40.dp)
        Text(
            text = stringResource(R.string.app_full_name),
            style = MaterialTheme.typography.titleMedium,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 8.dp),
        )
        version?.let {
            Text(
                text = stringResource(R.string.app_version, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
