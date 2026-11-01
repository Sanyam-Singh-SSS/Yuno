package com.decio.skipii

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PULL_BIAS = 1.5f
private val PULL_LIFT_EASE = 72.dp
/** How far a slow swipe down must travel before it closes the video. A flick closes sooner. */
private val DISMISS_DISTANCE = 56.dp
private const val DISMISS_FLICK = 1400f

private fun pullLift(pulled: Float, ease: Float): Float =
    if (pulled <= 0f || ease <= 0f) 0f else 0.5f * 2f * ease * (sqrt(1f + pulled / ease) - 1f)

@Composable
fun Modifier.playerDockArt(dock: PlayerDock?, corner: Dp): Modifier {
    if (dock == null) return this
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    androidx.compose.runtime.DisposableEffect(dock) {
        onDispose { placed[0]?.let(dock::releaseMiniArt) }
    }
    return this
        .onPlaced { coordinates ->
            placed[0] = coordinates
            dock.reportMiniArt(coordinates, corner)
        }
        .graphicsLayer { alpha = if (dock.coversMiniArt) 0f else 1f }
}

fun Modifier.miniPlayerPullGestures(pull: MiniPlayerPull?, onDismiss: (() -> Unit)? = null): Modifier = composed {
    if (pull == null && onDismiss == null) return@composed this
    val currentPull by rememberUpdatedState(pull)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val pinMini by remember(pull) { derivedStateOf { pull?.pinsMini == true } }
    val scope = rememberCoroutineScope()
    val lift = remember { mutableFloatStateOf(0f) }
    val drop = remember { mutableFloatStateOf(0f) }
    val settle = remember { arrayOfNulls<Job>(1) }
    pointerInput(Unit) {
        val liftEase = PULL_LIFT_EASE.toPx()
        val dismissPx = DISMISS_DISTANCE.toPx()
        val maxVelocity = viewConfiguration.maximumFlingVelocity
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointer = down.id
            var start: PointerInputChange? = null
            var upward = false
            while (start == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                val moved = change.position - down.position
                if (moved.getDistance() < viewConfiguration.touchSlop) continue
                upward = moved.y < 0f && abs(moved.x) < -moved.y * PULL_BIAS
                val downward = moved.y > 0f && abs(moved.x) < moved.y * PULL_BIAS
                // Down closes the video. Up opens it. Anything else is a tap or a sideways scroll.
                if (!upward && !downward) return@awaitEachGesture
                if (downward && (currentDismiss == null || pinMini)) return@awaitEachGesture
                if (upward && currentPull == null) return@awaitEachGesture
                change.consume()
                start = change
            }
            val slopped = start ?: return@awaitEachGesture
            settle[0]?.cancel()
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            tracker.addPointerInputChange(slopped)
            if (!upward) {
                var dropped = (slopped.position.y - down.position.y).coerceAtLeast(0f)
                drop.floatValue = dropped
                val finished = drag(pointer) { change ->
                    val delta = change.positionChange().y
                    change.consume()
                    tracker.addPointerInputChange(change)
                    dropped = (dropped + delta).coerceAtLeast(0f)
                    drop.floatValue = dropped
                }
                val velocity = if (finished) tracker.calculateVelocity().y.coerceIn(-maxVelocity, maxVelocity) else 0f
                val dismiss = finished && (dropped >= dismissPx || velocity >= DISMISS_FLICK)
                val from = drop.floatValue
                val close = currentDismiss
                settle[0] = scope.launch {
                    if (dismiss && close != null) {
                        animate(
                            initialValue = from,
                            targetValue = from + dismissPx,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
                        ) { value, _ -> drop.floatValue = value }
                        close()
                        drop.floatValue = 0f
                    } else {
                        animate(
                            initialValue = from,
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                        ) { value, _ -> drop.floatValue = value }
                    }
                }
                return@awaitEachGesture
            }
            val target = currentPull ?: return@awaitEachGesture
            target.drag(slopped.position.y - down.position.y)
            var pulled = 0f
            val finished = drag(pointer) { change ->
                val delta = change.positionChange().y
                change.consume()
                tracker.addPointerInputChange(change)
                pulled -= delta
                target.drag(delta)
                lift.floatValue = pullLift(pulled, liftEase)
            }
            target.release(
                if (finished) tracker.calculateVelocity().y.coerceIn(-maxVelocity, maxVelocity) else 0f,
            )
            val from = lift.floatValue
            settle[0] = scope.launch {
                animate(
                    initialValue = from,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> lift.floatValue = value }
            }
        }
    }.graphicsLayer { translationY = if (pinMini) 0f else drop.floatValue - lift.floatValue }
}
