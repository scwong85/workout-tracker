package com.workouttracker.app.ui.player

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workouttracker.app.service.WorkoutNotifications
import com.workouttracker.app.ui.appContainer
import com.workouttracker.app.ui.appViewModel
import com.workouttracker.app.ui.components.EmptyState
import com.workouttracker.app.ui.components.ExercisePickerSheet
import com.workouttracker.app.ui.components.LocalMessenger
import com.workouttracker.app.ui.components.StepperRow
import com.workouttracker.app.ui.components.rememberNow
import com.workouttracker.app.ui.theme.WorkoutTheme
import com.workouttracker.domain.ActiveWorkout
import com.workouttracker.domain.Format
import com.workouttracker.domain.Phase
import com.workouttracker.domain.PlanDefaults
import com.workouttracker.domain.Records
import com.workouttracker.domain.Session
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.launch

/** Player-wide preferences, supplied by settings. */
data class PlayerPrefs(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val keepScreenOn: Boolean = true,
    val defaultRestSec: Int = PlanDefaults.REST_SEC,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onSaved: (Session?) -> Unit,
    prefs: PlayerPrefs = PlayerPrefs(),
) {
    val vm = appViewModel { PlayerViewModel(it) }
    val workout by vm.workout.collectAsStateWithLifecycle()
    val loaded by vm.isLoaded.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val lastTimes by vm.lastTimes.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // The workout can end elsewhere (notification, another screen); leave when it's gone.
    LaunchedEffect(loaded, workout == null, saving) {
        if (loaded && workout == null && !saving) onClose()
    }
    BackHandler { onClose() }

    if (prefs.keepScreenOn) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }

    // Ask for notifications in context, the first time a workout is opened (Android 13+).
    val container = appContainer()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.appScope.launch { container.settings.setAskedNotifications() }
        container.background.ensureServiceRunning()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !container.settings.settings.value.askedNotifications && !WorkoutNotifications.canPost(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val w = workout ?: return
    // Fast ticks only while a countdown is on screen.
    val phase = w.phase
    val timed = phase is Phase.Resting || (phase is Phase.Performing && phase.holdEndsAt != null)
    val now by rememberNow(if (timed) 50 else 1_000)

    // An empty quick workout starts by choosing exercises.
    LaunchedEffect(w.queue.isEmpty()) { if (w.queue.isEmpty()) showPicker = true }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.ExpandMore, contentDescription = "Minimise workout") }
                },
                title = {
                    Column {
                        Text(w.planName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Format.elapsed(now - w.startedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Workout options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Up next") }, onClick = { menu = false; showQueue = true })
                            DropdownMenuItem(text = { Text("Add exercise") }, onClick = { menu = false; showPicker = true })
                            DropdownMenuItem(text = { Text("Finish workout") }, onClick = { menu = false; reviewing = true })
                            DropdownMenuItem(text = { Text("Discard workout") }, onClick = { menu = false; confirmDiscard = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ProgressStrip(w)
            val current = w.current
            when {
                reviewing || phase == Phase.Complete -> CompleteView(
                    workout = w,
                    now = now,
                    weightUnit = prefs.weightUnit,
                    finishedEarly = phase != Phase.Complete,
                    personalRecords = remember(w.log, history) { Records.newRecords(history, w.log, prefs.weightUnit) },
                    saving = saving,
                    onAddMore = { reviewing = false; showPicker = true },
                    onKeepGoing = if (phase != Phase.Complete) ({ reviewing = false }) else null,
                    onFinish = { choices ->
                        saving = true
                        scope.launch {
                            val session = vm.finish(choices.rating, choices.note, choices.saveAsPlanName, choices.updatePlan)
                            messenger.show(if (session != null) "Workout saved" else "Workout ended")
                            onSaved(session)
                        }
                    },
                )
                phase is Phase.Resting -> RestView(
                    rest = phase,
                    now = now,
                    next = current,
                    onAdjust = vm::adjustRest,
                    onSkip = vm::skipRest,
                    onAdd = { showPicker = true },
                )
                phase is Phase.Performing && current != null -> Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        SwipeCard(
                            cardKey = current.id,
                            onDone = {
                                vm.complete()
                                messenger.undo("Set logged") { vm.undo() }
                            },
                            onSkip = {
                                vm.skip()
                                messenger.undo("Set skipped") { vm.undo() }
                            },
                            onAdd = { showPicker = true },
                        ) {
                            SetContent(
                                set = current,
                                phase = phase,
                                now = now,
                                weightUnit = prefs.weightUnit,
                                lastTime = { ex -> lastTimes[ex.exerciseId]?.let { "Last time: " + Records.describe(it, prefs.weightUnit) } },
                                onAdjust = vm::adjust,
                                onStartHold = vm::startHold,
                                onStopHold = vm::complete,
                            )
                        }
                        Text(
                            "Swipe right to log · left to skip · up to add",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                        )
                    }
                    UpNextRow(w, onClick = { showQueue = true })
                    ActionBar(
                        onSkip = {
                            vm.skip()
                            messenger.undo("Set skipped") { vm.undo() }
                        },
                        onDone = {
                            vm.complete()
                            messenger.undo("Set logged") { vm.undo() }
                        },
                        onAdd = { showPicker = true },
                    )
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Outlined.Add, "Add your first exercise", "Pick what you're doing and it'll appear here as a card.") {
                        Button(onClick = { showPicker = true }) { Text("Add exercises") }
                    }
                }
            }
        }
    }

    if (showPicker) {
        var sets by remember { mutableIntStateOf(PlanDefaults.SETS) }
        var rest by remember { mutableIntStateOf(prefs.defaultRestSec) }
        ExercisePickerSheet(
            exercises = exercises,
            title = "Add to workout",
            onDismiss = { showPicker = false },
            subtitleFor = { ex -> lastTimes[ex.id]?.let { "Last: " + Records.describe(it, prefs.weightUnit) } },
            footer = {
                StepperRow("Sets each", sets.toString(), { sets = (sets - 1).coerceAtLeast(1) }, { sets = (sets + 1).coerceAtMost(20) })
                StepperRow("Rest", Format.clock(rest), { rest = (rest - 15).coerceAtLeast(0) }, { rest = (rest + 15).coerceAtMost(600) })
            },
            onConfirm = { selected, superset ->
                vm.inject(selected, superset, sets, rest)
                showPicker = false
                reviewing = false
            },
        )
    }

    if (showQueue) {
        QueueSheet(
            workout = w,
            onDismiss = { showQueue = false },
            onJump = vm::jumpTo,
            onMove = vm::moveQueued,
            onRemove = vm::removeQueued,
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("Nothing from this workout will be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.discard()
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProgressStrip(w: ActiveWorkout) {
    val total = w.queue.size
    if (total == 0) return
    val done = w.cursor.coerceAtMost(total)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (total <= 30) {
            Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(total) { i ->
                    val color = when {
                        i < done -> MaterialTheme.colorScheme.primary
                        i == done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                    Box(Modifier.weight(1f).height(6.dp).background(color, RoundedCornerShape(3.dp)))
                }
            }
        } else {
            LinearProgressIndicator(progress = { w.progress }, modifier = Modifier.fillMaxWidth())
        }
        Text(
            "$done / $total sets",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
        )
    }
}

@Composable
private fun UpNextRow(w: ActiveWorkout, onClick: () -> Unit) {
    val next = w.next
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Up next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(next?.summary() ?: "Last set", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Show queue")
    }
}

@Composable
private fun ActionBar(onSkip: () -> Unit, onDone: () -> Unit, onAdd: () -> Unit) {
    val colors = WorkoutTheme.colors
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onSkip,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.warning),
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Icon(Icons.Outlined.SkipNext, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Skip")
            }
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = colors.success, contentColor = colors.onSuccess),
                modifier = Modifier.weight(2f).height(64.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
        }
        TextButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add exercise  ·  or swipe up")
        }
    }
}
