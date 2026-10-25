package com.workouttracker.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.workouttracker.app.AppContainer
import com.workouttracker.app.ui.appViewModel
import com.workouttracker.app.ui.components.CategoryAvatar
import com.workouttracker.app.ui.components.LocalMessenger
import com.workouttracker.domain.Exercise
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.Session
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExerciseDetailViewModel(private val container: AppContainer, val exerciseId: String) : ViewModel() {
    val exercise: StateFlow<Exercise?> = container.exercises.observeAll()
        .map { list -> list.firstOrNull { it.id == exerciseId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessions: StateFlow<List<Session>> = container.sessions.observeAll()
        .map { list -> list.filter { s -> s.sets.any { it.exerciseId == exerciseId } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weightUnit: WeightUnit get() = container.settings.settings.value.weightUnit

    suspend fun plansUsing(): List<String> = container.exercises.planNamesUsing(exerciseId)

    /** Runs in the app scope: the screen pops right after, which would cancel viewModelScope. */
    fun setArchived(archived: Boolean) {
        container.appScope.launch { container.exercises.setArchived(exerciseId, archived) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenSession: (String) -> Unit = {},
) {
    val vm = appViewModel(key = "detail-$exerciseId") { ExerciseDetailViewModel(it, exerciseId) }
    val exercise by vm.exercise.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    var confirmPlans by remember { mutableStateOf<List<String>?>(null) }

    fun archive() {
        val name = exercise?.name ?: return
        vm.setArchived(true)
        onBack()
        messenger.undo("Removed $name") { vm.setArchived(false) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    val ex = exercise
                    if (ex != null) {
                        IconButton(onClick = { onEdit(ex.id) }) {
                            if (ex.isPreset) Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate and edit")
                            else Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                        }
                        if (!ex.isPreset) {
                            IconButton(onClick = {
                                scope.launch {
                                    val plans = vm.plansUsing()
                                    if (plans.isEmpty()) archive() else confirmPlans = plans
                                }
                            }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove") }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val ex = exercise ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(ex.category, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(ex.name, style = MaterialTheme.typography.headlineMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text(ex.category.label) })
                        AssistChip(onClick = {}, label = { Text(if (ex.type == ExerciseType.REPS) "Reps" else "Timed") })
                        if (ex.isPreset) AssistChip(onClick = {}, label = { Text("Built-in") })
                    }
                }
            }
            if (ex.notes.isNotBlank()) {
                Text(ex.notes, style = MaterialTheme.typography.bodyLarge)
            }
            ExerciseHistory(ex, sessions, vm.weightUnit, onOpenSession)
        }
    }

    confirmPlans?.let { plans ->
        AlertDialog(
            onDismissRequest = { confirmPlans = null },
            title = { Text("Remove exercise?") },
            text = {
                Text(
                    "It's used in: ${plans.joinToString()}. It will be hidden from the library, " +
                        "but stays in those plans and in your history.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmPlans = null; archive() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmPlans = null }) { Text("Cancel") } },
        )
    }
}
