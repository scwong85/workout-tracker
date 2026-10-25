package com.workouttracker.app.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

/**
 * Whole-surface swipe detection that can't be starved by what's inside or around it.
 *
 * Events are read in the Initial pass, i.e. before child buttons and before the enclosing
 * scroll container see them, and the drag total is accumulated locally. Until the finger moves
 * past touch slop nothing is consumed, so taps on child buttons still work; after that the
 * gesture is claimed and the children/scroller see it as consumed.
 *
 * Apply this before any offset/graphicsLayer that moves the element, so the coordinate space
 * stays fixed while the element follows the finger.
 */
fun Modifier.swipeGesture(
    key: Any?,
    onDrag: (total: Offset) -> Unit = {},
    onEnd: (total: Offset, size: IntSize) -> Unit,
    onCancel: () -> Unit = {},
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var total = Offset.Zero
        var dragging = false
        var cancelled = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                cancelled = true
                break
            }
            if (!change.pressed) break
            total += change.position - change.previousPosition
            if (!dragging && total.getDistance() > viewConfiguration.touchSlop) dragging = true
            if (dragging) {
                change.consume()
                onDrag(total)
            }
        }
        when {
            !dragging -> Unit
            cancelled -> onCancel()
            else -> onEnd(total, size)
        }
    }
}
