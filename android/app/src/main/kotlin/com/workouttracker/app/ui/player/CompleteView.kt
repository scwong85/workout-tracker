package com.workouttracker.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.workouttracker.app.ui.theme.WorkoutTheme
import com.workouttracker.domain.ActiveWorkout
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.Format
import com.workouttracker.domain.SetOutcome
import com.workouttracker.domain.Stats
import com.workouttracker.domain.WeightUnit

data class FinishChoices(val rating: Int?, val note: String, val saveAsPlanName: String?, val updatePlan: Boolean)

@Composable
fun CompleteView(
    workout: ActiveWorkout,
    now: Long,
    weightUnit: WeightUnit,
    finishedEarly: Boolean,
    personalRecords: List<String>,
    saving: Boolean,
    onAddMore: () -> Unit,
    onKeepGoing: (() -> Unit)?,
    onFinish: (FinishChoices) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rating by rememberSaveable { mutableStateOf<Int?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var saveAsPlan by rememberSaveable { mutableStateOf(false) }
    var planName by rememberSaveable { mutableStateOf("${workout.planName} (saved)") }
    var updatePlan by rememberSaveable { mutableStateOf(false) }

    val done = workout.log.filter { it.outcome == SetOutcome.DONE }
    val reps = done.filter { it.type == ExerciseType.REPS }.sumOf { it.actualReps }
    val volume = Stats.volume(done)
    val nothingDone = done.isEmpty()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (finishedEarly) "💪" else "🎉", style = MaterialTheme.typography.displayLarge)
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
            val remaining = workout.queue.size - workout.cursor
            Text("$remaining sets left. They won't be logged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Metric(Format.elapsed(now - workout.startedAt), "Time")
                Metric(done.size.toString(), "Sets")
                if (reps > 0) Metric(reps.toString(), "Reps")
                if (volume > 0) Metric(Format.volume(volume, weightUnit), "Volume")
            }
        }
        if (personalRecords.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = WorkoutTheme.colors.success.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("New personal bests", style = MaterialTheme.typography.titleMedium, color = WorkoutTheme.colors.success)
                    personalRecords.forEach { Text("🏆 $it") }
                }
            }
        }
        if (!nothingDone) {
            Text("How did it feel?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { n ->
                    FilterChip(selected = rating == n, onClick = { rating = if (rating == n) null else n }, label = { Text(n.toString()) })
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            if (workout.planId == null) {
                CheckRow("Save as a plan", saveAsPlan) { saveAsPlan = it }
                if (saveAsPlan) {
                    OutlinedTextField(value = planName, onValueChange = { planName = it }, label = { Text("Plan name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            } else {
                CheckRow("Update plan with today's numbers", updatePlan) { updatePlan = it }
            }
        }
        Button(
            onClick = {
                onFinish(FinishChoices(rating, note, planName.takeIf { saveAsPlan && workout.planId == null }, updatePlan))
            },
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(containerColor = WorkoutTheme.colors.success, contentColor = WorkoutTheme.colors.onSuccess),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(if (nothingDone) "End without saving" else "Save workout") }
        if (onKeepGoing != null) {
            TextButton(onClick = onKeepGoing) { Text("Keep going") }
        }
        TextButton(onClick = onAddMore) { Text("Add more exercises") }
        Spacer(Modifier.height(24.dp))
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
        Text(label, modifier = Modifier.padding(start = 4.dp))
    }
}
