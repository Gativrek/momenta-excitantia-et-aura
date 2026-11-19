package io.github.gativrek.momenta.ui.screens.moments

import android.content.Intent
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.ui.theme.Edge
import io.github.gativrek.momenta.ui.theme.LocalThemeSpec

/** True while a Moment's clip that plays in the cover is showing (or fading). */
@Composable
fun coverClipShowing(): Boolean {
    val clip by LocalMomentClip.current
    val glowState = LocalMomentGlow.current
    val showing by remember { derivedStateOf { glowState.value > 0f } }
    return clip.uri != null && !clip.behind && (showing || clip.playing)
}

/**
 * The playing Moment's clip, full screen behind the app, fading in and out with the
 * reaction, for clips set to play behind the app. Cropped to fill the screen.
 */
@Composable
fun MomentClipBackdrop(modifier: Modifier = Modifier) {
    val clip by LocalMomentClip.current
    val glowState = LocalMomentGlow.current
    val showing by remember { derivedStateOf { glowState.value > 0f } }
    val uri = clip.uri
    if (uri == null || !clip.behind || !(showing || clip.playing)) return
    val (player, videoSize) = rememberClipPlayer(uri, clip.playing)

    BoxWithConstraints(
        modifier =
            modifier
                .clipToBounds()
                .graphicsLayer { alpha = glowState.value * BACKDROP_ALPHA }
                .drawWithContent {
                    drawContent()
                    // Darkened toward the bottom, where the controls and text are.
                    drawRect(Brush.verticalGradient(listOf(SCRIM_TOP, SCRIM_BOTTOM)))
                },
    ) {
        // Fills the screen like a cover photo: scaled up until no edge shows, the rest cropped.
        val boxRatio = constraints.maxWidth.toFloat() / constraints.maxHeight.coerceAtLeast(1)
        val videoRatio = videoSize.ratio() ?: boxRatio
        val width: Float
        val height: Float
        if (boxRatio > videoRatio) {
            width = constraints.maxWidth.toFloat()
            height = width / videoRatio
        } else {
            height = constraints.maxHeight.toFloat()
            width = height * videoRatio
        }
        val density = LocalDensity.current
        ClipSurface(player, Modifier.requiredSize(with(density) { width.toDp() }, with(density) { height.toDp() }))
    }
}

/**
 * The playing Moment's clip in place of the cover, for clips set to play there. The cover
 * takes the clip's shape as the reaction swells: a 16:9 clip turns the square into a wide
 * frame as wide as the cover, a tall one a narrow frame as high, so the whole clip shows.
 * Goes over the cover art, which [coverClipShowing] tells the screen to fade out.
 */
@Composable
fun MomentClipCover(
    size: Dp,
    glow: State<Float>,
    modifier: Modifier = Modifier,
) {
    val clip by LocalMomentClip.current
    val uri = clip.uri
    if (uri == null || clip.behind || !coverClipShowing()) return
    val (player, videoSize) = rememberClipPlayer(uri, clip.playing)
    val ratio = videoSize.ratio() ?: 1f
    val edge = LocalThemeSpec.current.edge
    Box(modifier = modifier.requiredSize(size), contentAlignment = Alignment.Center) {
        val t = glow.value
        // From the square cover to the clip's own shape, inside the same square.
        val width = size * (1f + (minOf(1f, ratio) - 1f) * t)
        val height = size * (1f + (minOf(1f, 1f / ratio) - 1f) * t)
        val corner = minOf(width, height)
        Box(
            modifier =
                Modifier
                    .requiredSize(width, height)
                    .graphicsLayer { alpha = t }
                    .clip(if (edge == Edge.Sharp) CutCornerShape(corner / 10) else RoundedCornerShape(corner / 8)),
        ) {
            // The video keeps its shape; while the frame is still changing it's cropped to fit.
            val scale = maxOf(width / (size * minOf(1f, ratio)), height / (size * minOf(1f, 1f / ratio)))
            ClipSurface(
                player,
                Modifier
                    .align(Alignment.Center)
                    .requiredSize(size * minOf(1f, ratio) * scale, size * minOf(1f, 1f / ratio) * scale),
            )
        }
    }
}

/** Width over height, or null until the clip's size is known. */
private fun VideoSize.ratio(): Float? = if (width > 0 && height > 0) width * pixelWidthHeightRatio / height else null

/**
 * A silent, looping player for the clip at [uri] (its audio is never even decoded), playing
 * from the top while [playing] and paused otherwise. Released when it leaves the screen.
 */
@Composable
private fun rememberClipPlayer(
    uri: String,
    playing: Boolean,
): Pair<ExoPlayer, VideoSize> {
    val context = LocalContext.current
    val player =
        remember(uri) {
            ExoPlayer.Builder(context).build().apply {
                trackSelectionParameters =
                    trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(uri.toUri()))
                prepare()
            }
        }
    var videoSize by remember(player) { mutableStateOf(VideoSize.UNKNOWN) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoSize = size
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, playing) {
        if (playing) {
            player.seekTo(0)
            player.play()
        } else {
            player.pause()
        }
    }
    return player to videoSize
}

@Composable
private fun ClipSurface(
    player: ExoPlayer,
    modifier: Modifier,
) {
    AndroidView(factory = { TextureView(it).also(player::setVideoTextureView) }, modifier = modifier)
}

/** The clip setting on a Moment's Feel page: pick a video, where it plays, change or remove it. */
@Composable
internal fun ClipSetting(
    clipUri: String?,
    behind: Boolean,
    onChange: (String?) -> Unit,
    onBehindChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val pick =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            if (uri != null) {
                // Keep reading it after the app restarts. Some pickers don't offer a lasting
                // grant; the clip then plays until the phone restarts.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                onChange(uri.toString())
            }
        }
    val request = PickVisualMediaRequest(PickVisualMedia.VideoOnly)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.moment_clip_label), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (clipUri != null) R.string.moment_clip_set else R.string.moment_clip_none),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.moment_clip_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        if (clipUri != null) {
            val places = listOf(false to R.string.moment_clip_in_cover, true to R.string.moment_clip_behind)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                places.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = behind == value,
                        onClick = { onBehindChange(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, places.size),
                    ) { Text(stringResource(label)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick.launch(request) }) {
                Text(stringResource(if (clipUri != null) R.string.moment_clip_change else R.string.moment_clip_add))
            }
            if (clipUri != null) {
                TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.moment_clip_remove)) }
            }
        }
    }
}

/** A clip behind the app at the height of a Moment: strong, with the theme's backdrop still just there. */
private const val BACKDROP_ALPHA = 0.85f
private val SCRIM_TOP = Color(0x33000000)
private val SCRIM_BOTTOM = Color(0x99000000)
