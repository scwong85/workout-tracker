package com.workouttracker.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.workouttracker.app.ui.theme.WorkoutTheme
import com.workouttracker.domain.Format
import com.workouttracker.domain.Phase
import com.workouttracker.domain.QueuedSet

@Composable
fun RestView(
    rest: Phase.Resting,
    now: Long,
    next: QueuedSet?,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = (rest.endsAt - rest.startedAt).coerceAtLeast(1)
    val remaining = (rest.endsAt - now).coerceAtLeast(0)
    val over = remaining == 0L
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (over) "REST OVER" else "REST",
            style = MaterialTheme.typography.labelLarge,
            color = WorkoutTheme.colors.rest,
        )
        Spacer(Modifier.height(16.dp))
        CountdownRing(
            fraction = remaining.toFloat() / total,
            text = Format.clockCeil(remaining),
            color = WorkoutTheme.colors.rest,
            size = 240.dp,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { onAdjust(-15) }) { Text("−15s") }
            OutlinedButton(onClick = { onAdjust(15) }) { Text("+15s") }
        }
        Spacer(Modifier.height(32.dp))
        if (next != null) {
            Text("Up next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(next.summary(), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSkip,
            colors = ButtonDefaults.buttonColors(containerColor = WorkoutTheme.colors.rest, contentColor = WorkoutTheme.colors.onRest),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(if (over) "Start next set" else "Skip rest") }
    }
}

/** "Dips · 8 reps" or "Plank · 0:45 + Dips · 8 reps". */
fun QueuedSet.summary(): String = exercises.joinToString(" + ") { ex ->
    val target = when (ex.type) {
        com.workouttracker.domain.ExerciseType.REPS -> "${ex.targetReps} reps"
        com.workouttracker.domain.ExerciseType.DURATION -> Format.clock(ex.targetDurationSec)
    }
    "${ex.exerciseName} · $target"
}
