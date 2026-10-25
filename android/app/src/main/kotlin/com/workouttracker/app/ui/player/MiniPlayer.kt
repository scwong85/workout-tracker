package com.workouttracker.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workouttracker.app.ui.components.rememberNow
import com.workouttracker.app.ui.theme.WorkoutTheme
import com.workouttracker.domain.ActiveWorkout
import com.workouttracker.domain.Format
import com.workouttracker.domain.Phase

/** Persistent "now playing" bar shown above the bottom navigation while a workout is active. */
@Composable
fun MiniPlayer(workout: ActiveWorkout, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val now by rememberNow(500)
    val status = when (val phase = workout.phase) {
        is Phase.Resting -> "Rest ${Format.clockCeil(phase.endsAt - now)}"
        Phase.Complete -> "Ready to save"
        is Phase.Performing -> workout.current?.let { "Set ${workout.cursor + 1}/${workout.queue.size} · ${it.exercises.first().exerciseName}" } ?: "Add exercises"
    }
    val accent = if (workout.phase is Phase.Resting) WorkoutTheme.colors.rest else MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Resume workout", onClick = onResume),
    ) {
        Column {
            LinearProgressIndicator(progress = { workout.progress }, color = accent, modifier = Modifier.fillMaxWidth())
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(workout.planName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(onClick = onResume) { Text("Resume") }
            }
        }
    }
}
