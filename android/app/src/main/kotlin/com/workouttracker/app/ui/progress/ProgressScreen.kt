package com.workouttracker.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.workouttracker.app.AppContainer
import com.workouttracker.app.ui.appViewModel
import com.workouttracker.app.ui.components.BarChart
import com.workouttracker.app.ui.components.CategoryAvatar
import com.workouttracker.app.ui.components.CategorySplit
import com.workouttracker.app.ui.components.EmptyState
import com.workouttracker.app.ui.components.SectionHeader
import com.workouttracker.app.ui.components.relativeDay
import com.workouttracker.app.ui.components.shortDateTime
import com.workouttracker.app.ui.theme.WorkoutTheme
import com.workouttracker.domain.Exercise
import com.workouttracker.domain.Format
import com.workouttracker.domain.Overview
import com.workouttracker.domain.PrEvent
import com.workouttracker.domain.Progress
import com.workouttracker.domain.Session
import com.workouttracker.domain.SessionStatus
import com.workouttracker.domain.StatRange
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class ProgressState(
    val loaded: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val overview: Overview? = null,
    val recentPrs: List<PrEvent> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
)

class ProgressViewModel(container: AppContainer) : ViewModel() {
    val range = MutableStateFlow(StatRange.MONTH)

    val state: StateFlow<ProgressState> = combine(
        container.sessions.observeAll(),
        container.exercises.observeAll(),
        range,
        container.settings.settings,
    ) { sessions, exercises, r, settings ->
        val zone = ZoneId.systemDefault()
        ProgressState(
            loaded = true,
            sessions = sessions,
            exercises = exercises,
            overview = Progress.overview(sessions, exercises.associate { it.id to it.category }, r, LocalDate.now(zone), zone),
            recentPrs = Progress.prTimeline(sessions, settings.weightUnit).takeLast(5).reversed(),
            weightUnit = settings.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(onOpenSession: (String) -> Unit, onOpenExercise: (String) -> Unit) {
    val vm = appViewModel { ProgressViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("Progress") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("Overview", "History", "Exercises").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            if (!state.loaded) return@Column
            if (state.sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Outlined.Insights, "No workouts yet", "Finish a workout and your progress, history and records will show up here.")
                }
                return@Column
            }
            when (tab) {
                0 -> OverviewTab(state, range, onRange = { vm.range.value = it }, onOpenSession)
                1 -> HistoryTab(state.sessions, onOpenSession)
                else -> ExercisesTab(state, onOpenExercise)
            }
        }
    }
}

@Composable
private fun OverviewTab(state: ProgressState, range: StatRange, onRange: (StatRange) -> Unit, onOpenSession: (String) -> Unit) {
    val o = state.overview ?: return
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatRange.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = r == range,
                        onClick = { onRange(r) },
                        shape = SegmentedButtonDefaults.itemShape(i, StatRange.entries.size),
                    ) { Text(r.label) }
                }
            }
        }
        item {
            val prev = o.previous
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiTile("Workouts", o.current.workouts.toString(), prev?.let { Progress.delta(o.current.workouts.toDouble(), it.workouts.toDouble()) }, Modifier.weight(1f))
                    KpiTile("Active time", Format.minutes(o.current.activeMs), prev?.let { Progress.delta(o.current.activeMs.toDouble(), it.activeMs.toDouble()) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiTile("Sets", o.current.sets.toString(), prev?.let { Progress.delta(o.current.sets.toDouble(), it.sets.toDouble()) }, Modifier.weight(1f))
                    KpiTile("Volume", Format.volume(o.current.volumeKg, state.weightUnit), prev?.let { Progress.delta(o.current.volumeKg, it.volumeKg) }, Modifier.weight(1f))
                }
            }
        }
        item {
            OverviewCard("Workouts") { BarChart(o.buckets) }
        }
        if (o.muscleSplit.isNotEmpty()) {
            item { OverviewCard("Sets by muscle group") { CategorySplit(o.muscleSplit) } }
        }
        if (state.recentPrs.isNotEmpty()) {
            item {
                OverviewCard("Recent personal bests") {
                    state.recentPrs.forEach { pr ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenSession(pr.sessionId) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🏆", modifier = Modifier.padding(end = 8.dp))
                            Text(pr.text, modifier = Modifier.weight(1f))
                            Text(relativeDay(pr.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A headline number with its change against the previous equal-length period. */
@Composable
private fun KpiTile(label: String, value: String, delta: Double?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            if (delta != null) {
                val pct = (delta * 100).roundToInt()
                val color = if (pct > 0) WorkoutTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    (if (pct > 0) "▲ $pct%" else if (pct < 0) "▼ ${-pct}%" else "No change") + " vs previous",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun HistoryTab(sessions: List<Session>, onOpenSession: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    var month by remember { mutableStateOf(YearMonth.now(zone)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    val counts = remember(sessions) { Progress.dayCounts(sessions, zone) }
    val visible = sessions.filter { s ->
        val d = Progress.date(s.startedAt, zone)
        if (selectedDay != null) d == selectedDay else YearMonth.from(d) == month
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            MonthCalendar(
                month = month,
                counts = counts,
                selected = selectedDay,
                onPrev = { month = month.minusMonths(1); selectedDay = null },
                onNext = { month = month.plusMonths(1); selectedDay = null },
                onSelect = { selectedDay = if (selectedDay == it) null else it },
            )
        }
        item {
            SectionHeader(
                selectedDay?.let { "Workouts on ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" }
                    ?: "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            ) {
                if (selectedDay != null) TextButton(onClick = { selectedDay = null }) { Text("Whole month") }
            }
        }
        if (visible.isEmpty()) {
            item { Text("No workouts.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        }
        items(visible, key = { it.id }) { s -> SessionRow(s) { onOpenSession(s.id) } }
    }
}

@Composable
fun SessionRow(s: Session, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.planName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${shortDateTime(s.startedAt)} · ${Format.minutes(s.durationMs)} · ${s.doneSets.size} sets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.status == SessionStatus.PARTIAL) {
                Text("Partial", style = MaterialTheme.typography.labelMedium, color = WorkoutTheme.colors.warning)
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    counts: Map<LocalDate, Int>,
    selected: LocalDate?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month") }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext, enabled = month < YearMonth.from(today)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Row {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val leading = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
            val cells = leading + month.lengthOfMonth()
            val rows = (cells + 6) / 7
            val primary = MaterialTheme.colorScheme.primary
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - leading + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                val count = counts[date] ?: 0
                                val bg = when {
                                    count >= 2 -> primary
                                    count == 1 -> primary.copy(alpha = 0.55f)
                                    else -> Color.Transparent
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(bg, CircleShape)
                                        .then(if (selected == date) Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape) else Modifier)
                                        .clickable(enabled = count > 0) { onSelect(date) }
                                        .semantics { contentDescription = "$date: $count workouts" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        day.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = when {
                                            count > 0 -> MaterialTheme.colorScheme.onPrimary
                                            date == today -> primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExercisesTab(state: ProgressState, onOpenExercise: (String) -> Unit) {
    val byId = state.exercises.associateBy { it.id }
    val ranked = remember(state.sessions) {
        state.sessions.flatMap { it.doneSets }.groupingBy { it.exerciseId }.eachCount().toList().sortedByDescending { it.second }
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(ranked, key = { it.first }) { (id, count) ->
            val ex = byId[id]
            ListItem(
                modifier = Modifier.clickable(enabled = ex != null) { onOpenExercise(id) },
                leadingContent = { ex?.let { CategoryAvatar(it.category) } },
                headlineContent = { Text(ex?.name ?: "Removed exercise") },
                supportingContent = { Text("$count sets logged") },
            )
        }
    }
}
