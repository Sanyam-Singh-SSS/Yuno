package com.decio.skipii

/**
 * Smootherstep, `6t⁵ − 15t⁴ + 10t³`, for a fade across `t` in 0..1.
 */
fun smootherStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

/** Stops for a gradient that follows [smootherStep]. */
const val FADE_CURVE_STOPS = 16
