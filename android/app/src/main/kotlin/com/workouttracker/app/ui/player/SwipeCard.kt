package com.workouttracker.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ColumnScope
import com.workouttracker.app.ui.theme.WorkoutTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SwipeAction { DONE, SKIP, ADD }

/**
 * The player's set card. Swipes are shortcuts for the visible buttons: right = done,
 * left = skip, up = add exercise. The card tints toward the action and ticks a haptic
 * when it crosses the commit threshold. TalkBack users get the same actions as custom actions.
 */
@Composable
fun SwipeCard(
    cardKey: Any,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val done by rememberUpdatedState(onDone)
    val skip by rememberUpdatedState(onSkip)
    val add by rememberUpdatedState(onAdd)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scale = remember { Animatable(1f) }
    var armed by remember { mutableStateOf<SwipeAction?>(null) }
    val colors = WorkoutTheme.colors
    val surface = MaterialTheme.colorScheme.surfaceContainer

    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val sideThreshold = width * 0.3f
        val upThreshold = with(density) { 120.dp.toPx() }

        fun actionFor(o: Offset): SwipeAction? = when {
            o.x > sideThreshold && abs(o.x) > abs(o.y) -> SwipeAction.DONE
            o.x < -sideThreshold && abs(o.x) > abs(o.y) -> SwipeAction.SKIP
            o.y < -upThreshold && abs(o.y) > abs(o.x) -> SwipeAction.ADD
            else -> null
        }

        fun settle(action: SwipeAction?) {
            scope.launch {
                when (action) {
                    SwipeAction.DONE, SwipeAction.SKIP -> {
                        val dir = if (action == SwipeAction.DONE) 1 else -1
                        offset.animateTo(Offset(dir * width * 1.4f, offset.value.y), tween(180))
                        if (action == SwipeAction.DONE) done() else skip()
                        offset.snapTo(Offset.Zero)
                        scale.snapTo(0.94f)
                        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                    SwipeAction.ADD -> {
                        offset.animateTo(Offset.Zero, spring())
                        add()
                    }
                    null -> offset.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                armed = null
            }
        }

        val o = offset.value
        val towardDone = (o.x / sideThreshold).coerceIn(0f, 1f)
        val towardSkip = (-o.x / sideThreshold).coerceIn(0f, 1f)
        val tint = when {
            towardDone > 0f -> lerp(surface, colors.success.copy(alpha = 0.35f).compositeOver(surface), towardDone)
            towardSkip > 0f -> lerp(surface, colors.warning.copy(alpha = 0.35f).compositeOver(surface), towardSkip)
            else -> surface
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = tint),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
                .graphicsLayer {
                    rotationZ = (o.x / width) * 10f
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .pointerInput(cardKey) {
                    detectDragGestures(
                        onDragEnd = { settle(actionFor(offset.value)) },
                        onDragCancel = { settle(null) },
                        onDrag = { change, drag ->
                            change.consume()
                            val next = offset.value + drag
                            scope.launch { offset.snapTo(next) }
                            val action = actionFor(next)
                            if (action != armed) {
                                armed = action
                                if (action != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                    )
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Log set") { done(); true },
                        CustomAccessibilityAction("Skip set") { skip(); true },
                        CustomAccessibilityAction("Add exercise") { add(); true },
                    )
                },
            content = content,
        )
    }
}
