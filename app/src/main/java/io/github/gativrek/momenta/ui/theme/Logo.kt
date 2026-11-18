package io.github.gativrek.momenta.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val haloPath = PathParser().parsePathString(LogoPaths.HALO).toPath()
private val markPath = PathParser().parsePathString(LogoPaths.MARK).toPath()
private val sparkPath = PathParser().parsePathString(LogoPaths.SPARK).toPath()

/**
 * The logo, drawn in the current theme: halo and spark in the accent, the M in the text
 * color. Same shapes as the launcher icon (design/logo/logo.svg). [size] is the full
 * 108-unit canvas, which has generous padding built in like any adaptive icon.
 */
@Composable
fun MomentaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    ink: Color = MaterialTheme.colorScheme.onBackground,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / LogoPaths.VIEWPORT
        scale(s, pivot = Offset.Zero) {
            drawPath(haloPath, accent, style = Stroke(LogoPaths.HALO_WIDTH, cap = StrokeCap.Round))
            drawPath(
                markPath,
                ink,
                style = Stroke(LogoPaths.MARK_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Miter),
            )
            drawPath(sparkPath, accent)
        }
    }
}
