package com.workouttracker.app.ui.player

import kotlin.math.abs

enum class SwipeDirection { RIGHT, LEFT, UP }

/** What a drag currently means: the dominant direction and how far toward committing it is (0..1). */
data class SwipeReading(val direction: SwipeDirection?, val progress: Float) {
    val committed: Boolean get() = direction != null && progress >= 1f

    companion object {
        val NONE = SwipeReading(null, 0f)
    }
}

/** Pure drag maths, kept free of Compose types so it can be unit-tested on the JVM. */
object SwipeLogic {
    /** Horizontal commit distance as a percentage of the deck's width (same 30% as the web app). */
    const val SIDE_PERCENT = 30f

    /** Upward commit distance never exceeds this share of the deck's height. */
    const val UP_FRACTION = 0.25f

    /** How much of a drag in a direction with no action is shown, so the card feels "stuck". */
    const val RESISTANCE = 0.2f

    fun upThreshold(preferredPx: Float, heightPx: Float): Float =
        minOf(preferredPx, heightPx * UP_FRACTION).coerceAtLeast(1f)

    /**
     * The dominant direction of a drag. Only directions in [available] count; a drag mostly
     * downwards, or towards an unavailable direction, reads as nothing.
     */
    fun reading(dx: Float, dy: Float, widthPx: Float, upThresholdPx: Float, available: Set<SwipeDirection>): SwipeReading {
        val direction = when {
            dx == 0f && dy == 0f -> null
            abs(dx) >= abs(dy) -> if (dx > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
            dy < 0f -> SwipeDirection.UP
            else -> null
        }
        if (direction == null || direction !in available) return SwipeReading.NONE
        // Percent arithmetic: 400 * 0.3f is 120.0000047, which would make an exact-threshold drag fall short.
        val side = (widthPx * SIDE_PERCENT / 100f).coerceAtLeast(1f)
        val progress = when (direction) {
            SwipeDirection.RIGHT -> dx / side
            SwipeDirection.LEFT -> -dx / side
            SwipeDirection.UP -> -dy / upThresholdPx
        }
        return SwipeReading(direction, progress.coerceIn(0f, 1f))
    }

    /** Where the card is drawn for a finger offset: free towards available directions, resisted elsewhere. */
    fun displace(dx: Float, dy: Float, available: Set<SwipeDirection>): Pair<Float, Float> {
        val x = when {
            dx > 0f -> if (SwipeDirection.RIGHT in available) dx else dx * RESISTANCE
            dx < 0f -> if (SwipeDirection.LEFT in available) dx else dx * RESISTANCE
            else -> 0f
        }
        val y = when {
            dy < 0f -> if (SwipeDirection.UP in available) dy else dy * RESISTANCE
            else -> dy * RESISTANCE
        }
        return x to y
    }
}
