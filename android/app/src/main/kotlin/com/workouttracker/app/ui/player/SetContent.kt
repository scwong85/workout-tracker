package com.workouttracker.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.workouttracker.app.ui.components.CategoryAvatar
import com.workouttracker.domain.AdjustField
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.Format
import com.workouttracker.domain.Phase
import com.workouttracker.domain.QueuedExercise
import com.workouttracker.domain.QueuedSet
import com.workouttracker.domain.Units
import com.workouttracker.domain.WeightUnit

/** "Last time" cue for an exercise, e.g. "Last time: 10 reps @ 20 kg". Supplied by the progress layer. */
typealias LastTimeLookup = (QueuedExercise) -> String?

/**
 * Contents of the set card. There is deliberately no scrolling and no Done/Skip buttons: the
 * whole card is swiped, and only the −/+ and timer controls inside it are tappable. Layout
 * shrinks with the number of exercises so even a four-exercise superset fits one screen.
 */
@Composable
fun SetContent(
    set: QueuedSet,
    phase: Phase.Performing,
    now: Long,
    weightUnit: WeightUnit,
    lastTime: LastTimeLookup,
    onAdjust: (index: Int, field: AdjustField, delta: Double) -> Unit,
    onStartHold: () -> Unit,
    onStopHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = set.exercises.size
    val dense = count >= 3
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val label = if (count > 1) {
            "SUPERSET · ROUND ${set.setOrdinal} OF ${set.totalSetsInGroup}"
        } else {
            "${set.exercises.first().category.label.uppercase()} · SET ${set.setOrdinal} OF ${set.totalSetsInGroup}"
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(if (dense) 4.dp else 8.dp))
        set.exercises.forEachIndexed { index, ex ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = if (dense) 4.dp else 10.dp))
            val adjust = { field: AdjustField, delta: Double -> onAdjust(index, field, delta) }
            if (dense) {
                DenseBlock(ex, weightUnit, adjust)
            } else {
                FullBlock(
                    ex = ex,
                    compact = count > 1,
                    // A lone timed exercise swaps its stepper for the countdown ring while running.
                    inlineTimer = count == 1,
                    phase = phase,
                    now = now,
                    weightUnit = weightUnit,
                    lastTime = lastTime(ex),
                    onAdjust = adjust,
                    onStartHold = onStartHold,
                    onStopHold = onStopHold,
                )
            }
        }
        if (count > 1 && set.exercises.any { it.type == ExerciseType.DURATION }) {
            Spacer(Modifier.height(8.dp))
            HoldControls(phase, now, ringSize = if (dense) 96.dp else 120.dp, onStartHold, onStopHold)
        }
    }
}

@Composable
private fun FullBlock(
    ex: QueuedExercise,
    compact: Boolean,
    inlineTimer: Boolean,
    phase: Phase.Performing,
    now: Long,
    weightUnit: WeightUnit,
    lastTime: String?,
    onAdjust: (AdjustField, Double) -> Unit,
    onStartHold: () -> Unit,
    onStopHold: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            CategoryAvatar(ex.category, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                ex.exerciseName,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        when (ex.type) {
            ExerciseType.REPS -> BigStepper(
                value = ex.actualReps.toString(),
                unit = "reps",
                compact = compact,
                label = "Reps for ${ex.exerciseName}",
                onMinus = { onAdjust(AdjustField.REPS, -1.0) },
                onPlus = { onAdjust(AdjustField.REPS, 1.0) },
            )
            ExerciseType.DURATION -> {
                if (inlineTimer && phase.holdEndsAt != null && phase.holdStartedAt != null) {
                    HoldControls(phase, now, ringSize = 200.dp, onStartHold, onStopHold)
                } else {
                    BigStepper(
                        value = Format.clock(ex.actualDurationSec),
                        unit = "time",
                        compact = compact,
                        label = "Time for ${ex.exerciseName}",
                        onMinus = { onAdjust(AdjustField.DURATION, -5.0) },
                        onPlus = { onAdjust(AdjustField.DURATION, 5.0) },
                    )
                    if (inlineTimer) {
                        Spacer(Modifier.height(4.dp))
                        HoldControls(phase, now, ringSize = 200.dp, onStartHold, onStopHold)
                    }
                }
            }
        }
        WeightRow(ex.weightKg, weightUnit, onAdjust)
        if (lastTime != null) {
            Text(
                lastTime,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** One row per exercise, for supersets of three or more. */
@Composable
private fun DenseBlock(ex: QueuedExercise, weightUnit: WeightUnit, onAdjust: (AdjustField, Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CategoryAvatar(ex.category, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(ex.exerciseName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ex.weightKg > 0) {
                Text(Format.weight(ex.weightKg, weightUnit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val (value, field, step) = when (ex.type) {
            ExerciseType.REPS -> Triple("${ex.actualReps}", AdjustField.REPS, 1.0)
            ExerciseType.DURATION -> Triple(Format.clock(ex.actualDurationSec), AdjustField.DURATION, 5.0)
        }
        IconButton(onClick = { onAdjust(field, -step) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease ${ex.exerciseName}")
        }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(64.dp).semantics { contentDescription = "${ex.exerciseName}: $value" },
        )
        IconButton(onClick = { onAdjust(field, step) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Increase ${ex.exerciseName}")
        }
    }
}

@Composable
private fun HoldControls(phase: Phase.Performing, now: Long, ringSize: Dp, onStart: () -> Unit, onStop: () -> Unit) {
    // Locals: smart casts don't work on properties declared in another module.
    val endsAt = phase.holdEndsAt
    val startedAt = phase.holdStartedAt
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (endsAt != null && startedAt != null) {
            val total = (endsAt - startedAt).coerceAtLeast(1)
            val remaining = (endsAt - now).coerceAtLeast(0)
            CountdownRing(
                fraction = remaining.toFloat() / total,
                text = Format.clockCeil(remaining),
                color = MaterialTheme.colorScheme.primary,
                size = ringSize,
            )
            FilledTonalButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Stop timer")
            }
        } else {
            FilledTonalButton(onClick = onStart) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Start timer")
            }
        }
    }
}

@Composable
private fun BigStepper(value: String, unit: String, compact: Boolean, label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val buttonSize = if (compact) 48.dp else 64.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $unit")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(
                value,
                style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.semantics {
                    contentDescription = "$label: $value"
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Text(unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $unit")
        }
    }
}

@Composable
private fun WeightRow(weightKg: Double, unit: WeightUnit, onAdjust: (AdjustField, Double) -> Unit) {
    val step = Units.fromDisplay(Units.step(unit), unit)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        IconButton(onClick = { onAdjust(AdjustField.WEIGHT, -step) }, enabled = weightKg > 0) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease weight")
        }
        Text(
            if (weightKg > 0) Format.weight(weightKg, unit) else "Bodyweight",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp),
        )
        IconButton(onClick = { onAdjust(AdjustField.WEIGHT, step) }) { Icon(Icons.Filled.Add, contentDescription = "Increase weight") }
    }
}

/** Circular countdown; [fraction] is the remaining share (1 → full ring). */
@Composable
fun CountdownRing(fraction: Float, text: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = (size.toPx() * 0.07f).coerceIn(6.dp.toPx(), 14.dp.toPx())
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                color, -90f, 360f * fraction.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // No live region: it would announce every tick. Ends are announced by the alert instead.
        Text(
            text,
            style = when {
                size >= 200.dp -> MaterialTheme.typography.displayLarge
                size >= 110.dp -> MaterialTheme.typography.displayMedium
                else -> MaterialTheme.typography.headlineMedium
            },
        )
    }
}
