package io.github.gativrek.momenta.ui.screens.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.ui.theme.hex
import io.github.gativrek.momenta.ui.theme.parseHexOrNull
import kotlin.math.max
import kotlin.math.min

/** Hue 0..360, saturation and value 0..1. */
internal data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun toColor(): Color = Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

    companion object {
        fun of(color: Color): Hsv {
            val r = color.red
            val g = color.green
            val b = color.blue
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val delta = maxC - minC
            val hue =
                when {
                    delta == 0f -> 0f
                    maxC == r -> 60f * (((g - b) / delta).mod(6f))
                    maxC == g -> 60f * (((b - r) / delta) + 2f)
                    else -> 60f * (((r - g) / delta) + 4f)
                }
            return Hsv(hue, if (maxC == 0f) 0f else delta / maxC, maxC)
        }
    }
}

/**
 * Picks one color: hue, saturation and brightness sliders whose tracks show what each
 * position gives, a hex field for exact values, and [suggestions] to start from.
 * Nothing is applied until "Apply".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorEditorDialog(
    title: String,
    initial: Color,
    suggestions: List<Color>,
    onApply: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    var hsv by remember { mutableStateOf(Hsv.of(initial)) }
    var hexText by remember { mutableStateOf(initial.hex()) }
    val color = hsv.toColor()

    fun set(newHsv: Hsv) {
        hsv = newHsv
        hexText = newHsv.toColor().hex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Before and after, side by side.
                Row(modifier = Modifier.fillMaxWidth().height(48.dp).clip(MaterialTheme.shapes.medium)) {
                    Box(modifier = Modifier.weight(1f).height(48.dp).background(initial))
                    Box(modifier = Modifier.weight(1f).height(48.dp).background(color))
                }
                GradientSlider(
                    label = stringResource(R.string.color_hue),
                    value = hsv.hue / 360f,
                    brush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                    onValueChange = { set(hsv.copy(hue = it * 360f)) },
                )
                GradientSlider(
                    label = stringResource(R.string.color_saturation),
                    value = hsv.saturation,
                    brush =
                        Brush.horizontalGradient(
                            listOf(Color.hsv(hsv.hue, 0f, hsv.value), Color.hsv(hsv.hue, 1f, hsv.value)),
                        ),
                    onValueChange = { set(hsv.copy(saturation = it)) },
                )
                GradientSlider(
                    label = stringResource(R.string.color_brightness),
                    value = hsv.value,
                    brush = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hsv.hue, hsv.saturation, 1f))),
                    onValueChange = { set(hsv.copy(value = it)) },
                )
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text.take(7)
                        parseHexOrNull(hexText)?.let { hsv = Hsv.of(it) }
                    },
                    label = { Text(stringResource(R.string.color_hex)) },
                    isError = parseHexOrNull(hexText) == null,
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.distinct().forEach { suggestion ->
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(suggestion)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                                    .clickable { set(Hsv.of(suggestion)) }
                                    .semantics { contentDescription = suggestion.hex() },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(color) }) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A 0..1 slider drawn as a gradient bar, so the track itself shows the colors on offer. */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    brush: Brush,
    onValueChange: (Float) -> Unit,
) {
    val thumbColor = MaterialTheme.colorScheme.onSurface
    val ringColor = MaterialTheme.colorScheme.surface
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(vertical = 4.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { onValueChange((it.x / size.width).coerceIn(0f, 1f)) }
                    }.pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    }.semantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                        setProgress { target ->
                            onValueChange(target.coerceIn(0f, 1f))
                            true
                        }
                    },
        ) {
            val radius = size.height / 2
            drawRoundRect(brush, cornerRadius = CornerRadius(radius))
            val x = (value * size.width).coerceIn(radius, size.width - radius)
            drawCircle(ringColor, radius = radius, center = Offset(x, radius))
            drawCircle(
                thumbColor,
                radius = radius - 2.dp.toPx(),
                center = Offset(x, radius),
                style = Stroke(3.dp.toPx()),
            )
        }
    }
}

/** A row with a color swatch and its hex value. */
@Composable
internal fun ColorRow(
    label: String,
    description: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            color.hex(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
