package com.workouttracker.app.ui.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What one swipe direction does. [flyOff] cards leave the screen; others spring back after acting. */
data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val flyOff: Boolean = true,
    val onCommit: () -> Unit,
)

data class SwipeActions(
    val right: SwipeAction? = null,
    val left: SwipeAction? = null,
    val up: SwipeAction? = null,
) {
    operator fun get(direction: SwipeDirection): SwipeAction? = when (direction) {
        SwipeDirection.RIGHT -> right
        SwipeDirection.LEFT -> left
        SwipeDirection.UP -> up
    }

    val available: Set<SwipeDirection>
        get() = buildSet {
            if (right != null) add(SwipeDirection.RIGHT)
            if (left != null) add(SwipeDirection.LEFT)
            if (up != null) add(SwipeDirection.UP)
        }
}

private class JobHolder {
    var job: Job? = null
}

/**
 * The player's only interaction surface: one card that follows the finger and is operated purely
 * by swiping. While dragging, the card tilts, tints toward the action, and shows a "stamp" naming
 * it; crossing the threshold ticks a haptic. A hint bar underneath lists what each direction does.
 *
 * Swipes are recognised anywhere on the deck (not just on the card), and tapping controls inside
 * the card (−/+, timers) still works because nothing is claimed until the finger moves.
 *
 * [cardKey] identifies the card on screen. After a card flies off it stays hidden until the key
 * changes (the next card appears), with a short fallback in case the action didn't change it.
 */
@Composable
fun SwipeDeck(
    cardKey: Any,
    actions: SwipeActions,
    modifier: Modifier = Modifier,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentActions by rememberUpdatedState(actions)
    val currentKey by rememberUpdatedState(cardKey)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var size by remember { mutableStateOf(IntSize.Zero) }
    var shown by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var hiddenKey by remember { mutableStateOf<Any?>(null) }
    var armed by remember { mutableStateOf<SwipeDirection?>(null) }
    val holder = remember { JobHolder() }

    fun read(o: Offset): SwipeReading {
        if (size == IntSize.Zero) return SwipeReading.NONE
        val upPx = SwipeLogic.upThreshold(with(density) { 120.dp.toPx() }, size.height.toFloat())
        return SwipeLogic.reading(o.x, o.y, size.width.toFloat(), upPx, currentActions.available)
    }

    fun settle(finger: Offset) {
        val reading = read(finger)
        val direction = reading.direction.takeIf { reading.committed }
        val action = direction?.let { currentActions[it] }
        holder.job?.cancel()
        holder.job = scope.launch {
            when {
                action == null -> animateOffset(shown, Offset.Zero, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { shown = it }
                action.flyOff -> {
                    val w = size.width.toFloat()
                    val target = when (direction) {
                        SwipeDirection.RIGHT -> Offset(w * 1.3f, shown.y)
                        SwipeDirection.LEFT -> Offset(-w * 1.3f, shown.y)
                        else -> Offset(shown.x, -size.height * 1.3f)
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    animateOffset(shown, target, tween(160)) { shown = it }
                    hiddenKey = currentKey
                    shown = Offset.Zero
                    action.onCommit()
                }
                else -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    action.onCommit()
                    animateOffset(shown, Offset.Zero, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { shown = it }
                }
            }
            armed = null
        }
    }

    // A card that flew off stays hidden until its replacement arrives; don't strand it if none does.
    LaunchedEffect(hiddenKey) {
        if (hiddenKey != null) {
            delay(700)
            hiddenKey = null
        }
    }
    // Each new card eases in.
    LaunchedEffect(cardKey) {
        scale = 0.92f
        animate(0.92f, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { v, _ -> scale = v }
    }

    val reading = read(shown)
    val activeAction = reading.direction?.let { currentActions[it] }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .swipeGesture(
                onStart = {
                    holder.job?.cancel()
                    shown = Offset.Zero
                },
                onDrag = { total ->
                    val (x, y) = SwipeLogic.displace(total.x, total.y, currentActions.available)
                    shown = Offset(x, y)
                    val r = read(total)
                    val nowArmed = r.direction.takeIf { r.committed }
                    if (nowArmed != armed) {
                        armed = nowArmed
                        if (nowArmed != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onEnd = { total -> settle(total) },
                onCancel = { settle(Offset.Zero) },
            )
            .semantics {
                customActions = buildList {
                    currentActions.right?.let { a -> add(CustomAccessibilityAction(a.label) { a.onCommit(); true }) }
                    currentActions.left?.let { a -> add(CustomAccessibilityAction(a.label) { a.onCommit(); true }) }
                    currentActions.up?.let { a -> add(CustomAccessibilityAction(a.label) { a.onCommit(); true }) }
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (hiddenKey != cardKey) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(shown.x.roundToInt(), shown.y.roundToInt()) }
                            .graphicsLayer {
                                rotationZ = (shown.x / size.width.coerceAtLeast(1)) * 10f
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            content()
                            if (activeAction != null) {
                                Box(Modifier.matchParentSize().background(activeAction.color.copy(alpha = reading.progress * 0.28f)))
                                val stampModifier = when (reading.direction) {
                                    SwipeDirection.RIGHT -> Modifier.align(Alignment.TopStart).padding(20.dp)
                                    SwipeDirection.LEFT -> Modifier.align(Alignment.TopEnd).padding(20.dp)
                                    else -> Modifier.align(Alignment.BottomCenter).padding(24.dp)
                                }
                                val tilt = when (reading.direction) {
                                    SwipeDirection.RIGHT -> -12f
                                    SwipeDirection.LEFT -> 12f
                                    else -> 0f
                                }
                                Stamp(activeAction, reading.progress, reading.committed, tilt, stampModifier)
                            }
                        }
                    }
                }
            }
            HintBar(actions)
        }
    }
}

private suspend fun animateOffset(from: Offset, to: Offset, spec: AnimationSpec<Offset>, update: (Offset) -> Unit) {
    animate(Offset.VectorConverter, from, to, animationSpec = spec) { value, _ -> update(value) }
}

@Composable
private fun Stamp(action: SwipeAction, progress: Float, armed: Boolean, tilt: Float, modifier: Modifier) {
    val visibility = (progress * 1.5f - 0.1f).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .graphicsLayer {
                alpha = visibility
                rotationZ = tilt
                scaleX = 0.85f + 0.15f * progress
                scaleY = 0.85f + 0.15f * progress
            }
            .border(3.dp, action.color, shape)
            .background(if (armed) action.color.copy(alpha = 0.2f) else Color.Transparent, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, tint = action.color)
        Spacer(Modifier.width(6.dp))
        Text(action.label.uppercase(), color = action.color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

/** Always-visible legend of what each swipe does. */
@Composable
private fun HintBar(actions: SwipeActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { actions.left?.let { Hint("←", it.label, it.color, "left", arrowFirst = true) } }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { actions.up?.let { Hint("↑", it.label, it.color, "up", arrowFirst = true) } }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { actions.right?.let { Hint("→", it.label, it.color, "right", arrowFirst = false) } }
    }
}

@Composable
private fun Hint(arrow: String, label: String, color: Color, direction: String, arrowFirst: Boolean) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Swipe $direction to $label" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val arrowText: @Composable () -> Unit = { Text(arrow, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
        val labelText: @Composable () -> Unit = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
        if (arrowFirst) {
            arrowText(); labelText()
        } else {
            labelText(); arrowText()
        }
    }
}
