package io.github.gativrek.momenta.ui.screens.nowplaying

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.playback.SleepTimerState
import io.github.gativrek.momenta.ui.format.formatDuration
import kotlinx.coroutines.delay

private val PRESET_MINUTES = listOf(15, 30, 45, 60, 90)
private const val MAX_CUSTOM_MINUTES = 12 * 60

/** Milliseconds left on [timer], ticking once a second while shown. */
@Composable
fun rememberSleepTimerRemaining(timer: SleepTimerState?): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(timer) {
        while (timer != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    return timer?.let { (it.endElapsedMs - now).coerceAtLeast(0) } ?: 0L
}

/** Button label: "Sleep timer", "Stops in 12:34" or "Stops after this track". */
@Composable
fun sleepTimerLabel(timer: SleepTimerState?): String {
    val remaining = rememberSleepTimerRemaining(timer)
    return when {
        timer == null -> stringResource(R.string.sleep_timer)
        timer.waitingForTrackEnd -> stringResource(R.string.sleep_timer_end_of_track)
        else -> stringResource(R.string.sleep_timer_remaining, formatDuration(remaining))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    timer: SleepTimerState?,
    onStart: (minutes: Int, finishTrack: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    var finishTrack by rememberSaveable { mutableStateOf(timer?.finishTrack ?: false) }
    var showCustom by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (timer != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sleepTimerLabel(timer),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        onCancelTimer()
                        onDismiss()
                    }) { Text(stringResource(R.string.sleep_timer_cancel)) }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { finishTrack = !finishTrack }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sleep_timer_finish_track), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.sleep_timer_finish_track_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = finishTrack, onCheckedChange = { finishTrack = it })
            }

            PRESET_MINUTES.forEach { minutes ->
                SheetOption(pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes)) {
                    onStart(minutes, finishTrack)
                    onDismiss()
                }
            }
            SheetOption(stringResource(R.string.sleep_timer_custom)) { showCustom = true }
        }
    }

    if (showCustom) {
        CustomMinutesDialog(
            onConfirm = { minutes ->
                showCustom = false
                onStart(minutes, finishTrack)
                onDismiss()
            },
            onDismiss = { showCustom = false },
        )
    }
}

@Composable
private fun SheetOption(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun CustomMinutesDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val minutes = text.toIntOrNull()?.takeIf { it in 1..MAX_CUSTOM_MINUTES }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer_custom)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.sleep_timer_custom_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let(onConfirm) }, enabled = minutes != null) {
                Text(stringResource(R.string.sleep_timer_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
