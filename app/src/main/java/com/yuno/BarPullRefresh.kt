package com.yuno

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pull-to-refresh that stretches the top bar instead of dropping a spinner.
 *
 * Home uses it. Another page can opt in the same way: wrap the screen in
 * [BarPullRefresh], turn off [PullToRefresh], and wrap its mark or title in
 * [BarPullReveal]. [PageTopBar] grows its top and bottom padding on its own
 * while [LocalBarPullRefresh] is set. While reloading, the mark fills and
 * empties with white until the new page lands.
 */
@Stable
class BarPullRefreshState {
    var offsetPx by mutableFloatStateOf(0f)
    var thresholdPx by mutableFloatStateOf(1f)

    /** -1 hidden. Otherwise 0..1: the first half fills the mark, the second half empties it. */
    var shimmer by mutableFloatStateOf(-1f)

    /** 0 at rest. Peaks at 1 for the little pop when a reload settles. */
    var jump by mutableFloatStateOf(0f)

    /** 0 at rest, 1 once the white fill has reached the far edge. */
    val fraction: Float
        get() = if (thresholdPx <= 0f) 0f else (offsetPx / thresholdPx).coerceIn(0f, 1f)

    /** Like [fraction], but keeps a little travel past the edge for the rubber band. */
    val stretch: Float
        get() = if (thresholdPx <= 0f) 0f else (offsetPx / thresholdPx).coerceIn(0f, BarPullRubber)

    fun extraPadding(): Dp = BarPullExtra * stretch

    fun markScale(): Float = (1f + BarPullGrow * stretch) * (1f + BarPullJump * jump)
}

val LocalBarPullRefresh = staticCompositionLocalOf<BarPullRefreshState?> { null }

/** How far the finger has to pull the bar before a release reloads. */
private val BarPullThreshold = 84.dp

/** Top and bottom padding added to the bar at the reload point. */
private val BarPullExtra = 24.dp

/** Wordmark / title growth at the reload point (1 + this). */
private const val BarPullGrow = 0.36f

/** Share of the pull at which the mark is already at 20% opacity. */
private const val BarPullFadeSpan = 0.22f

/** Extra travel after the fill completes, as a multiple of [BarPullThreshold]. */
private const val BarPullRubber = 1.22f

/** How much bigger the mark gets at the top of the settle pop. */
private const val BarPullJump = 0.065f

/** One fill or one unfill. */
private const val StrokeMs = 280f

/** Held after a completed fill, before the next unfill. */
private const val GapMs = 500f

/** fill, unfill, fill, then the half-second hold. */
private const val IntroMs = StrokeMs * 3 + GapMs

/** unfill, fill, then the half-second hold. Repeats after [IntroMs]. */
private const val LoopMs = StrokeMs * 2 + GapMs

/**
 * Opens with fill, unfill, fill. After that: unfill, fill, and a half-second
 * hold on the full mark, over and over. 0.5 draws as solid white.
 */
private fun markPhase(elapsed: Float): Float {
    if (elapsed < IntroMs) return burstPhase(elapsed, opening = true)
    return burstPhase((elapsed - IntroMs) % LoopMs, opening = false)
}

private fun burstPhase(t: Float, opening: Boolean): Float {
    val unfillAt = if (opening) StrokeMs else 0f
    val fillAt = if (opening) StrokeMs * 2 else StrokeMs
    val doneAt = if (opening) StrokeMs * 3 else StrokeMs * 2
    return when {
        opening && t < StrokeMs -> (t / StrokeMs) * 0.5f
        t < unfillAt + StrokeMs && t >= unfillAt -> 0.5f + ((t - unfillAt) / StrokeMs) * 0.5f
        t < doneAt -> ((t - fillAt) / StrokeMs) * 0.5f
        else -> 0.5f
    }
}

/** Time left until the mark lands on a completed fill. A hold already has. */
private fun playOutMs(elapsed: Float): Float {
    if (elapsed < IntroMs) return playOutIn(elapsed, opening = true)
    return playOutIn((elapsed - IntroMs) % LoopMs, opening = false)
}

private fun playOutIn(t: Float, opening: Boolean): Float {
    val unfillAt = if (opening) StrokeMs else 0f
    val fillAt = if (opening) StrokeMs * 2 else StrokeMs
    val doneAt = if (opening) StrokeMs * 3 else StrokeMs * 2
    return when {
        opening && t < StrokeMs -> StrokeMs - t
        t < unfillAt + StrokeMs && !(opening && t < StrokeMs) -> (unfillAt + StrokeMs - t) + StrokeMs
        t < doneAt -> doneAt - t
        else -> 0f
    }
}

@Composable
fun BarPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val state = remember(density) {
        BarPullRefreshState().apply {
            thresholdPx = with(density) { BarPullThreshold.toPx() }
        }
    }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val onRefreshState = rememberUpdatedState(onRefresh)
    val enabledState = rememberUpdatedState(enabled && !refreshing)
    val reducedState = rememberUpdatedState(reduced)
    val connection = remember(state, scope, view) {
        BarPullConnection(
            state = state,
            scope = scope,
            view = view,
            enabled = { enabledState.value },
            reduced = { reducedState.value },
            onRefresh = { onRefreshState.value.invoke() },
        )
    }
    val refreshingNow = rememberUpdatedState(refreshing)
    LaunchedEffect(reduced) {
        if (reduced) state.shimmer = -1f
        snapshotFlow { refreshingNow.value }.collect { active ->
            if (!active || reduced) return@collect
            val start = withFrameNanos { it }
            var elapsed = 0f
            // -1 until the reload has finished. Then the mark plays out to a completed fill.
            var stopAt = -1f
            while (isActive) {
                elapsed = (withFrameNanos { it } - start) / 1_000_000f
                val phase = markPhase(elapsed)
                if (refreshingNow.value) {
                    stopAt = -1f
                } else if (stopAt < 0f) {
                    // Mid-fill: finish this fill. Mid-unfill or in the pause: reach the next full fill.
                    stopAt = elapsed + playOutMs(elapsed)
                }
                if (stopAt >= 0f && elapsed >= stopAt) break
                state.shimmer = phase
            }
            state.shimmer = -1f
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (!reduced) {
                val pop = Animatable(0f)
                try {
                    pop.animateTo(1f, tween(70, easing = Motion.Emphasized)) { state.jump = value }
                    pop.animateTo(0f, spring(dampingRatio = 0.58f, stiffness = 1100f)) { state.jump = value }
                } finally {
                    state.jump = 0f
                }
            }
        }
    }
    CompositionLocalProvider(LocalBarPullRefresh provides state) {
        Box(Modifier.fillMaxSize().nestedScroll(connection)) { content() }
    }
}

/**
 * The mark or title that fills with white as the bar is pulled. At rest this is
 * just [content]. While pulling, the unfilled part fades to 20% and a white
 * copy covers from the left; once that copy reaches the right edge the mark is
 * fully opaque. After release the same fill runs again, then clears from the
 * left, and repeats until the reload finishes.
 */
@Composable
fun BarPullReveal(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = LocalBarPullRefresh.current
    if (state == null) {
        Box(modifier) { content() }
        return
    }
    val fraction = state.fraction
    val shimmer = state.shimmer
    val armed = fraction >= 0.999f && shimmer < 0f
    // Unfilled glyphs fall to 20% in the first part of the pull, then stay there
    // so the solid white reads against them. Covering the last of the mark leaves it fully opaque.
    val fade = when {
        shimmer >= 0f -> 0.2f
        armed -> 1f
        else -> 1f - 0.8f * (fraction / BarPullFadeSpan).coerceIn(0f, 1f)
    }
    val reloading = shimmer >= 0f
    Box(modifier) {
        Box(if (fade == 1f) Modifier else Modifier.graphicsLayer { alpha = fade }) { content() }
        if (reloading) {
            val filling = shimmer < 0.5f
            val t = if (filling) shimmer * 2f else (shimmer - 0.5f) * 2f
            WhiteCopy(
                shape = if (filling) LeftFraction(t) else RightClear(t),
                alpha = 1f,
                content = content,
            )
        } else if (fraction > 0f) {
            WhiteCopy(shape = LeftFraction(fraction), alpha = 1f, content = content)
        }
    }
}

/** Grows to the right. The left edge stays on the page gutter. */
internal fun Modifier.pullScale(scale: Float): Modifier =
    if (scale == 1f) this else graphicsLayer {
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0f, 0.5f)
    }

@Composable
private fun BoxScope.WhiteCopy(
    shape: Shape,
    alpha: Float,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .matchParentSize()
            .clearAndSetSemantics { }
            .graphicsLayer {
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(shape)
            .drawWithContent {
                drawContent()
                drawRect(Color.White, blendMode = BlendMode.SrcIn)
            },
    ) { content() }
}

private class BarPullConnection(
    private val state: BarPullRefreshState,
    private val scope: CoroutineScope,
    private val view: View,
    private val enabled: () -> Boolean,
    private val reduced: () -> Boolean,
    private val onRefresh: () -> Unit,
) : NestedScrollConnection {
    private var settle: Job? = null
    private var crossed = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || state.offsetPx <= 0f) return Offset.Zero
        settle?.cancel()
        val consume = available.y.coerceAtLeast(-state.offsetPx)
        state.offsetPx += consume
        if (state.offsetPx < state.thresholdPx) crossed = false
        return Offset(0f, consume)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f || !enabled()) return Offset.Zero
        settle?.cancel()
        val max = state.thresholdPx * BarPullRubber
        val resistance = if (state.offsetPx < state.thresholdPx) 0.66f else 0.28f
        val add = (available.y * resistance).coerceAtMost((max - state.offsetPx).coerceAtLeast(0f))
        val before = state.offsetPx
        state.offsetPx += add
        if (!crossed && before < state.thresholdPx && state.offsetPx >= state.thresholdPx) {
            crossed = true
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.offsetPx <= 0f) return Velocity.Zero
        val refresh = state.offsetPx >= state.thresholdPx
        settle?.cancel()
        settle = scope.launch {
            if (refresh) onRefresh()
            val from = state.offsetPx
            if (reduced() || from <= 0.5f) {
                state.offsetPx = 0f
            } else {
                val anim = Animatable(from)
                anim.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 420f)) {
                    state.offsetPx = value
                }
            }
            crossed = false
        }
        return Velocity(0f, available.y)
    }
}

private class LeftFraction(private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, 0f, size.width * fraction.coerceIn(0f, 1f), size.height))
}

/** White left behind as the clear edge travels left to right. */
private class RightClear(private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val left = size.width * fraction.coerceIn(0f, 1f)
        return Outline.Rectangle(Rect(left, 0f, size.width, size.height))
    }
}
