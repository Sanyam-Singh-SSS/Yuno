package com.yuno

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The app's single shared video surface (see YunoApp). */
val LocalVideoSurface = androidx.compose.runtime.staticCompositionLocalOf<@Composable (Modifier) -> Unit> { { m -> VideoSurface(null, m) } }

/** Draws nothing: where the video must not show (picture-in-picture shows it elsewhere). */
val NoVideoSurface: @Composable (Modifier) -> Unit = {}

/**
 * The one surface the decoder draws into, kept alive for the whole process.
 *
 * Why: a TextureView destroys its SurfaceTexture whenever it leaves the window (moving it
 * from the full player to the mini-player does exactly that). The decoder then loses its
 * output, is rebuilt, and waits for the next keyframe — seconds of frozen picture, the
 * "flash" when minimizing. Here the SurfaceTexture is never released (the destroy callback
 * returns false) and is handed back to whichever TextureView shows the video next, so the
 * player's surface never changes and playback never notices a move.
 */
object SharedVideoTexture {
    var texture: SurfaceTexture? = null
        private set
    var surface: Surface? = null
        private set
    /** Bumped when the surface first exists, so players can be pointed at it. */
    var version by mutableIntStateOf(0)
        private set
    private var host: TextureView? = null

    fun attach(view: TextureView) { host = view }

    fun detach(view: TextureView) { if (host === view) host = null }

    /** The frame on screen right now, so a handoff can keep drawing it instead of the poster. */
    fun captureFrame() {
        val view = host ?: return
        if (!view.isAvailable || view.width < 2 || view.height < 2) return
        val edge = max(view.width, view.height)
        val scale = min(1f, 1080f / edge)
        val w = (view.width * scale).roundToInt().coerceAtLeast(1)
        val h = (view.height * scale).roundToInt().coerceAtLeast(1)
        val shot = runCatching { view.getBitmap(w, h) }.getOrNull() ?: return
        HeldFrame.image = shot.asImageBitmap()
    }

    /**
     * Top [statusBarPx] of the picture (in view coords). Readback width matches [captureFrame]
     * (up to 240 on the long edge) so the strip is not over-read from the texture.
     */
    fun captureStatusStrip(statusBarPx: Int): android.graphics.Bitmap? {
        val view = host ?: return null
        if (!view.isAvailable || view.width < 2 || view.height < 2 || statusBarPx <= 0) return null
        val edge = max(view.width, view.height)
        val scale = min(1f, 240f / edge)
        val sampleWidth = (view.width * scale).roundToInt().coerceAtLeast(1)
        val fullH = (sampleWidth.toLong() * view.height / view.width).toInt().coerceAtLeast(1)
        val full = runCatching { view.getBitmap(sampleWidth, fullH) }.getOrNull() ?: return null
        val bandH = (fullH.toLong() * statusBarPx / view.height).toInt().coerceIn(1, fullH)
        val strip = android.graphics.Bitmap.createBitmap(full, 0, 0, sampleWidth, bandH)
        if (full !== strip) full.recycle()
        val matrix = Matrix().apply { postScale(1f, -1f, strip.width / 2f, bandH / 2f) }
        return android.graphics.Bitmap.createBitmap(strip, 0, 0, strip.width, bandH, matrix, true).also {
            if (it !== strip) strip.recycle()
        }
    }

    fun adopt(view: TextureView, fresh: SurfaceTexture) {
        val kept = texture
        if (kept == null) {
            texture = fresh
            surface = Surface(fresh)
            version++
        } else if (kept !== fresh) {
            // The view made a new one on re-attach: swap ours back in (releases the new one).
            runCatching { view.setSurfaceTexture(kept) }
                .onFailure { Log.w("Yuno", "couldn't re-attach the video texture", it) }
        }
    }
}

/**
 * The video, in a TextureView this app owns. All TextureViews show [SharedVideoTexture];
 * the player's output surface is set once and never cleared while the app runs.
 */
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                SharedVideoTexture.attach(this)
                // A view created after the texture exists shows it straight away.
                SharedVideoTexture.texture?.let { runCatching { setSurfaceTexture(it) } }
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) = SharedVideoTexture.adopt(this@apply, st)
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                    // Never let the view release it: the next view (or this one, re-attached) reuses it.
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = st !== SharedVideoTexture.texture
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                        AvSync.onFrameDrawn()
                        VideoTopSampler.onVideoFrame()
                    }
                }
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        SharedVideoTexture.attach(this@apply)
                        // Before the hardware layer is built, so it adopts the kept texture.
                        val kept = SharedVideoTexture.texture ?: return
                        if (surfaceTexture !== kept) runCatching { setSurfaceTexture(kept) }
                    }
                    override fun onViewDetachedFromWindow(v: View) { SharedVideoTexture.detach(this@apply) }
                })
            }
        },
    )
    val version = SharedVideoTexture.version
    LaunchedEffect(player, version) {
        val surface = SharedVideoTexture.surface ?: return@LaunchedEffect
        player?.setVideoSurface(surface)
    }
}

/** The last real video frame. Drawn under the surface so a move never falls back to the poster. */
object HeldFrame {
    var image by mutableStateOf<ImageBitmap?>(null)
}

@Composable
fun HeldFrameImage(modifier: Modifier = Modifier) {
    val image = HeldFrame.image ?: return
    Image(bitmap = image, contentDescription = null, modifier = modifier, contentScale = ContentScale.FillBounds)
}
