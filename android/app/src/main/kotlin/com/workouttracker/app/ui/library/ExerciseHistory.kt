package com.workouttracker.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.workouttracker.app.ui.components.LineChart
import com.workouttracker.app.ui.components.SectionHeader
import com.workouttracker.app.ui.components.relativeDay
import com.workouttracker.domain.Exercise
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.Format
import com.workouttracker.domain.Progress
import com.workouttracker.domain.Records
import com.workouttracker.domain.Session
import com.workouttracker.domain.WeightUnit

/** Records, trend and recent sessions for one exercise. */
@Composable
fun ExerciseHistory(exercise: Exercise, sessions: List<Session>, unit: WeightUnit, onOpenSession: (String) -> Unit) {
    val sets = remember(sessions) { sessions.flatMap { s -> s.doneSets.filter { it.exerciseId == exercise.id } } }
    if (sets.isEmpty()) {
        Text(
            "No sets logged yet. Your records and trend will appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }
    val record = remember(sets) { Records.of(sets)[exercise.id] }
    val trend = remember(sessions) { Progress.trend(sessions, exercise.id) }
    val weighted = sets.any { it.weightKg > 0 }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (record != null) {
            when {
                weighted -> Tile("Best weight", Format.weight(record.maxWeightKg, unit), Modifier.weight(1f))
                exercise.type == ExerciseType.DURATION -> Tile("Longest hold", Format.clock(record.maxDurationSec), Modifier.weight(1f))
                else -> Tile("Most reps", record.maxReps.toString(), Modifier.weight(1f))
            }
            if (weighted) Tile("Most reps", record.maxReps.toString(), Modifier.weight(1f))
        }
        Tile("Sets logged", sets.size.toString(), Modifier.weight(1f))
    }

    if (trend.size >= 2) {
        SectionHeader(if (weighted) "Heaviest set per workout" else if (exercise.type == ExerciseType.DURATION) "Longest hold per workout" else "Most reps per workout")
        LineChart(
            points = trend,
            format = { v ->
                when {
                    weighted -> Format.weight(v, unit)
                    exercise.type == ExerciseType.DURATION -> Format.clock(v.toInt())
                    else -> v.toInt().toString()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SectionHeader("Recent workouts")
    sessions.sortedByDescending { it.startedAt }.take(10).forEach { s ->
        val mine = s.doneSets.filter { it.exerciseId == exercise.id }
        val best = Records.lastPerformance(listOf(s), exercise.id)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenSession(s.id) },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.planName, style = MaterialTheme.typography.titleMedium)
                    Text(relativeDay(s.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${mine.size} sets", style = MaterialTheme.typography.labelLarge)
                    best?.let { Text("Best ${Records.describe(it, unit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
