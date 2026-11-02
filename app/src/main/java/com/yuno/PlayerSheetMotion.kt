package com.yuno

import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.WindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private const val SLIDE_STIFFNESS = 195f
private const val SLIDE_SETTLED_PX = 0.5f
private const val SHEET_VELOCITY_FRESH_MS = 80f
private const val LAYOUT_WAIT_MS = 300L
private const val WINDOW_ENTER_MS = 600L

/** Pull the mini player up into the full player sheet. */
interface MiniPlayerPull {
    fun drag(delta: Float)
    fun release(velocity: Float)

    /**
     * The sheet is carrying the picture. The mini player must stay on its
     * layout slot — a visual lift sits above that slot, and the flying frame
     * then appears just below the video.
     */
    val pinsMini: Boolean
}

@OptIn(ExperimentalMaterial3Api::class)
@Stable
class PlayerSheetMotion(
    private val scope: CoroutineScope,
    private val dock: PlayerDock,
    private val shown: () -> Boolean,
    private val setShown: (Boolean) -> Unit,
) : MiniPlayerPull {
    var held by mutableFloatStateOf(Float.NaN)
        private set

    val holding: Boolean get() = !held.isNaN()
    override val pinsMini: Boolean get() = dock.sheetUp
    var sheet: SheetState? = null
    private var window: Window? = null
    private var windowAnimations = 0
    private var windowRestore: Job? = null
    private var pullStartNanos = 0L
    private var pullStartAt = 0f
    private var sheetSampleAt = Float.NaN
    private var sheetSampleNanos = 0L
    private var sheetVelocity = 0f
    var laidOut by mutableStateOf(false)
    /** The frame after the first layout, so the offset on screen is the corrected one. */
    var placed by mutableStateOf(false)
        private set
    var windowInfo: WindowInfo? = null
    var flingVelocity = 0f
    private var settle: Job? = null
    private var closing = false

    private fun travel(): Float =
        dock.sheetTravel.takeIf { it > 0f } ?: (windowInfo?.containerSize?.height ?: 0).toFloat()

    private fun sheetOffset(): Float = try {
        sheet?.requireOffset() ?: Float.NaN
    } catch (_: IllegalStateException) {
        Float.NaN
    }

    fun position(): Float = when {
        holding -> held
        laidOut && dock.sheetTravel > 0f && shown() -> 0f
        else -> sheetOffset()
    }

    /**
     * False until the sheet has a real height. Until then [contentOffsetPx]
     * stays put and the content is not drawn: an unknown sheet position used
     * to be treated as the top, which shoved the page down by a full screen.
     */
    fun contentReady(): Boolean {
        if (!placed || !laidOut || dock.sheetTravel <= 0f) return false
        if (sheet != null && holding && sheetOffset().isNaN()) return false
        return true
    }

    fun contentOffsetPx(): Int {
        val target = held
        val sheetAt = sheetOffset()
        if (target.isNaN()) {
            if (!sheetAt.isNaN()) sampleSheet(sheetAt)
            return 0
        }
        // Still the Material sheet: its own offset is not the top just because
        // it has not been measured. The content stays hidden via [contentReady].
        if (sheet != null) {
            if (sheetAt.isNaN()) return 0
            return target.toInt() - sheetAt.toInt()
        }
        return target.toInt()
    }

    /** First real height. A pull that started against a guessed travel is moved by the difference. */
    fun noteLaidOut(height: Float) {
        if (height <= 0f) return
        if (holding) {
            val previous = dock.sheetTravel
            if (previous <= 0f) {
                val guess = (windowInfo?.containerSize?.height ?: 0).toFloat()
                if (guess > 0f) held = (height + (held - guess)).coerceIn(0f, height)
            } else if (!laidOut || abs(previous - height) > 0.5f) {
                held = (height + (held - previous)).coerceIn(0f, height)
            }
        }
        dock.sheetTravel = height
        laidOut = true
    }

    fun markPlaced() {
        placed = true
    }

    private fun sampleSheet(at: Float) {
        val now = System.nanoTime()
        if (!sheetSampleAt.isNaN()) {
            val seconds = (now - sheetSampleNanos) / 1e9f
            if (seconds > 0f) {
                val instant = (at - sheetSampleAt) / seconds
                sheetVelocity = sheetVelocity * 0.4f + instant * 0.6f
            }
        }
        sheetSampleAt = at
        sheetSampleNanos = now
    }

    private fun recentSheetVelocity(): Float =
        if ((System.nanoTime() - sheetSampleNanos) / 1e6f < SHEET_VELOCITY_FRESH_MS) sheetVelocity else 0f

    fun open() {
        if (shown()) {
            if (closing && holding) {
                settle?.cancel()
                reopening()
                val from = held
                settle = scope.launch {
                    slide(from = from, to = 0f)
                    held = Float.NaN
                }
            }
            return
        }
        settle?.cancel()
        closing = false
        val travel = travel()
        held = travel
        dock.sheetUp = true
        dock.fadeArmed = false
        SharedVideoTexture.captureFrame()
        setShown(true)
        settle = scope.launch {
            withTimeoutOrNull(LAYOUT_WAIT_MS) { snapshotFlow { laidOut }.first { it } }
            slide(from = if (holding) held else travel(), to = 0f)
            held = Float.NaN
        }
    }

    fun attachWindow(window: Window?) {
        this.window = window ?: return
        windowAnimations = window.attributes.windowAnimations
        window.setWindowAnimations(0)
        windowRestore?.cancel()
        windowRestore = scope.launch {
            delay(WINDOW_ENTER_MS)
            window.setWindowAnimations(windowAnimations)
        }
    }

    private fun quietWindow() {
        windowRestore?.cancel()
        window?.setWindowAnimations(0)
    }

    private fun passTouchesThrough(pass: Boolean) {
        val window = window ?: return
        if (pass) window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun reopening() {
        closing = false
        passTouchesThrough(false)
        window?.setWindowAnimations(windowAnimations)
    }

    fun close() {
        if (!shown() || closing) return
        closing = true
        quietWindow()
        passTouchesThrough(true)
        SharedVideoTexture.captureFrame()
        settle?.cancel()
        val from = position().takeUnless { it.isNaN() } ?: run {
            dismissQuietly()
            return
        }
        val velocity = if (holding) 0f else recentSheetVelocity()
        held = from
        settle = scope.launch {
            slide(from = from, to = travel(), velocity = velocity.coerceAtLeast(0f))
            dismissQuietly()
        }
    }

    override fun drag(delta: Float) {
        if (!holding) {
            if (!shown()) {
                // Pull up from the mini player. A downward drag has nothing to open.
                if (delta >= 0f) return
                settle?.cancel()
                closing = false
                held = travel()
                pullStartAt = held
                pullStartNanos = System.nanoTime()
                dock.sheetUp = true
                dock.fadeArmed = false
                SharedVideoTexture.captureFrame()
                setShown(true)
            } else {
                // Already open: follow the finger down. Upward is the fullscreen pull.
                if (delta <= 0f) return
                settle?.cancel()
                closing = false
                val from = position().takeUnless { it.isNaN() } ?: 0f
                held = from
                pullStartAt = from
                pullStartNanos = System.nanoTime()
                SharedVideoTexture.captureFrame()
            }
        }
        settle?.cancel()
        held = (held + delta).coerceIn(0f, travel())
    }

    override fun release(velocity: Float) {
        if (!holding) return
        val travel = travel()
        val seconds = (System.nanoTime() - pullStartNanos) / 1e9f
        val average = if (seconds > 0f) (held - pullStartAt) / seconds else 0f
        val speed = if (abs(average) > abs(velocity)) average else velocity
        val flick = abs(speed) >= flingVelocity
        val open = if (flick) speed < 0f else held < travel / 2f
        if (!open) {
            closing = true
            quietWindow()
            passTouchesThrough(true)
        } else if (closing) {
            reopening()
        }
        val from = held
        settle = scope.launch {
            slide(from = from, to = if (open) 0f else travel, velocity = speed)
            if (open) held = Float.NaN else dismissQuietly()
        }
    }

    fun onSheetGone() {
        closing = false
        sheetSampleAt = Float.NaN
        sheetVelocity = 0f
        settle?.cancel()
        windowRestore?.cancel()
        held = Float.NaN
        dock.sheetUp = false
        dock.resetPose()
        laidOut = false
        placed = false
        sheet = null
        window = null
    }

    private suspend fun slide(from: Float, to: Float, velocity: Float = 0f) {
        animate(
            initialValue = from,
            targetValue = to,
            initialVelocity = velocity,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = SLIDE_STIFFNESS,
                visibilityThreshold = SLIDE_SETTLED_PX,
            ),
        ) { value, _ ->
            held = value.coerceIn(0f, travel())
        }
    }

    private fun dismissQuietly() {
        quietWindow()
        SharedVideoTexture.captureFrame()
        dock.sheetUp = false
        setShown(false)
    }
}
