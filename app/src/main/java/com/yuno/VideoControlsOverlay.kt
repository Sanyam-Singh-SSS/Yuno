package com.yuno

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Controls dim on the full video ([VideoContent]) and a flat wash on the status-bar blur. */
object VideoControlsOverlay {
    val scrim = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.5f),
        0.32f to Color.Black.copy(alpha = 0.08f),
        0.58f to Color.Black.copy(alpha = 0.08f),
        1f to Color.Black.copy(alpha = 0.62f),
    )

    /** Flat dim on the status-bar blur — same strength as the top of [scrim], not the gradient. */
    val statusBarDimColor = Color.Black.copy(alpha = 0.5f)

    /** 0 hidden, 1 fully shown — set from [VideoContent] during composition; read in [VideoTopStatusBlur]. */
    var dimAlpha by mutableFloatStateOf(0f)
}
