package com.workouttracker.app.ui.plans

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.workouttracker.app.AppContainer
import com.workouttracker.app.ui.Routes
import com.workouttracker.app.ui.appViewModel
import com.workouttracker.app.ui.components.CategoryAvatar
import com.workouttracker.app.ui.components.ExercisePickerSheet
import com.workouttracker.app.ui.components.StepperRow
import com.workouttracker.domain.Exercise
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.Format
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanDefaults
import com.workouttracker.domain.PlanEdits
import com.workouttracker.domain.PlanEstimator
import com.workouttracker.domain.PlanGroup
import com.workouttracker.domain.PlanItem
import com.workouttracker.domain.Units
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanEditorState(
    val id: String = "",
    val name: String = "",
    val groups: List<PlanGroup> = emptyList(),
    val createdAt: Long = 0,
    val lastPerformedAt: Long? = null,
    val loaded: Boolean = false,
    val dirty: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && groups.isNotEmpty()
    val isNew: Boolean get() = id.isBlank()
}

class PlanEditorViewModel(private val container: AppContainer, planId: String) : ViewModel() {
    private val _state = MutableStateFlow(PlanEditorState())
    val state: StateFlow<PlanEditorState> = _state.asStateFlow()

    val exercises: StateFlow<List<Exercise>> = container.exercises.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Default rest for newly added exercises; replaced by the user setting once settings exist. */
    var defaultRestSec: Int = PlanDefaults.REST_SEC

    /** Builds the starting targets for an exercise the user just added. */
    var itemFor: (Exercise) -> PlanItem = { PlanDefaults.itemFor(it) }

    init {
        viewModelScope.launch {
            val plan = if (planId == Routes.NEW) null else container.plans.get(planId)
            _state.value = plan?.let {
                PlanEditorState(id = it.id, name = it.name, groups = it.groups, createdAt = it.createdAt, lastPerformedAt = it.lastPerformedAt, loaded = true)
            } ?: PlanEditorState(loaded = true)
        }
    }

    private fun edit(transform: (List<PlanGroup>) -> List<PlanGroup>) =
        _state.update { it.copy(groups = transform(it.groups), dirty = true) }

    fun rename(name: String) = _state.update { it.copy(name = name, dirty = true) }
    fun add(exercises: List<Exercise>, asSuperset: Boolean) =
        edit { PlanEdits.addExercises(it, exercises, asSuperset, defaultRestSec, itemFor) }
    fun move(from: Int, to: Int) = edit { PlanEdits.move(it, from, to) }
    fun mergeWithNext(index: Int) = edit { PlanEdits.mergeWithNext(it, index) }
    fun ungroup(index: Int) = edit { PlanEdits.ungroup(it, index) }
    fun removeGroup(index: Int) = edit { groups -> groups.filterIndexed { i, _ -> i != index } }
    fun removeItem(itemId: String) = edit { PlanEdits.removeItem(it, itemId) }
    fun updateItem(itemId: String, transform: (PlanItem) -> PlanItem) = edit { PlanEdits.updateItem(it, itemId, transform) }
    fun setRest(groupId: String, sec: Int) = edit { PlanEdits.setRest(it, groupId, sec) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            container.plans.save(
                Plan(id = s.id, name = s.name, groups = s.groups, createdAt = s.createdAt, lastPerformedAt = s.lastPerformedAt),
            )
            _state.update { it.copy(saved = true, dirty = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(
    planId: String,
    onDone: () -> Unit,
    weightUnit: WeightUnit = WeightUnit.KG,
    configure: (PlanEditorViewModel) -> Unit = {},
) {
    val vm = appViewModel(key = "plan-$planId") { PlanEditorViewModel(it, planId).also(configure) }
    val state by vm.state.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var expandedItem by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    fun leave() {
        if (state.dirty) confirmDiscard = true else onDone()
    }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New plan" else "Edit plan") },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = vm::save, enabled = state.canSave) { Text("Save") } },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::rename,
                    label = { Text("Plan name") },
                    placeholder = { Text("e.g. Push Day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            itemsIndexed(state.groups, key = { _, g -> g.id }) { index, group ->
                GroupCard(
                    group = group,
                    index = index,
                    count = state.groups.size,
                    expandedItem = expandedItem,
                    weightUnit = weightUnit,
                    onToggleItem = { id -> expandedItem = if (expandedItem == id) null else id },
                    onMoveUp = { vm.move(index, index - 1) },
                    onMoveDown = { vm.move(index, index + 1) },
                    onMergeNext = { vm.mergeWithNext(index) },
                    onUngroup = { vm.ungroup(index) },
                    onRemoveGroup = { vm.removeGroup(index) },
                    onRemoveItem = vm::removeItem,
                    onUpdateItem = vm::updateItem,
                    onRest = { vm.setRest(group.id, it) },
                    modifier = Modifier.animateItem(),
                )
            }
            item {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercises")
                }
            }
            if (state.groups.isNotEmpty()) {
                item {
                    val sets = state.groups.sumOf { it.rounds }
                    Text(
                        "Estimated ${PlanEstimator.estimateMinutes(state.groups)} min · $sets sets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = exercises,
            onDismiss = { showPicker = false },
            onConfirm = { selected, superset ->
                vm.add(selected, superset)
                showPicker = false
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this plan haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDone() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun GroupCard(
    group: PlanGroup,
    index: Int,
    count: Int,
    expandedItem: String?,
    weightUnit: WeightUnit,
    onToggleItem: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMergeNext: () -> Unit,
    onUngroup: () -> Unit,
    onRemoveGroup: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onUpdateItem: (String, (PlanItem) -> PlanItem) -> Unit,
    onRest: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (group.isSuperset) "SUPERSET · ${group.rounds} ROUNDS" else "EXERCISE ${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = if (group.isSuperset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMoveUp, enabled = index > 0) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up") }
            IconButton(onClick = onMoveDown, enabled = index < count - 1) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (index < count - 1) {
                        DropdownMenuItem(text = { Text("Superset with next") }, onClick = { menu = false; onMergeNext() })
                    }
                    if (group.isSuperset) {
                        DropdownMenuItem(text = { Text("Split superset") }, onClick = { menu = false; onUngroup() })
                    }
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onRemoveGroup() })
                }
            }
        }
        group.items.forEach { item ->
            ItemRow(
                item = item,
                expanded = expandedItem == item.id,
                weightUnit = weightUnit,
                onToggle = { onToggleItem(item.id) },
                onRemove = if (group.isSuperset) ({ onRemoveItem(item.id) }) else null,
                onUpdate = { t -> onUpdateItem(item.id, t) },
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        StepperRow(
            label = "Rest after",
            value = Format.clock(group.restAfterSec),
            onDecrement = { onRest(group.restAfterSec - 15) },
            onIncrement = { onRest(group.restAfterSec + 15) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ItemRow(
    item: PlanItem,
    expanded: Boolean,
    weightUnit: WeightUnit,
    onToggle: () -> Unit,
    onRemove: (() -> Unit)?,
    onUpdate: ((PlanItem) -> PlanItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryAvatar(item.category, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.exerciseName, style = MaterialTheme.typography.titleMedium)
                val weight = if (item.weightKg > 0) " @ ${Format.weight(item.weightKg, weightUnit)}" else ""
                Text(Format.target(item) + weight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, contentDescription = "Remove ${item.exerciseName}") }
            }
        }
        if (expanded) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                StepperRow("Sets", item.sets.toString(), { onUpdate { it.copy(sets = it.sets - 1) } }, { onUpdate { it.copy(sets = it.sets + 1) } })
                when (item.type) {
                    ExerciseType.REPS ->
                        StepperRow("Reps", item.reps.toString(), { onUpdate { it.copy(reps = it.reps - 1) } }, { onUpdate { it.copy(reps = it.reps + 1) } })
                    ExerciseType.DURATION ->
                        StepperRow(
                            "Time", Format.clock(item.durationSec),
                            { onUpdate { it.copy(durationSec = it.durationSec - 5) } },
                            { onUpdate { it.copy(durationSec = it.durationSec + 5) } },
                        )
                }
                val step = Units.fromDisplay(Units.step(weightUnit), weightUnit)
                StepperRow(
                    "Weight",
                    if (item.weightKg > 0) Format.weight(item.weightKg, weightUnit) else "Bodyweight",
                    { onUpdate { it.copy(weightKg = (it.weightKg - step).coerceAtLeast(0.0)) } },
                    { onUpdate { it.copy(weightKg = it.weightKg + step) } },
                )
            }
        }
    }
}
