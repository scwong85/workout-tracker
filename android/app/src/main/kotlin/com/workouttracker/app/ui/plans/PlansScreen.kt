package com.workouttracker.app.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.workouttracker.app.ui.components.EmptyState
import com.workouttracker.app.ui.components.LocalMessenger
import com.workouttracker.app.ui.components.relativeDay
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanEstimator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlansViewModel(private val container: AppContainer) : ViewModel() {
    val plans: StateFlow<List<Plan>?> = container.plans.observeAll()
        .map { list -> list.sortedWith(compareByDescending<Plan> { it.lastPerformedAt ?: 0 }.thenByDescending { it.updatedAt }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun duplicate(plan: Plan) = viewModelScope.launch { container.plans.duplicate(plan.id) }

    fun delete(plan: Plan) = container.appScope.launch { container.plans.delete(plan.id) }

    fun restore(plan: Plan) = container.appScope.launch { container.plans.restore(plan) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    onEditPlan: (String) -> Unit,
    onCreatePlan: () -> Unit,
    onStartPlan: ((Plan) -> Unit)? = null,
) {
    val vm = appViewModel { PlansViewModel(it) }
    val plans by vm.plans.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Plans") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreatePlan,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New plan") },
            )
        },
    ) { padding ->
        val list = plans ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.AutoMirrored.Outlined.List,
                    "No plans yet",
                    "A plan is a reusable list of exercises with sets, reps and rest. Build one to get started.",
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    onClick = { onEditPlan(plan.id) },
                    onStart = onStartPlan?.let { start -> { start(plan) } },
                    onDuplicate = { vm.duplicate(plan); messenger.show("Duplicated ${plan.name}") },
                    onDelete = {
                        vm.delete(plan)
                        messenger.undo("Deleted ${plan.name}") { vm.restore(plan) }
                    },
                )
            }
        }
    }
}

@Composable
fun PlanCard(
    plan: Plan,
    onClick: () -> Unit,
    onStart: (() -> Unit)?,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${plan.exerciseCount} exercises · ${plan.totalSets} sets · ~${PlanEstimator.estimateMinutes(plan.groups)} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onStart != null) {
                    FilledTonalIconButton(onClick = onStart) { Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${plan.name}") }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onClick() })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                plan.groups.flatMap { g -> g.items.map { it.category } }.distinct().take(5).forEach {
                    CategoryAvatar(it, size = 28.dp)
                }
                Text(
                    plan.lastPerformedAt?.let { "Last done ${relativeDay(it)}" } ?: "Not done yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
            }
        }
    }
}
