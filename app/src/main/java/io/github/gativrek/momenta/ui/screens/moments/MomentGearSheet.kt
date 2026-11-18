package io.github.gativrek.momenta.ui.screens.moments

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.Moments
import io.github.gativrek.momenta.data.settings.MomentsSettings
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The lightning-gear: everything about the Moments of the playing song. Edits are kept in
 * the sheet and saved when switching Moment, previewing, or closing the sheet, so dragging
 * a slider isn't a database write per frame.
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_moment_gear),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    stringResource(R.string.moment_gear_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            if (moments.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
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

            OutlinedTextField(
                value = draft.name.orEmpty(),
                onValueChange = { draft = draft.copy(name = it.ifBlank { null }) },
                label = { Text(stringResource(R.string.moment_name)) },
                placeholder = { Text(stringResource(R.string.moment_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Where: coarse over the whole song, then nudged to the tenth of a second.
            Text(
                stringResource(
                    R.string.moment_range,
                    formatMomentTime(draft.startMs),
                    formatMomentTime(draft.endMs),
                    formatSeconds(draft.durationMs),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            val total = durationMs.coerceAtLeast(draft.endMs).coerceAtLeast(1).toFloat()
            RangeSlider(
                value = (draft.startMs / total)..(draft.endMs / total),
                onValueChange = { range ->
                    val start = (range.start * total).roundToLong()
                    val end = (range.endInclusive * total).roundToLong()
                    if (end - start >= Moments.MIN_DURATION_MS) draft = draft.copy(startMs = start, endMs = end)
                },
            )
            Nudger(
                label = stringResource(R.string.moment_start),
                valueMs = draft.startMs,
                onChange = { v -> draft = draft.copy(startMs = v.coerceIn(0, draft.endMs - Moments.MIN_DURATION_MS)) },
                onNow = {
                    draft =
                        draft.copy(
                            startMs = positionMs.coerceAtMost(draft.endMs - Moments.MIN_DURATION_MS),
                        )
                },
            )
            Nudger(
                label = stringResource(R.string.moment_end),
                valueMs = draft.endMs,
                onChange = { v ->
                    draft = draft.copy(endMs = v.coerceIn(draft.startMs + Moments.MIN_DURATION_MS, total.toLong()))
                },
                onNow = {
                    draft =
                        draft.copy(
                            endMs = positionMs.coerceAtLeast(draft.startMs + Moments.MIN_DURATION_MS),
                        )
                },
            )
            OutlinedButton(
                onClick = {
                    commit()
                    onPreview(draft)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    stringResource(R.string.moment_preview),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }

            HorizontalDivider()

            LabeledSlider(
                label = stringResource(R.string.moment_run_up, formatSeconds(draft.leadInMs)),
                hint = stringResource(R.string.moment_run_up_hint),
                value = draft.leadInMs / Moments.MAX_LEAD_IN_MS.toFloat(),
                steps = (Moments.MAX_LEAD_IN_MS / 1000).toInt() - 1,
                onChange = { draft = draft.copy(leadInMs = (it * Moments.MAX_LEAD_IN_MS / 1000).roundToLong() * 1000) },
            )
            LabeledSlider(
                label =
                    if (draft.boostPercent == 0) {
                        stringResource(R.string.moment_boost_off)
                    } else {
                        stringResource(R.string.moment_boost, draft.boostPercent)
                    },
                hint =
                    when {
                        settings.chill -> stringResource(R.string.moment_boost_chill)
                        !settings.boosts -> stringResource(R.string.moment_boost_disabled)
                        else -> stringResource(R.string.moment_boost_hint)
                    },
                value = draft.boostPercent / Moments.MAX_BOOST_PERCENT.toFloat(),
                steps = Moments.MAX_BOOST_PERCENT / 5 - 1,
                onChange = { draft = draft.copy(boostPercent = (it * Moments.MAX_BOOST_PERCENT).roundToInt()) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
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

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (stored.heardCount == 0) {
                        stringResource(R.string.moment_heard_never)
                    } else {
                        pluralStringResource(R.plurals.moment_heard, stored.heardCount, stored.heardCount)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onDelete(stored) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(
                        stringResource(R.string.moment_delete),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

/** A time with fine steps either side and "Now" (the playing position). */
@Composable
private fun Nudger(
    label: String,
    valueMs: Long,
    onChange: (Long) -> Unit,
    onNow: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
        Step("−1s") { onChange(valueMs - 1_000) }
        Step("−.1") { onChange(valueMs - 100) }
        Text(
            formatMomentTime(valueMs),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )
        Step("+.1") { onChange(valueMs + 100) }
        Step("+1s") { onChange(valueMs + 1_000) }
        TextButton(onClick = onNow) { Text(stringResource(R.string.moment_now)) }
    }
}

@Composable
private fun Step(
    text: String,
    onClick: () -> Unit,
) {
    // Compact: four of these, the time and "Now" share one row.
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = Modifier.defaultMinSize(minWidth = 40.dp),
    ) { Text(text) }
}

@Composable
private fun LabeledSlider(
    label: String,
    hint: String,
    value: Float,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange, steps = steps)
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
