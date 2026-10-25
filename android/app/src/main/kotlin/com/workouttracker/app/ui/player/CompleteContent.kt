package com.workouttracker.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.workouttracker.app.ui.theme.WorkoutTheme
import com.workouttracker.domain.ActiveWorkout
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.Format
import com.workouttracker.domain.SetOutcome
import com.workouttracker.domain.Stats
import com.workouttracker.domain.WeightUnit

/** What the user has chosen on the summary card; read when they swipe to save. */
@Stable
class FinishDraft(defaultPlanName: String) {
    var rating by mutableStateOf<Int?>(null)
    var note by mutableStateOf("")
    var saveAsPlan by mutableStateOf(false)
    var planName by mutableStateOf(defaultPlanName)
    var updatePlan by mutableStateOf(false)
}

/**
 * Contents of the summary card. Saving is a swipe (right), like everything else; the rating,
 * note and plan options are small controls on the card. There is no scrolling, so the card stays
 * compact and drops the decorative parts while the keyboard is open.
 */
@Composable
fun CompleteContent(
    workout: ActiveWorkout,
    now: Long,
    weightUnit: WeightUnit,
    finishedEarly: Boolean,
    personalRecords: List<String>,
    draft: FinishDraft,
    modifier: Modifier = Modifier,
) {
    val done = workout.log.filter { it.outcome == SetOutcome.DONE }
    val reps = done.filter { it.type == ExerciseType.REPS }.sumOf { it.actualReps }
    val volume = Stats.volume(done)
    val nothingDone = done.isEmpty()
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        if (!keyboardOpen) Text(if (finishedEarly) "💪" else "🎉", style = MaterialTheme.typography.displayMedium)
        Text(
            when {
                nothingDone -> "Nothing logged yet"
                finishedEarly -> "Finish workout?"
                else -> "Workout complete"
            },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (finishedEarly && !nothingDone) {
            Text(
                "${workout.queue.size - workout.cursor} sets left won't be logged",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric(Format.elapsed(now - workout.startedAt), "Time")
            Metric(done.size.toString(), "Sets")
            if (reps > 0) Metric(reps.toString(), "Reps")
            if (volume > 0) Metric(Format.volume(volume, weightUnit), "Volume")
        }
        if (personalRecords.isNotEmpty() && !keyboardOpen) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("New personal bests", style = MaterialTheme.typography.labelLarge, color = WorkoutTheme.colors.success)
                personalRecords.take(3).forEach { Text("🏆 $it", style = MaterialTheme.typography.bodyMedium) }
                if (personalRecords.size > 3) {
                    Text("+${personalRecords.size - 3} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!nothingDone) {
            Text("How did it feel?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { n ->
                    FilterChip(
                        selected = draft.rating == n,
                        onClick = { draft.rating = if (draft.rating == n) null else n },
                        label = { Text(n.toString()) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft.note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (workout.planId == null) {
                CheckRow("Save as a plan", draft.saveAsPlan) { draft.saveAsPlan = it }
                if (draft.saveAsPlan) {
                    OutlinedTextField(
                        value = draft.planName,
                        onValueChange = { draft.planName = it },
                        label = { Text("Plan name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                CheckRow("Update plan with today's numbers", draft.updatePlan) { draft.updatePlan = it }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
