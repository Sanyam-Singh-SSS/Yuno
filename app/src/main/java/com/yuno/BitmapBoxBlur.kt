package com.yuno

import android.graphics.Bitmap

/** In-place box blur (Gaussian-ish), for tiny sampled frames — same recipe as Shorts glass. */
internal object BitmapBoxBlur {
    private var pixels = IntArray(0)
    private var spare = IntArray(0)

    fun apply(bitmap: Bitmap, radius: Int, passes: Int = 3) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0 || radius <= 0) return
        if (pixels.size != w * h) {
            pixels = IntArray(w * h)
            spare = IntArray(w * h)
        }
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(passes) {
            boxPass(pixels, spare, w, h, horizontal = true, radius)
            boxPass(spare, pixels, w, h, horizontal = false, radius)
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, horizontal: Boolean, radius: Int) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val n = 2 * radius + 1
        for (line in 0 until lines) {
            for (i in 0 until length) {
                var red = 0
                var green = 0
                var blue = 0
                for (k in -radius..radius) {
                    val j = (i + k).coerceIn(0, length - 1)
                    val c = src[if (horizontal) line * w + j else j * w + line]
                    red += (c shr 16) and 0xFF
                    green += (c shr 8) and 0xFF
                    blue += c and 0xFF
                }
                dst[if (horizontal) line * w + i else i * w + line] =
                    (0xFF shl 24) or ((red / n) shl 16) or ((green / n) shl 8) or (blue / n)
            }
        }
    }
}
