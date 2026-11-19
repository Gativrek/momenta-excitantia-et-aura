package io.github.gativrek.momenta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.ui.theme.drawLogo
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app coming in when it starts. The bars and the mini-player fade in at once; then, as
 * soon as the list has loaded, its rows fly up from the bottom of the screen into place, the
 * top one first and the rest just behind, stacking up into the list (see [introFlyUp]).
 *
 * Meanwhile the logo from the launch screen, [launchLogo] (its canvas in window pixels, handed
 * over as the launch screen goes; [hasLaunchScreen] says whether one is coming), glides from
 * where the system left it to its place in the title bar beside the tab's name
 * ([introLogoTarget]), turning from the launcher's colors to the theme's on the way, while the
 * launch screen's plain navy melts into the theme's background.
 *
 * Plays once per launch: not again on rotation or when coming back to the app. Only drawing
 * changes while it plays, and touches work straight away.
 */
@Composable
fun StartupIntro(
    launchLogo: Rect?,
    hasLaunchScreen: Boolean,
    content: @Composable () -> Unit,
) {
    var played by rememberSaveable { mutableStateOf(false) }
    val intro = remember { Intro(done = played) }
    val from by rememberUpdatedState(launchLogo)
    val fade = remember { Animatable(if (played) 1f else 0f) }
    val flight = remember { Animatable(0f) }
    val veil = remember { Animatable(if (played || !hasLaunchScreen) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        launch { fade.animateTo(1f, tween(FADE_MS)) }
        launch {
            // Where it starts arrives as the launch screen goes; where it lands, once the title
            // bar has been laid out. Without either the title bar's logo simply shows.
            val ready =
                hasLaunchScreen &&
                    withTimeoutOrNull(MAX_WAIT_MS) {
                        snapshotFlow { from }.filterNotNull().first()
                        snapshotFlow { intro.logoTarget }.filterNotNull().first()
                    } != null
            if (ready) {
                launch { veil.animateTo(0f, tween(LOGO_FLIGHT_MS)) }
                flight.animateTo(1f, tween(LOGO_FLIGHT_MS, easing = LOGO_EASING))
            } else {
                veil.animateTo(0f, tween(FADE_MS))
            }
            intro.logoLanded = true
        }
        withTimeoutOrNull(MAX_WAIT_MS) {
            // A couple of frames for the first screen to compose (and say it's loading, if it
            // is), then until nothing is loading, then a couple more to lay out what loaded.
            repeat(2) { withFrameNanos { } }
            snapshotFlow { intro.loading }.first { it == 0 }
            repeat(2) { withFrameNanos { } }
        }
        val start = withFrameNanos { it }
        while (intro.elapsedMs < TOTAL_MS) {
            withFrameNanos { intro.elapsedMs = (it - start) / 1_000_000 }
        }
        intro.done = true
        played = true
    }
    val navy = colorResource(R.color.window_background)
    Box(modifier = Modifier.fillMaxSize()) {
        // The launch screen's background, over the theme's, fading out as the logo flies.
        Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(navy, alpha = veil.value) })
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
            CompositionLocalProvider(LocalIntro provides intro, content)
        }
        val start = from
        if (start != null && !intro.logoLanded) FlyingLogo(start, intro.logoTarget) { flight.value }
    }
}

/** The launch-screen logo on its way to the title bar, [progress] 0 → 1. */
@Composable
private fun FlyingLogo(
    from: Rect,
    to: Rect?,
    progress: () -> Float,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(Modifier.fillMaxSize()) {
        val p = progress()
        val r = if (to == null) from else lerp(from, to, p)
        translate(r.left, r.top) {
            drawLogo(
                size = r.width,
                accent = lerp(LAUNCH_ACCENT, accent, p),
                ink = lerp(LAUNCH_INK, ink, p),
            )
        }
    }
}

/**
 * Where the launch logo lands: the logo in the title bar. Hidden until it has, so the two
 * never show at once.
 */
fun Modifier.introLogoTarget(): Modifier =
    composed {
        val intro = LocalIntro.current ?: return@composed this
        onGloballyPositioned { intro.logoTarget = it.boundsInRoot() }
            .graphicsLayer { alpha = if (intro.logoLanded) 1f else 0f }
    }

/**
 * A list row (or a list's header) taking part in the startup intro: it flies up from the
 * bottom of the screen to its place. Rows higher up set off first and travel further, so the
 * list builds from the top down while every row comes up from below. Once the intro has
 * played this is a plain modifier.
 */
fun Modifier.introFlyUp(): Modifier =
    composed {
        val intro = LocalIntro.current
        if (intro == null || intro.done) return@composed this
        val screenHeight =
            LocalWindowInfo.current.containerSize.height
                .toFloat()
        val overshoot = with(LocalDensity.current) { FLY_EXTRA.toPx() }
        var top by remember { mutableFloatStateOf(Float.NaN) }
        onGloballyPositioned { top = it.positionInRoot().y }
            .graphicsLayer {
                val elapsed = intro.elapsedMs
                if (elapsed < 0 || top.isNaN() || screenHeight <= 0f) {
                    alpha = 0f
                    return@graphicsLayer
                }
                val delay = (top / screenHeight).coerceIn(0f, 1f) * SPREAD_MS
                val t = ((elapsed - delay) / FLY_MS).coerceIn(0f, 1f)
                translationY = (1f - FLY_EASING.transform(t)) * (screenHeight - top + overshoot)
                alpha = (t * FADE_IN_SPEED).coerceAtMost(1f)
            }
    }

/** True while the startup intro is waiting for the list: a loading spinner stays hidden. */
@Composable
fun introPending(): Boolean = LocalIntro.current?.let { !it.done && it.elapsedMs < 0 } ?: false

/** Call while a screen is loading: the startup intro waits until it's gone (or gives up). */
@Composable
fun HoldStartupIntro() {
    val intro = LocalIntro.current ?: return
    DisposableEffect(intro) {
        intro.loading++
        onDispose { intro.loading-- }
    }
}

private class Intro(
    done: Boolean,
) {
    /** How many screens are still loading what they'll show. */
    var loading by mutableIntStateOf(0)

    /** Time since the rows set off, or -1 while waiting. Read while drawing. */
    var elapsedMs by mutableLongStateOf(-1L)

    var done by mutableStateOf(done)

    /** The title bar's logo, in window pixels, once laid out. */
    var logoTarget by mutableStateOf<Rect?>(null)

    var logoLanded by mutableStateOf(done)
}

private val LocalIntro = staticCompositionLocalOf<Intro?> { null }

/** The bars fading in, at launch. */
private const val FADE_MS = 350

/** Longest wait for the list to load before the intro plays anyway. */
private const val MAX_WAIT_MS = 2_000L

/** How long one row takes to fly into place. */
private const val FLY_MS = 700f

/** The last row on screen sets off this long after the first. */
private const val SPREAD_MS = 420f

private const val TOTAL_MS = (FLY_MS + SPREAD_MS).toLong() + 50

/** Rows start a little below the screen's edge. */
private val FLY_EXTRA = 24.dp

/** A row is fully opaque a third of the way into its flight. */
private const val FADE_IN_SPEED = 3f

/** The logo's glide from the middle of the screen to the title bar. */
private const val LOGO_FLIGHT_MS = 800
private val LOGO_EASING = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)

/** The launcher icon's colors (design/logo/logo.svg), where the flying logo starts. */
private val LAUNCH_ACCENT = Color(0xFFF2C14E)
private val LAUNCH_INK = Color(0xFFF4EFE6)

/** Fast off the mark, a long soft landing. */
private val FLY_EASING = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
