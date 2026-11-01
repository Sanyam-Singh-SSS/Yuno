package com.decio.skipii

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/** How far the sheet rises before the page starts to appear. */
internal const val PAGE_FADE_LEAD = 0.15f

/**
 * The mini player the expanded player docks into (BitChord [PlayerDock]).
 * Screen-space rects bridge the modal sheet and the bar beneath it.
 */
@Stable
class PlayerDock {
    private var miniArt: LayoutCoordinates? = null
    private var miniArtCorner: Dp = MiniThumbRadius

    var sheetOffset: (() -> Float)? by mutableStateOf(null)
    var sheetTravel by mutableFloatStateOf(0f)
    var attached by mutableStateOf(false)
        internal set

    /** The expanded player is on screen. The slot stays up until the flying picture covers it. */
    var sheetUp by mutableStateOf(false)

    /**
     * A dock measurement taken after the sheet was actually placed. The first
     * one is still the window's unsettled position, and drawing it puts a
     * second copy of the frame just under the mini player.
     */
    var poseReady by mutableStateOf(false)
        private set

    /**
     * The page may draw only after the sheet has actually been at the bottom.
     * A first frame that thinks it is fully open otherwise paints the solid
     * page down there and then snaps it clear.
     */
    var fadeArmed = false

    fun reportMiniArt(coordinates: LayoutCoordinates, corner: Dp) {
        miniArt = coordinates
        miniArtCorner = corner
    }

    fun releaseMiniArt(coordinates: LayoutCoordinates) {
        if (miniArt === coordinates) miniArt = null
    }

    internal fun hasMiniArt(): Boolean = miniArt?.isAttached == true

    internal fun miniArtOnScreen(): Rect? {
        val coordinates = miniArt?.takeIf { it.isAttached } ?: return null
        val size = coordinates.size.toSize()
        return Rect(
            coordinates.localToScreen(Offset.Zero),
            coordinates.localToScreen(Offset(size.width, size.height)),
        )
    }

    internal fun miniArtCorner(): Dp = miniArtCorner

    fun notePose() {
        poseReady = true
    }

    fun resetPose() {
        poseReady = false
    }

    internal fun offset(): Float? = sheetOffset?.invoke()?.takeUnless { it.isNaN() }

    fun openFraction(): Float {
        val travel = sheetTravel
        val offset = offset() ?: return 0f
        if (travel <= 0f) return 0f
        return (1f - offset / travel).coerceIn(0f, 1f)
    }

    /** 0 while the page should stay clear, 1 when the player is fully open. */
    fun pageAlpha(holding: Boolean): Float {
        // No travel or no position yet. That 0 is "not ready", not "at the bottom",
        // and must not arm the fade — the next frame would paint the solid page.
        if (sheetTravel <= 0f) return 0f
        val at = offset() ?: return 0f
        val open = (1f - at / sheetTravel).coerceIn(0f, 1f)
        if (open < PAGE_FADE_LEAD) fadeArmed = true
        if (!holding && open >= 1f) fadeArmed = true
        if (!fadeArmed) return 0f
        if (open >= 1f) return 1f
        return ((open - PAGE_FADE_LEAD) / (1f - PAGE_FADE_LEAD)).coerceIn(0f, 1f)
    }

    val coversMiniArt: Boolean
        get() = coversMiniArtState.value

    private val coversMiniArtState = derivedStateOf {
        sheetUp && poseReady && openFraction() > 0f
    }
}

val LocalPlayerDock = staticCompositionLocalOf<PlayerDock?> { null }
