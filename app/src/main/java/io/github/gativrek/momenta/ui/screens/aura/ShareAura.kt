package io.github.gativrek.momenta.ui.screens.aura

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.aura.AuraProfile
import io.github.gativrek.momenta.ui.screens.moments.SavedMoment
import io.github.gativrek.momenta.ui.theme.LocalThemeSpec
import io.github.gativrek.momenta.ui.theme.ThemeBackdrop
import io.github.gativrek.momenta.ui.theme.moment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * The share card, previewed as it will look, and a Share button that hands it to the share
 * sheet as a picture. Drawn on the phone in the user's own theme; nothing goes online.
 */
@Composable
fun ShareAuraDialog(
    profile: AuraProfile,
    hypeMs: Long,
    topMoment: SavedMoment?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            // Drawn on screen as usual and recorded a second time into a layer, scaled up to
            // SHARE_WIDTH_PX: the same draw calls replayed larger, so text and patterns stay
            // sharp instead of being stretched from the small preview.
            Box(
                Modifier.drawWithContent {
                    drawContent()
                    if (size.width <= 0f) return@drawWithContent
                    val factor = SHARE_WIDTH_PX / size.width
                    layer.record(size = IntSize(SHARE_WIDTH_PX, (size.height * factor).roundToInt())) {
                        scale(factor, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
                    }
                },
            ) { AuraCard(profile, hypeMs, topMoment) }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        shareImage(context, bitmap)
                        busy = false
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.aura_share_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun AuraCard(
    profile: AuraProfile,
    hypeMs: Long,
    topMoment: SavedMoment?,
) {
    val month = profile.thisMonth
    val era = profile.currentEra
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(CARD_RATIO).clip(MaterialTheme.shapes.large)) {
        ThemeBackdrop(LocalThemeSpec.current, Modifier.matchParentSize())
        Column(modifier = Modifier.matchParentSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_aura),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.aura_card_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.aura_card_rank_lead, monthName(month.month)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = rankName(month.rank),
                style = MaterialTheme.typography.headlineSmall,
                color = rankColor(month.rank),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (era != null) {
                Text(
                    text = stringResource(R.string.aura_era, era.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CardStat(formatSpan(month.listenedMs), stringResource(R.string.aura_card_month))
                if (hypeMs > 0) CardStat(formatSpan(hypeMs), stringResource(R.string.aura_card_hype), accent = true)
            }
            Spacer(Modifier.height(16.dp))
            profile.topSongs.take(CARD_SONGS).forEachIndexed { index, song ->
                Text(
                    text = "${index + 1}  ${song.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            if (topMoment != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_moment),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.moment,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text =
                            topMoment.moment.name
                                ?.takeIf(String::isNotBlank)
                                ?.let { "$it · ${topMoment.track.title}" } ?: topMoment.track.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
                    )
                }
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.aura_card_moment_heard,
                            topMoment.moment.heardCount,
                            topMoment.moment.heardCount,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 22.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = stringResource(R.string.app_full_name),
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardStat(
    value: String,
    label: String,
    accent: Boolean = false,
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = if (accent) MaterialTheme.colorScheme.moment else MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Writes [bitmap] to the app's cache and opens the share sheet with it. */
private suspend fun shareImage(
    context: Context,
    bitmap: Bitmap,
) {
    val file =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            File(dir, SHARE_FILE).also { file ->
                // A hardware bitmap can't always be compressed directly: copy it to a software one.
                val software = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                file.outputStream().use { software.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.aura_share)))
}

/** Width to height: a phone-story shape, so it fits a status or a story as it is. */
private const val CARD_RATIO = 0.6f
private const val CARD_SONGS = 3

/** Width of the shared picture in pixels, whatever the preview's size: a full-HD story. */
private const val SHARE_WIDTH_PX = 1080
private const val SHARE_DIR = "shared"
private const val SHARE_FILE = "my-aura.png"
