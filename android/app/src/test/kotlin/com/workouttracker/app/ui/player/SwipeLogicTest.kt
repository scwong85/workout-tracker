package com.workouttracker.app.ui.player

import com.workouttracker.app.ui.player.SwipeDirection.LEFT
import com.workouttracker.app.ui.player.SwipeDirection.RIGHT
import com.workouttracker.app.ui.player.SwipeDirection.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeLogicTest {
    private val all = setOf(RIGHT, LEFT, UP)
    private val width = 400f
    private val up = 120f

    private fun read(dx: Float, dy: Float, available: Set<SwipeDirection> = all) =
        SwipeLogic.reading(dx, dy, width, up, available)

    @Test fun rightCommitsAtThirtyPercentOfWidth() {
        assertFalse(read(119f, 0f).committed)
        assertTrue(read(120f, 0f).committed)
        assertEquals(RIGHT, read(200f, 10f).direction)
    }

    @Test fun leftAndUpCommit() {
        assertTrue(read(-130f, 5f).committed)
        assertEquals(LEFT, read(-130f, 5f).direction)
        assertTrue(read(10f, -125f).committed)
        assertEquals(UP, read(10f, -125f).direction)
    }

    @Test fun progressGrowsWithDistanceAndIsClamped() {
        assertEquals(0.5f, read(60f, 0f).progress, 1e-4f)
        assertEquals(1f, read(900f, 0f).progress, 0f)
        assertEquals(0f, read(0f, 0f).progress, 0f)
    }

    @Test fun dominantAxisWins() {
        // Mostly up with a bit of right: counts as up, not right.
        assertEquals(UP, read(100f, -140f).direction)
        // Mostly right with a bit of up: counts as right.
        assertEquals(RIGHT, read(150f, -100f).direction)
    }

    @Test fun downwardDragReadsAsNothing() {
        assertNull(read(0f, 300f).direction)
        assertNull(read(10f, 200f).direction)
    }

    @Test fun unavailableDirectionReadsAsNothing() {
        val noLeft = setOf(RIGHT, UP)
        assertNull(read(-300f, 0f, noLeft).direction)
        assertFalse(read(-300f, 0f, noLeft).committed)
        assertTrue(read(300f, 0f, noLeft).committed)
    }

    @Test fun upThresholdIsCappedByHeight() {
        assertEquals(120f, SwipeLogic.upThreshold(120f, 1000f), 0f)
        assertEquals(50f, SwipeLogic.upThreshold(120f, 200f), 0f)
        assertEquals(1f, SwipeLogic.upThreshold(120f, 0f), 0f)
    }

    @Test fun displaceIsFreeForAvailableAndResistedOtherwise() {
        val (x1, y1) = SwipeLogic.displace(100f, -50f, all)
        assertEquals(100f, x1, 0f)
        assertEquals(-50f, y1, 0f)

        val (x2, _) = SwipeLogic.displace(-100f, 0f, setOf(RIGHT))
        assertEquals(-100f * SwipeLogic.RESISTANCE, x2, 1e-4f)

        val (_, y3) = SwipeLogic.displace(0f, 100f, all)
        assertEquals(100f * SwipeLogic.RESISTANCE, y3, 1e-4f)
    }
}
