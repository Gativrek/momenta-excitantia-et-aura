package io.github.gativrek.momenta.ui.screens.nowplaying

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.moments.MomentEntity
import io.github.gativrek.momenta.data.moments.MomentSuggestion
import io.github.gativrek.momenta.data.settings.MomentsSettings
import io.github.gativrek.momenta.playback.PlayerUiState
import io.github.gativrek.momenta.ui.components.PlayPauseButton
import io.github.gativrek.momenta.ui.components.SkipButton
import io.github.gativrek.momenta.ui.format.formatDuration
import io.github.gativrek.momenta.ui.screens.moments.FieryTitle
import io.github.gativrek.momenta.ui.screens.moments.HeavenlyLight
import io.github.gativrek.momenta.ui.screens.moments.LocalMomentGlow
import io.github.gativrek.momenta.ui.screens.moments.MomentGearSheet
import io.github.gativrek.momenta.ui.screens.moments.formatMomentTime
import io.github.gativrek.momenta.ui.screens.moments.momentAura
import io.github.gativrek.momenta.ui.theme.MOMENT_MARK_ALPHA
import io.github.gativrek.momenta.ui.theme.moment
import kotlinx.coroutines.delay

/** What the lightning-gear opens on: a Moment's ID, or the song's first Moment. */
private const val GEAR_FIRST = -1L

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position by viewModel.finePosition.collectAsStateWithLifecycle(initialValue = 0L)
    val moments by viewModel.currentMoments.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val linerNote by viewModel.linerNote.collectAsStateWithLifecycle()

    // Nothing left to show (queue cleared, or service gone): close instead of an empty screen.
    LaunchedEffect(state.connected, state.nowPlaying) {
        if (state.connected && state.nowPlaying == null) onCollapse()
    }

    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    var gearFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var justSaved by remember { mutableStateOf<Long?>(null) }

    // The "Moment saved" note offers the lightning-gear for a few seconds, then goes.
    LaunchedEffect(justSaved) {
        if (justSaved != null) {
            delay(SAVED_NOTE_MS)
            justSaved = null
        }
    }

    NowPlayingContent(
        state = state,
        positionMs = position,
        moments = moments,
        settings = settings,
        justSavedMoment = justSaved != null,
        suggestion = suggestion,
        linerNote = linerNote,
        onCollapse = onCollapse,
        onTogglePlayPause = viewModel::togglePlayPause,
        onPrevious = viewModel::previous,
        onPreviousTrack = viewModel::previousTrack,
        onNext = viewModel::next,
        onSeek = viewModel::seekTo,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeat,
        onOpenSleepTimer = { showSleepTimer = true },
        onOpenQueue = onOpenQueue,
        onSaveMoment = { viewModel.saveMoment { id -> justSaved = id } },
        onSaveSuggestion = { viewModel.saveSuggestion { id -> justSaved = id } },
        onDismissSuggestion = viewModel::dismissSuggestion,
        onOpenGear = {
            gearFor = justSaved ?: GEAR_FIRST
            justSaved = null
        },
        onToggleChill = { viewModel.setChill(!settings.chill) },
        modifier = modifier,
    )

    if (showSleepTimer) {
        SleepTimerSheet(
            timer = state.sleepTimer,
            onStart = viewModel::setSleepTimer,
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimer = false },
        )
    }
    gearFor?.let { target ->
        MomentGearSheet(
            moments = moments,
            initialMomentId = target.takeIf { it != GEAR_FIRST },
            durationMs = state.durationMs,
            positionMs = position,
            settings = settings,
            onSave = viewModel::updateMoment,
            onDelete = { moment ->
                viewModel.deleteMoment(moment)
                if (moments.size <= 1) gearFor = null
            },
            onPreview = viewModel::previewMoment,
            onBoostsChange = viewModel::setBoosts,
            onDismiss = { gearFor = null },
        )
    }
}

@Composable
fun NowPlayingContent(
    state: PlayerUiState,
    positionMs: Long,
    moments: List<MomentEntity>,
    settings: MomentsSettings,
    justSavedMoment: Boolean,
    suggestion: MomentSuggestion?,
    linerNote: String?,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    onSaveMoment: () -> Unit,
    onSaveSuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onOpenGear: () -> Unit,
    onToggleChill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowPlaying = state.nowPlaying

    // When the user last went back: the cover slides backwards for a song change right after.
    var backAt by remember { mutableLongStateOf(0L) }
    val spark = MaterialTheme.colorScheme.tertiary

    // 0 → 1 as a Moment starts, back to 0 as it ends; every reaction follows this one value.
    val glow by LocalMomentGlow.current

    // Transparent: the theme's backdrop is the Now Playing background, brightened during a Moment.
    Surface(modifier = modifier.fillMaxSize().momentAura(glow, spark), color = Color.Transparent) {
        Column(
            // No systemBarsPadding here: the root Scaffold already pads its content for the bars.
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        painter = painterResource(R.drawable.ic_collapse),
                        contentDescription = stringResource(R.string.action_collapse),
                    )
                }
                IconButton(onClick = onToggleChill) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chill),
                        contentDescription =
                            stringResource(
                                if (settings.chill) R.string.chill_on else R.string.chill_off,
                            ),
                        tint = if (settings.chill) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }

            // Everything under the top row sits at the bottom; the cover takes what height is left,
            // up to a square as wide as the screen, so a long title, a liner note or a tall font
            // shrinks the cover instead of pushing the controls off the screen.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                // Square art. BoxWithConstraints gives the real size, so the thumbnail is requested
                // at display size instead of a guess. During a Moment it rises a little into a
                // heavenly light.
                Spacer(Modifier.height(16.dp))
                BoxWithConstraints(
                    modifier = Modifier.weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true),
                    contentAlignment = Alignment.Center,
                ) {
                    val coverSize = maxWidth
                    HeavenlyLight(glow = glow, color = spark, modifier = Modifier.requiredSize(coverSize * LIGHT_SCALE))
                    Box(
                        Modifier.graphicsLayer {
                            val scale = 1f + COVER_RISE * glow
                            scaleX = scale
                            scaleY = scale
                        },
                    ) {
                        SwipeCover(
                            nowPlaying = nowPlaying,
                            size = coverSize,
                            hasNext = state.hasNext,
                            hasPrevious = state.hasPreviousTrack,
                            backAt = { backAt },
                            onSwipeNext = onNext,
                            onSwipePrevious = {
                                backAt = SystemClock.uptimeMillis()
                                onPreviousTrack()
                            },
                        )
                    }
                    // The notes float over the bottom of the cover, so they never push the controls off a
                    // short screen.
                    Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = justSavedMoment,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) { SavedNote(onOpenGear) }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = suggestion != null && !justSavedMoment,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) { suggestion?.let { SuggestionNote(it, onSaveSuggestion, onDismissSuggestion) } }
                    }
                }

                Spacer(Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FieryTitle(
                            text = nowPlaying?.title.orEmpty(),
                            glow = glow,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.track_subtitle,
                                    nowPlaying?.artist ?: stringResource(R.string.unknown_artist),
                                    nowPlaying?.album ?: stringResource(R.string.unknown_album),
                                ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        // The liner note, quietly: a line of the user's own under the song.
                        AnimatedVisibility(visible = linerNote != null, enter = fadeIn(), exit = fadeOut()) {
                            Text(
                                text = linerNote.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LINER_NOTE_ALPHA),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                    IconButton(onClick = onSaveMoment, modifier = Modifier.size(56.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_moment),
                            contentDescription = stringResource(R.string.moment_save),
                            tint =
                                if (moments.isNotEmpty()) {
                                    MaterialTheme.colorScheme.moment
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                SeekBar(
                    positionMs = positionMs,
                    durationMs = state.durationMs,
                    moments = moments,
                    onSeek = onSeek,
                    modifier = Modifier.padding(top = 24.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeButton(
                        iconRes = R.drawable.ic_shuffle,
                        label =
                            stringResource(
                                if (state.shuffle) R.string.action_shuffle_on else R.string.action_shuffle_off,
                            ),
                        active = state.shuffle,
                        onClick = onToggleShuffle,
                    )
                    SkipButton(
                        next = false,
                        enabled = state.hasPrevious,
                        onClick = {
                            backAt = SystemClock.uptimeMillis()
                            onPrevious()
                        },
                        modifier = Modifier.size(64.dp),
                        iconSize = 32.dp,
                    )
                    PlayPauseButton(
                        playWhenReady = state.playWhenReady,
                        onClick = onTogglePlayPause,
                        filled = true,
                        iconSize = 36.dp,
                        modifier = Modifier.size(80.dp),
                    )
                    SkipButton(
                        next = true,
                        enabled = state.hasNext,
                        onClick = onNext,
                        modifier = Modifier.size(64.dp),
                        iconSize = 32.dp,
                    )
                    ModeButton(
                        iconRes =
                            if (state.repeatMode ==
                                Player.REPEAT_MODE_ONE
                            ) {
                                R.drawable.ic_repeat_one
                            } else {
                                R.drawable.ic_repeat
                            },
                        label =
                            stringResource(
                                when (state.repeatMode) {
                                    Player.REPEAT_MODE_ALL -> R.string.action_repeat_all
                                    Player.REPEAT_MODE_ONE -> R.string.action_repeat_one
                                    else -> R.string.action_repeat_off
                                },
                            ),
                        active = state.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = onCycleRepeat,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onOpenSleepTimer) {
                        Icon(
                            painterResource(R.drawable.ic_sleep_timer),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(
                            text = sleepTimerLabel(state.sleepTimer),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                    if (moments.isNotEmpty()) {
                        TextButton(onClick = onOpenGear) {
                            Icon(
                                painterResource(R.drawable.ic_moment_gear),
                                contentDescription = stringResource(R.string.moment_gear_title),
                                tint = MaterialTheme.colorScheme.moment,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Text(
                                text = moments.size.toString(),
                                color = MaterialTheme.colorScheme.moment,
                                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                            )
                        }
                    }
                    TextButton(onClick = onOpenQueue) {
                        Icon(
                            painterResource(R.drawable.ic_queue),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(
                            text = stringResource(R.string.action_queue),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                }
            }
        }
    }
}

/** Right after ⚡: the Moment is saved, and the lightning-gear is one tap away. */
@Composable
private fun SavedNote(onOpenGear: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Icon(
                painterResource(R.drawable.ic_moment),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.moment,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(R.string.moment_saved), modifier = Modifier.padding(start = 8.dp))
            TextButton(onClick = onOpenGear) {
                Icon(
                    painterResource(R.drawable.ic_moment_gear),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    stringResource(R.string.moment_adjust),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
    }
}

/** A section that keeps getting replayed: one tap makes it a Moment. */
@Composable
private fun SuggestionNote(
    suggestion: MomentSuggestion,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_moment),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.moment,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text =
                        stringResource(
                            R.string.moment_suggest,
                            formatMomentTime(suggestion.startMs).substringBefore('.'),
                        ),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.moment_suggest_dismiss)) }
                TextButton(onClick = onSave) { Text(stringResource(R.string.moment_suggest_save)) }
            }
        }
    }
}

/** Shuffle or repeat: tinted with the accent colour while on, its label says the current mode. */
@Composable
private fun ModeButton(
    @DrawableRes iconRes: Int,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Slider over the track, with the song's Moments marked under it in the accent. While
 * the user drags, it shows the drag position and the player keeps playing; the seek is sent
 * once, on release, instead of on every pixel.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    moments: List<MomentEntity>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1)
    val fraction = dragFraction ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val mark = MaterialTheme.colorScheme.moment.copy(alpha = MOMENT_MARK_ALPHA)

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * duration).toLong()) }
                dragFraction = null
            },
            enabled = durationMs > 0,
        )
        if (moments.isNotEmpty() && durationMs > 0) {
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                // Material's slider track is inset by half its thumb on each side.
                val inset = 10.dp.toPx()
                val track = size.width - inset * 2
                moments.forEach { m ->
                    val start = inset + track * (m.startMs.toFloat() / duration).coerceIn(0f, 1f)
                    val end = inset + track * (m.endMs.toFloat() / duration).coerceIn(0f, 1f)
                    drawRoundRect(
                        color = mark,
                        topLeft = Offset(start, 0f),
                        size = Size((end - start).coerceAtLeast(size.height), size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeLabel((fraction * duration).toLong())
            TimeLabel(durationMs)
        }
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(
        text = formatDuration(ms),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val SAVED_NOTE_MS = 6_000L

/** How much the cover rises during a Moment, and how far the light spills past it. */
private const val COVER_RISE = 0.07f
private const val LIGHT_SCALE = 1.45f

/** Liner notes sit back from the song's own text. */
private const val LINER_NOTE_ALPHA = 0.85f
