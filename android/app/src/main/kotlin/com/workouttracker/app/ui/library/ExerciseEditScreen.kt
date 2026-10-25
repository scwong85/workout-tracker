package com.workouttracker.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.workouttracker.app.data.repo.ExerciseRepository
import com.workouttracker.app.ui.Routes
import com.workouttracker.app.ui.appViewModel
import com.workouttracker.domain.Category
import com.workouttracker.domain.Exercise
import com.workouttracker.domain.ExerciseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseForm(
    val id: String = "",
    val name: String = "",
    val type: ExerciseType = ExerciseType.REPS,
    val category: Category = Category.CHEST,
    val weightText: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    val loaded: Boolean = false,
    val saved: Boolean = false,
    val isCopyOfPreset: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && (weightText.isBlank() || weightText.toDoubleOrNull()?.let { it >= 0 } == true)
}

class ExerciseEditViewModel(private val repo: ExerciseRepository, exerciseId: String) : ViewModel() {
    private val _form = MutableStateFlow(ExerciseForm())
    val form: StateFlow<ExerciseForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = if (exerciseId == Routes.NEW) null else repo.get(exerciseId)
            _form.value = when {
                existing == null -> ExerciseForm(loaded = true)
                // Presets are read-only: editing one creates a custom copy.
                existing.isPreset -> existing.toForm().copy(id = "", name = "${existing.name} (custom)", createdAt = 0, isCopyOfPreset = true)
                else -> existing.toForm()
            }
        }
    }

    private fun Exercise.toForm() = ExerciseForm(
        id = id, name = name, type = type, category = category,
        weightText = if (defaultWeightKg > 0) defaultWeightKg.toString().removeSuffix(".0") else "",
        notes = notes, createdAt = createdAt, loaded = true,
    )

    fun update(transform: (ExerciseForm) -> ExerciseForm) = _form.update(transform)

    fun save() {
        val f = _form.value
        if (!f.canSave) return
        viewModelScope.launch {
            repo.save(
                Exercise(
                    id = f.id, name = f.name, type = f.type, category = f.category,
                    defaultWeightKg = f.weightText.toDoubleOrNull() ?: 0.0,
                    notes = f.notes.trim(), createdAt = f.createdAt,
                ),
            )
            _form.update { it.copy(saved = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(exerciseId: String, onDone: () -> Unit) {
    val vm = appViewModel(key = "edit-$exerciseId") { ExerciseEditViewModel(it.exercises, exerciseId) }
    val form by vm.form.collectAsStateWithLifecycle()

    LaunchedEffect(form.saved) { if (form.saved) onDone() }

    val title = when {
        form.isCopyOfPreset -> "Customise exercise"
        form.id.isBlank() -> "New exercise"
        else -> "Edit exercise"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { TextButton(onClick = vm::save, enabled = form.canSave) { Text("Save") } },
            )
        },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                isError = form.name.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Measured by", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ExerciseType.entries.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = { vm.update { it.copy(type = type) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ExerciseType.entries.size),
                    ) { Text(if (type == ExerciseType.REPS) "Reps" else "Time") }
                }
            }
            Text("Muscle group", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(selected = form.category == c, onClick = { vm.update { it.copy(category = c) } }, label = { Text(c.label) })
                }
            }
            OutlinedTextField(
                value = form.weightText,
                onValueChange = { v -> vm.update { it.copy(weightText = v.filter { ch -> ch.isDigit() || ch == '.' }) } },
                label = { Text("Default weight (kg, optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> vm.update { it.copy(notes = v) } },
                label = { Text("Notes & form cues") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
