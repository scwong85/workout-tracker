package com.workouttracker.app.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Whole-surface swipe detection that can't be starved by what's inside or around it.
 *
 * Touches are read in the Initial pass, i.e. before child buttons and any enclosing scroll
 * container see them. Nothing is consumed until the finger passes touch slop, so taps on child
 * buttons still work; after that the gesture is claimed and children see it as consumed.
 *
 * The callbacks are always the latest ones passed in (no stale closures), and the drag total is
 * accumulated here, never read back from animated state.
 *
 * Apply this before any offset/graphicsLayer that moves the content, so the coordinate space
 * stays fixed while the content follows the finger.
 */
@Composable
fun Modifier.swipeGesture(
    onStart: () -> Unit = {},
    onDrag: (total: Offset) -> Unit = {},
    onEnd: (total: Offset) -> Unit,
    onCancel: () -> Unit = {},
): Modifier {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(Unit) {
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
                if (!dragging && total.getDistance() > viewConfiguration.touchSlop) {
                    dragging = true
                    start()
                }
                if (dragging) {
                    change.consume()
                    drag(total)
                }
            }
            when {
                !dragging -> Unit
                cancelled -> cancel()
                else -> end(total)
            }
        }
    }
}
