package com.yuno

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** TEMP: set true to show the raw cropped strip with no blur. */
internal const val VIDEO_TOP_DEBUG_RAW_STRIP = false

private const val BOX_BLUR_RADIUS = 3
private const val BOX_BLUR_PASSES = 2
private val ComposeBlurRadius = 12.dp

/**
 * Live blurred copy of the video's top edge for the open player's status-bar region.
 */
object VideoTopSampler {
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set
    var topLuminance by mutableFloatStateOf(0f)
        private set
    /** Status-bar height in px — strip crop matches this, not a fraction of the whole frame. */
    var statusBarPx by mutableIntStateOf(0)
    private var running = false
    private var scratch: Bitmap? = null

    fun start() {
        running = true
    }

    fun stop() {
        running = false
    }

    /** Called on every main-player frame ([TextureView.onSurfaceTextureUpdated]). */
    fun onVideoFrame() {
        if (!running || statusBarPx <= 0) return
        tick()
    }

    private fun tick() {
        val barPx = statusBarPx
        if (barPx <= 0) return
        val fresh = SharedVideoTexture.captureStatusStrip(barPx) ?: return
        val target = scratch?.takeIf { it.width == fresh.width && it.height == fresh.height }
            ?: Bitmap.createBitmap(fresh.width, fresh.height, Bitmap.Config.ARGB_8888).also { scratch = it }
        Canvas(target).drawBitmap(fresh, 0f, 0f, null)
        fresh.recycle()
        topLuminance = averageLuminance(target)
        if (!VIDEO_TOP_DEBUG_RAW_STRIP) {
            BitmapBoxBlur.apply(target, BOX_BLUR_RADIUS, BOX_BLUR_PASSES)
        }
        frame = target.asImageBitmap()
    }
}

private fun averageLuminance(bitmap: Bitmap): Float {
    val w = bitmap.width
    val h = bitmap.height
    val n = w * h
    if (n == 0) return 0f
    val row = IntArray(w)
    var sum = 0f
    for (y in 0 until h) {
        bitmap.getPixels(row, 0, w, 0, y, w, 1)
        for (x in 0 until w) {
            val c = row[x]
            val r = (c shr 16 and 0xFF) / 255f
            val g = (c shr 8 and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
    }
    return sum / n
}

/** Blurred duplicate of the video top, drawn only in the status-bar strip. */
@Composable
internal fun VideoTopStatusBlur(
    height: Dp,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val bitmap = VideoTopSampler.frame
    val controlsDim = VideoControlsOverlay.dimAlpha
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { this.alpha = alpha() },
    ) {
        if (bitmap != null) {
            val blurMod = when {
                VIDEO_TOP_DEBUG_RAW_STRIP -> Modifier
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    Modifier.blur(ComposeBlurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                else -> Modifier
            }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().then(blurMod),
            )
            if (controlsDim > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = controlsDim }
                        .background(VideoControlsOverlay.statusBarDimColor),
                )
            }
        }
    }
}

/** White status icons on a dark top; dark icons when the frame top is already bright. */
internal fun videoStatusUsesLightIcons(topLuminance: Float): Boolean = topLuminance < 0.42f
