package com.workouttracker.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.workouttracker.app.AppContainer
import com.workouttracker.app.ui.appViewModel
import com.workouttracker.app.ui.components.LocalMessenger
import com.workouttracker.app.ui.components.shortDateTime
import com.workouttracker.app.ui.theme.WorkoutTheme
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.Format
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanGroup
import com.workouttracker.domain.Session
import com.workouttracker.domain.SessionPlans
import com.workouttracker.domain.SessionSet
import com.workouttracker.domain.SessionStatus
import com.workouttracker.domain.SetOutcome
import com.workouttracker.domain.Stats
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionDetailViewModel(private val container: AppContainer, sessionId: String) : ViewModel() {
    val session: StateFlow<Session?> = container.sessions.observe(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weightUnit: WeightUnit get() = container.settings.settings.value.weightUnit

    fun delete(s: Session) = container.appScope.launch { container.sessions.delete(s.id) }
    fun restore(s: Session) = container.appScope.launch { container.sessions.save(s) }

    /** The saved plan if it still exists, otherwise groups rebuilt from what was logged. */
    suspend fun repeatSource(s: Session): Pair<Plan?, List<PlanGroup>> {
        val plan = s.planId?.let { container.plans.get(it) }
        if (plan != null) return plan to plan.groups
        val categories = container.exercises.observeAll().first().associate { it.id to it.category }
        return null to SessionPlans.groupsFrom(s.sets, categories)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onRepeatPlan: (Plan) -> Unit,
    onRepeatAdHoc: (String, List<PlanGroup>) -> Unit,
) {
    val vm = appViewModel(key = "session-$sessionId") { SessionDetailViewModel(it, sessionId) }
    val session by vm.session.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val unit = vm.weightUnit
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.planName.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    session?.let { s ->
                        IconButton(onClick = {
                            vm.delete(s)
                            onBack()
                            messenger.undo("Workout deleted") { vm.restore(s) }
                        }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete workout") }
                    }
                },
            )
        },
    ) { padding ->
        val s = session ?: return@Scaffold
        val groups = s.sets.groupBy { it.groupOrdinal }.toSortedMap().values.toList()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(shortDateTime(s.startedAt), style = MaterialTheme.typography.bodyLarge)
                    val reps = s.doneSets.filter { it.type == ExerciseType.REPS }.sumOf { it.actualReps }
                    val volume = Stats.volume(s.sets)
                    val parts = listOfNotNull(
                        Format.minutes(s.durationMs),
                        "${s.doneSets.size} sets",
                        reps.takeIf { it > 0 }?.let { "$it reps" },
                        volume.takeIf { it > 0 }?.let { Format.volume(it, unit) },
                    )
                    Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.status == SessionStatus.PARTIAL) Text("Finished early", color = WorkoutTheme.colors.warning)
                    s.rating?.let { Text("Effort: ${"●".repeat(it)}${"○".repeat(5 - it)}") }
                    if (s.note.isNotBlank()) Text("“${s.note}”", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            val (plan, rebuilt) = vm.repeatSource(s)
                            if (plan != null) onRepeatPlan(plan) else onRepeatAdHoc(s.planName, rebuilt)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Repeat this workout")
                }
            }
            items(groups) { sets -> GroupCard(sets, unit) }
        }
    }
}

@Composable
private fun GroupCard(sets: List<SessionSet>, unit: WeightUnit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val names = sets.map { it.exerciseName }.distinct()
            Text(names.joinToString(" + "), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            sets.groupBy { it.setOrdinal }.toSortedMap().forEach { (ordinal, round) ->
                round.forEach { set ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Set $ordinal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                        if (names.size > 1) Text(set.exerciseName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        else Spacer(Modifier.weight(1f))
                        Text(describe(set, unit), color = if (set.outcome == SetOutcome.SKIPPED) WorkoutTheme.colors.warning else MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (ordinal != sets.maxOf { it.setOrdinal }) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

private fun describe(set: SessionSet, unit: WeightUnit): String {
    if (set.outcome == SetOutcome.SKIPPED) return "Skipped"
    val main = when (set.type) {
        ExerciseType.REPS -> {
            val diff = set.actualReps - set.targetReps
            "${set.actualReps} reps" + if (diff != 0) " (${if (diff > 0) "+" else ""}$diff)" else ""
        }
        ExerciseType.DURATION -> Format.clock(set.actualDurationSec)
    }
    return if (set.weightKg > 0) "$main @ ${Format.weight(set.weightKg, unit)}" else main
}
