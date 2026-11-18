package io.github.gativrek.momenta.ui.screens.moments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.Moments
import io.github.gativrek.momenta.data.settings.MomentsSettings
import io.github.gativrek.momenta.ui.theme.moment
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The lightning-gear: everything about the Moments of the playing song, on two pages so
 * neither is crowded. Timing: where the Moment starts and ends, to the tenth of a second.
 * Feel: its name, run-up and volume boost.
 *
 * Edits are kept in the sheet and saved when switching Moment, previewing, or closing the
 * sheet, so dragging a slider isn't a database write per frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentGearSheet(
    moments: List<MomentEntity>,
    initialMomentId: Long?,
    durationMs: Long,
    positionMs: Long,
    settings: MomentsSettings,
    onSave: (MomentEntity) -> Unit,
    onDelete: (MomentEntity) -> Unit,
    onPreview: (MomentEntity) -> Unit,
    onBoostsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf(initialMomentId ?: moments.firstOrNull()?.id) }
    val stored = moments.firstOrNull { it.id == selectedId } ?: moments.firstOrNull()
    if (stored == null) {
        // The last Moment was deleted (here or elsewhere): nothing left to edit.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    // The working copy; re-seeded when another Moment is picked.
    var draft by remember(stored.id) { mutableStateOf(stored) }
    var page by rememberSaveable { mutableStateOf(GearPage.Timing) }
    val index = moments.indexOfFirst { it.id == stored.id }

    fun commit() {
        if (draft != stored) onSave(draft)
    }

    ModalBottomSheet(
        onDismissRequest = {
            commit()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
        ) {
            Header(
                title = draft.name ?: stringResource(R.string.moment_number, index + 1),
                heardCount = stored.heardCount,
                onDelete = { onDelete(stored) },
            )

            if (moments.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    moments.forEachIndexed { i, m ->
                        FilterChip(
                            selected = m.id == stored.id,
                            onClick = {
                                commit()
                                selectedId = m.id
                            },
                            label = {
                                Text(
                                    m.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.moment_number, i + 1),
                                )
                            },
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                GearPage.entries.forEach { p ->
                    SegmentedButton(
                        selected = page == p,
                        onClick = { page = p },
                        shape = SegmentedButtonDefaults.itemShape(p.ordinal, GearPage.entries.size),
                    ) { Text(stringResource(p.labelRes)) }
                }
            }
            Spacer(Modifier.height(24.dp))

            when (page) {
                GearPage.Timing ->
                    TimingPage(
                        draft = draft,
                        durationMs = durationMs,
                        positionMs = positionMs,
                        onChange = { draft = it },
                        onPreview = {
                            commit()
                            onPreview(draft)
                        },
                    )
                GearPage.Feel ->
                    FeelPage(
                        draft = draft,
                        settings = settings,
                        onChange = { draft = it },
                        onBoostsChange = onBoostsChange,
                    )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class GearPage(
    val labelRes: Int,
) {
    Timing(R.string.moment_page_timing),
    Feel(R.string.moment_page_feel),
}

/** Which end of the Moment the fine controls move. */
private enum class Edge { Start, End }

@Composable
private fun Header(
    title: String,
    heardCount: Int,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_moment_gear),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.moment,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                text =
                    if (heardCount == 0) {
                        stringResource(R.string.moment_heard_never)
                    } else {
                        pluralStringResource(R.plurals.moment_heard, heardCount, heardCount)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.moment_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimingPage(
    draft: MomentEntity,
    durationMs: Long,
    positionMs: Long,
    onChange: (MomentEntity) -> Unit,
    onPreview: () -> Unit,
) {
    var edge by rememberSaveable { mutableStateOf(Edge.Start) }
    val total = durationMs.coerceAtLeast(draft.endMs).coerceAtLeast(1)

    fun moveStart(ms: Long) = onChange(draft.copy(startMs = ms.coerceIn(0, draft.endMs - Moments.MIN_DURATION_MS)))

    fun moveEnd(ms: Long) = onChange(draft.copy(endMs = ms.coerceIn(draft.startMs + Moments.MIN_DURATION_MS, total)))

    fun move(ms: Long) = if (edge == Edge.Start) moveStart(ms) else moveEnd(ms)

    val selected = if (edge == Edge.Start) draft.startMs else draft.endMs

    // The two ends, big and tappable: tap one to fine-tune it below.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeBlock(
            label = stringResource(R.string.moment_start),
            valueMs = draft.startMs,
            selected = edge == Edge.Start,
            onClick = { edge = Edge.Start },
            modifier = Modifier.weight(1f),
        )
        TimeBlock(
            label = stringResource(R.string.moment_end),
            valueMs = draft.endMs,
            selected = edge == Edge.End,
            onClick = { edge = Edge.End },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = stringResource(R.string.moment_length, formatSeconds(draft.durationMs)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )

    // Coarse: anywhere in the song.
    RangeSlider(
        value = (draft.startMs / total.toFloat())..(draft.endMs / total.toFloat()),
        onValueChange = { range ->
            val start = (range.start * total).roundToLong()
            val end = (range.endInclusive * total).roundToLong()
            if (end - start >= Moments.MIN_DURATION_MS) onChange(draft.copy(startMs = start, endMs = end))
        },
        modifier = Modifier.padding(top = 16.dp),
    )

    // Fine: the selected end, by a second or a tenth, or to where the song is now.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Nudge("−1s", Modifier.weight(1f)) { move(selected - 1_000) }
        Nudge("−0.1", Modifier.weight(1f)) { move(selected - 100) }
        Nudge(stringResource(R.string.moment_now), Modifier.weight(1.2f)) { move(positionMs) }
        Nudge("+0.1", Modifier.weight(1f)) { move(selected + 100) }
        Nudge("+1s", Modifier.weight(1f)) { move(selected + 1_000) }
    }

    FilledTonalButton(onClick = onPreview, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Icon(
            painterResource(R.drawable.ic_play),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Text(stringResource(R.string.moment_preview), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

@Composable
private fun TimeBlock(
    label: String,
    valueMs: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(2.dp, colors.moment) else null,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Text(formatMomentTime(valueMs), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }
}

@Composable
private fun Nudge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp), modifier = modifier) {
        Text(text, maxLines = 1)
    }
}

@Composable
private fun FeelPage(
    draft: MomentEntity,
    settings: MomentsSettings,
    onChange: (MomentEntity) -> Unit,
    onBoostsChange: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = draft.name.orEmpty(),
        onValueChange = { onChange(draft.copy(name = it.ifBlank { null })) },
        label = { Text(stringResource(R.string.moment_name)) },
        placeholder = { Text(stringResource(R.string.moment_name_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    LabeledSlider(
        label = stringResource(R.string.moment_run_up_label),
        value = formatSeconds(draft.leadInMs),
        hint = stringResource(R.string.moment_run_up_hint),
        fraction = draft.leadInMs / Moments.MAX_LEAD_IN_MS.toFloat(),
        steps = (Moments.MAX_LEAD_IN_MS / 1000).toInt() - 1,
        onChange = { onChange(draft.copy(leadInMs = (it * Moments.MAX_LEAD_IN_MS / 1000).roundToLong() * 1000)) },
    )
    Spacer(Modifier.height(20.dp))
    LabeledSlider(
        label = stringResource(R.string.moment_boost_label),
        value =
            if (draft.boostPercent == 0) {
                stringResource(R.string.moment_boost_none)
            } else {
                stringResource(R.string.moment_boost_value, draft.boostPercent.coerceAtMost(Moments.MAX_BOOST_PERCENT))
            },
        hint =
            when {
                settings.chill -> stringResource(R.string.moment_boost_chill)
                !settings.boosts -> stringResource(R.string.moment_boost_disabled)
                else -> stringResource(R.string.moment_boost_hint)
            },
        fraction = draft.boostPercent / Moments.MAX_BOOST_PERCENT.toFloat(),
        steps = Moments.MAX_BOOST_PERCENT / 5 - 1,
        onChange = { onChange(draft.copy(boostPercent = (it * Moments.MAX_BOOST_PERCENT).roundToInt())) },
    )
    Spacer(Modifier.height(20.dp))
    // App-wide, so set apart from this Moment's own settings.
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.moment_boosts_all), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.moment_boosts_all_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.boosts, onCheckedChange = onBoostsChange)
        }
    }
}

/** A setting's name with its value on the right, a slider, and a quiet hint under it. */
@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    hint: String,
    fraction: Float,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.moment)
        }
        Slider(value = fraction.coerceIn(0f, 1f), onValueChange = onChange, steps = steps)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "7:40.3": Moments are edited to the tenth of a second. */
fun formatMomentTime(ms: Long): String {
    val tenths = (ms.coerceAtLeast(0) + 50) / 100
    val minutes = tenths / 600
    val seconds = (tenths / 10) % 60
    return String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths % 10)
}

/** "16.0 s" */
fun formatSeconds(ms: Long): String = String.format(Locale.ROOT, "%.1f s", ms / 1000.0)
