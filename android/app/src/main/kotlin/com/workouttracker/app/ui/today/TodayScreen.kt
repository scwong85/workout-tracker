package com.workouttracker.app.ui.today

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.workouttracker.app.AppContainer
import com.workouttracker.app.ui.appViewModel
import com.workouttracker.app.ui.components.CategoryAvatar
import com.workouttracker.app.ui.components.LocalMessenger
import com.workouttracker.app.ui.components.SectionHeader
import com.workouttracker.app.ui.components.relativeDay
import com.workouttracker.app.ui.progress.SwipeableSessionRow
import com.workouttracker.domain.BackupFormatException
import com.workouttracker.domain.ImportData
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanEstimator
import com.workouttracker.domain.PlanOrder
import com.workouttracker.domain.Progress
import com.workouttracker.domain.Session
import com.workouttracker.domain.Templates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class TodayState(
    val loaded: Boolean = false,
    val plans: List<Plan> = emptyList(),
    val recent: List<Session> = emptyList(),
    val week: List<Boolean> = List(7) { false },
    val weekCount: Int = 0,
    val weeklyGoal: Int = 3,
    val streak: Int = 0,
    val upNext: Plan? = null,
    val hasHistory: Boolean = false,
)

class TodayViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<TodayState> = combine(
        container.plans.observeAll(),
        container.sessions.observeAll(),
        container.settings.settings,
    ) { plans, sessions, settings ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val week = Progress.thisWeek(sessions, today, zone)
        TodayState(
            loaded = true,
            plans = plans,
            recent = sessions.take(5),
            week = week,
            weekCount = week.count { it },
            weeklyGoal = settings.weeklyGoal,
            streak = Progress.streakDays(sessions, today, zone),
            // Rotate through plans: the one done least recently (never-done first).
            upNext = plans.filter { it.totalSets > 0 }.minByOrNull { it.lastPerformedAt ?: Long.MIN_VALUE },
            hasHistory = sessions.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun delete(s: Session) = container.appScope.launch { container.sessions.delete(s.id) }
    fun restore(s: Session) = container.appScope.launch { container.sessions.save(s) }

    fun addTemplates() {
        container.appScope.launch {
            val now = System.currentTimeMillis()
            Templates.all(now).forEach { container.plans.restore(it) }
        }
    }

    fun import(uri: Uri, done: (Result<ImportData>) -> Unit) {
        container.appScope.launch {
            val r = runCatching { container.backup.read(uri) }
            r.onSuccess { container.backup.apply(it, replace = false) }
            withContext(Dispatchers.Main) { done(r) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onStartPlan: (Plan) -> Unit,
    onQuickWorkout: () -> Unit,
    onCreatePlan: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSeeHistory: () -> Unit,
    onRepeat: (Session) -> Unit,
) {
    val vm = appViewModel { TodayViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    var showStart by rememberSaveable { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri) { r ->
            r.onSuccess { messenger.show("Imported ${it.plans.size} plans and ${it.sessions.size} workouts") }
                .onFailure { e -> messenger.show((e as? BackupFormatException)?.message ?: "Couldn't import that file") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(greeting()) },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") } },
            )
        },
        floatingActionButton = {
            if (state.loaded && (state.plans.isNotEmpty() || state.hasHistory)) {
                ExtendedFloatingActionButton(
                    onClick = { showStart = true },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = { Text("Start workout") },
                )
            }
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.plans.isEmpty() && !state.hasHistory) {
                item {
                    Onboarding(
                        onTemplates = vm::addTemplates,
                        onBuild = onCreatePlan,
                        onQuick = onQuickWorkout,
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                }
                return@LazyColumn
            }
            item { WeekCard(state) }
            state.upNext?.let { plan -> item { UpNextCard(plan, onStart = { onStartPlan(plan) }) } }
            if (state.recent.isNotEmpty()) {
                item {
                    SectionHeader("Recent") { TextButton(onClick = onSeeHistory) { Text("All") } }
                }
                items(state.recent, key = { it.id }) { s ->
                    SwipeableSessionRow(
                        s,
                        onOpen = { onOpenSession(s.id) },
                        onRepeat = onRepeat,
                        onDelete = { gone ->
                            vm.delete(gone)
                            messenger.undo("Workout deleted") { vm.restore(gone) }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showStart) {
        StartSheet(
            plans = state.plans,
            onDismiss = { showStart = false },
            onQuick = { showStart = false; onQuickWorkout() },
            onPlan = { showStart = false; onStartPlan(it) },
        )
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun WeekCard(state: TodayState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.streak > 1) Text("🔥 ${state.streak}-day streak", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            val labels = listOf("M", "T", "W", "T", "F", "S", "S")
            val todayIndex = LocalDate.now().dayOfWeek.value - 1
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = "${state.weekCount} of ${state.weeklyGoal} workouts this week" },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                state.week.forEachIndexed { i, done ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val primary = MaterialTheme.colorScheme.primary
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(if (done) primary else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                                .then(if (i == todayIndex && !done) Modifier.border(2.dp, primary, CircleShape) else Modifier),
                        )
                        Text(labels[i], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val reached = state.weekCount >= state.weeklyGoal
            Text(
                if (reached) "Goal reached — ${state.weekCount}/${state.weeklyGoal} 🎉" else "${state.weekCount}/${state.weeklyGoal} workouts toward your weekly goal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpNextCard(plan: Plan, onStart: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("UP NEXT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(plan.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "${plan.exerciseCount} exercises · ~${PlanEstimator.estimateMinutes(plan.groups)} min · " +
                    (plan.lastPerformedAt?.let { "last done ${relativeDay(it)}" } ?: "not done yet"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    plan.groups.flatMap { g -> g.items.map { it.category } }.distinct().take(5).forEach { CategoryAvatar(it, size = 28.dp) }
                }
                Button(onClick = onStart) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
            }
        }
    }
}

@Composable
private fun Onboarding(onTemplates: () -> Unit, onBuild: () -> Unit, onQuick: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Let's get you training", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Plans are reusable workouts. Start with a ready-made one, build your own, or just log as you go.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onTemplates, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Add 5 starter plans") }
        OutlinedButton(onClick = onBuild, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Build my own plan") }
        OutlinedButton(onClick = onQuick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Outlined.Bolt, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Quick workout")
        }
        TextButton(onClick = onImport, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Import a backup from the web app") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartSheet(plans: List<Plan>, onDismiss: () -> Unit, onQuick: () -> Unit, onPlan: (Plan) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.7f).navigationBarsPadding()) {
            Text("Start a workout", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            ListItem(
                modifier = Modifier.clickable(onClick = onQuick),
                leadingContent = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
                headlineContent = { Text("Quick workout") },
                supportingContent = { Text("No plan — add exercises as you go") },
            )
            HorizontalDivider()
            val sorted = remember(plans) { PlanOrder.recentFirst(plans) }
            LazyColumn {
                items(sorted, key = { it.id }) { plan ->
                    ListItem(
                        modifier = Modifier.clickable(enabled = plan.totalSets > 0) { onPlan(plan) },
                        headlineContent = { Text(plan.name) },
                        supportingContent = {
                            Text(
                                "${plan.totalSets} sets · ~${PlanEstimator.estimateMinutes(plan.groups)} min" +
                                    (plan.lastPerformedAt?.let { " · ${relativeDay(it)}" } ?: ""),
                            )
                        },
                        trailingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${plan.name}") },
                    )
                }
            }
        }
    }
}
