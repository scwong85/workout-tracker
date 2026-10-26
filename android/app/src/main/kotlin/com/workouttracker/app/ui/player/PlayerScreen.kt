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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workouttracker.app.service.WorkoutNotifications
import com.workouttracker.app.ui.appContainer
import com.workouttracker.app.ui.appViewModel
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
import com.workouttracker.domain.SetOutcome
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
    val feedback = container.feedback
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

    val draft = remember { FinishDraft("${w.planName} (saved)") }
    val complete = reviewing || phase == Phase.Complete
    val current = w.current
    val nothingDone = w.log.none { it.outcome == SetOutcome.DONE }

    fun finishWorkout() {
        if (saving) return
        saving = true
        scope.launch {
            val session = vm.finish(
                draft.rating,
                draft.note,
                draft.planName.takeIf { draft.saveAsPlan && w.planId == null },
                draft.updatePlan,
            )
            messenger.show(if (session != null) "Workout saved" else "Workout ended")
            onSaved(session)
        }
    }

    // Everything in the workout is driven by swipes; what each direction does depends on the card.
    val colors = WorkoutTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val addExercise = SwipeAction("Add exercise", Icons.Outlined.Add, primary, flyOff = false) { feedback.added(); showPicker = true }
    val actions = when {
        complete -> SwipeActions(
            right = SwipeAction(if (nothingDone) "End workout" else "Save workout", Icons.Filled.Check, colors.success) { feedback.saved(); finishWorkout() },
            left = if (phase != Phase.Complete) {
                SwipeAction("Keep going", Icons.AutoMirrored.Filled.ArrowBack, colors.warning, flyOff = false) { feedback.added(); reviewing = false }
            } else null,
            up = SwipeAction("Add more", Icons.Outlined.Add, primary, flyOff = false) { feedback.added(); reviewing = false; showPicker = true },
        )
        phase is Phase.Resting -> SwipeActions(
            right = SwipeAction("Start next set", Icons.Filled.PlayArrow, colors.rest) { feedback.added(); vm.skipRest() },
            left = SwipeAction("+30s", Icons.Outlined.Timer, colors.rest, flyOff = false) { feedback.added(); vm.adjustRest(30) },
            up = addExercise,
        )
        phase is Phase.Performing && current != null -> SwipeActions(
            right = SwipeAction("Log set", Icons.Filled.Check, colors.success) {
                feedback.logged()
                vm.complete()
                messenger.undo("Set logged") { vm.undo() }
            },
            left = SwipeAction("Skip", Icons.Outlined.SkipNext, colors.warning) {
                feedback.skipped()
                vm.skip()
                messenger.undo("Set skipped") { vm.undo() }
            },
            up = addExercise,
        )
        else -> SwipeActions(up = addExercise)
    }
    val deckKey: Any = when {
        complete -> "complete"
        phase is Phase.Resting -> "rest-${current?.id}"
        phase is Phase.Performing && current != null -> current.id
        else -> "empty"
    }
    val cardColor = if (phase is Phase.Resting && !complete) {
        colors.rest.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surfaceContainer)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

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
            SwipeDeck(
                cardKey = deckKey,
                actions = actions,
                cardColor = cardColor,
                onArmed = feedback::tick,
                // Each action gives its own feedback above.
                onCommit = {},
                modifier = Modifier.weight(1f),
            ) {
                when {
                    complete -> CompleteContent(
                        workout = w,
                        now = now,
                        weightUnit = prefs.weightUnit,
                        finishedEarly = phase != Phase.Complete,
                        personalRecords = remember(w.log, history) { Records.newRecords(history, w.log, prefs.weightUnit) },
                        draft = draft,
                    )
                    phase is Phase.Resting -> RestContent(
                        rest = phase,
                        now = now,
                        next = current,
                        onAdjust = vm::adjustRest,
                    )
                    phase is Phase.Performing && current != null -> SetContent(
                        set = current,
                        phase = phase,
                        now = now,
                        weightUnit = prefs.weightUnit,
                        lastTime = { ex -> lastTimes[ex.exerciseId]?.let { "Last time: " + Records.describe(it, prefs.weightUnit) } },
                        onAdjust = vm::adjust,
                        onStartHold = vm::startHold,
                        onStopHold = vm::complete,
                    )
                    else -> EmptyCard()
                }
            }
            if (!complete && phase !is Phase.Resting) UpNextRow(w, onClick = { showQueue = true })
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
private fun EmptyCard() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Add your first exercise", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            "Swipe up to pick what you're doing. It'll appear here as a card.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
