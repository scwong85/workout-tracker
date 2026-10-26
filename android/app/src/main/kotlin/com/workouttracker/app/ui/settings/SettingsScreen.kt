package com.workouttracker.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workouttracker.app.AppContainer
import com.workouttracker.app.BuildConfig
import com.workouttracker.app.data.repo.Settings
import com.workouttracker.app.service.WorkoutNotifications
import com.workouttracker.app.ui.appViewModel
import com.workouttracker.app.ui.components.LocalMessenger
import com.workouttracker.app.ui.components.StepperRow
import com.workouttracker.app.ui.theme.ThemeMode
import com.workouttracker.domain.BackupFormatException
import com.workouttracker.domain.BackupSource
import com.workouttracker.domain.Format
import com.workouttracker.domain.ImportData
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings> = container.settings.settings
    private val repo = container.settings
    private val scope = container.appScope

    /** Plays the rest-over alert and returns something the user can fix if it would be silent. */
    fun testAlert(): String? {
        container.feedback.alarm()
        return container.feedback.diagnose()
    }

    fun update(block: suspend com.workouttracker.app.data.repo.SettingsRepository.() -> Unit) {
        scope.launch { repo.block() }
    }

    /** Data work runs in the app scope so leaving the screen can't interrupt it; results come back on Main. */
    private fun <T> work(block: suspend () -> T, done: (Result<T>) -> Unit) {
        scope.launch {
            val result = runCatching { block() }
            withContext(Dispatchers.Main) { done(result) }
        }
    }

    fun export(uri: Uri, done: (Throwable?) -> Unit) = work({ container.backup.exportTo(uri) }) { done(it.exceptionOrNull()) }

    fun read(uri: Uri, done: (Result<ImportData>) -> Unit) = work({ container.backup.read(uri) }, done)

    fun apply(data: ImportData, replace: Boolean, done: (Throwable?) -> Unit) =
        work({ container.backup.apply(data, replace) }) { done(it.exceptionOrNull()) }

    fun deleteAll(done: () -> Unit) = work({ container.backup.deleteAll() }) { done() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    var preview by remember { mutableStateOf<ImportData?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Re-read when coming back from the system notification settings.
    var canNotify by remember { mutableStateOf(WorkoutNotifications.canPost(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canNotify = WorkoutNotifications.canPost(context) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri) { e -> messenger.show(if (e == null) "Backup saved" else "Couldn't save backup: ${e.message}") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.read(uri) { r ->
            r.onSuccess { preview = it }.onFailure { e ->
                messenger.show((e as? BackupFormatException)?.message ?: "Couldn't read that file")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Workout")
            ListItem(
                headlineContent = { Text("Weight unit") },
                trailingContent = {
                    SingleChoiceSegmentedButtonRow {
                        WeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(
                                selected = s.weightUnit == u,
                                onClick = { vm.update { setWeightUnit(u) } },
                                shape = SegmentedButtonDefaults.itemShape(i, WeightUnit.entries.size),
                            ) { Text(u.label) }
                        }
                    }
                },
            )
            StepperRow(
                label = "Default rest",
                value = Format.clock(s.defaultRestSec),
                onDecrement = { vm.update { setDefaultRest(s.defaultRestSec - 15) } },
                onIncrement = { vm.update { setDefaultRest(s.defaultRestSec + 15) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            StepperRow(
                label = "Weekly goal",
                value = "${s.weeklyGoal} workouts",
                onDecrement = { vm.update { setWeeklyGoal(s.weeklyGoal - 1) } },
                onIncrement = { vm.update { setWeeklyGoal(s.weeklyGoal + 1) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Toggle("Start next set when rest ends", s.autoAdvanceRest) { v -> vm.update { setAutoAdvanceRest(v) } }
            Toggle("Sound", s.sound) { v -> vm.update { setSound(v) } }
            Toggle("Vibration", s.vibration) { v -> vm.update { setVibration(v) } }
            ListItem(
                modifier = Modifier.clickable {
                    messenger.show(vm.testAlert() ?: "Played the rest-over alert. Didn't feel or hear it? Check the phone's volume and vibration settings.")
                },
                headlineContent = { Text("Test rest alert") },
                supportingContent = { Text("Plays the sound and vibration used when rest ends") },
            )
            ListItem(
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
                headlineContent = { Text("Notifications") },
                supportingContent = {
                    Text(
                        if (canNotify) "On. Rest alerts show when the app is in the background."
                        else "Off. You won't be alerted when rest ends with the screen off. Tap to turn on.",
                        color = if (canNotify) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                },
            )
            Toggle("Keep screen on during workouts", s.keepScreenOn) { v -> vm.update { setKeepScreenOn(v) } }

            Section("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = s.theme == m,
                                onClick = { vm.update { setTheme(m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toggle("Use wallpaper colours", s.dynamicColor) { v -> vm.update { setDynamicColor(v) } }
            }

            Section("Data")
            ListItem(
                modifier = Modifier.clickable { exportLauncher.launch("workout-tracker-${LocalDate.now()}.json") },
                headlineContent = { Text("Back up to a file") },
                supportingContent = { Text("Exercises, plans and history as JSON") },
            )
            ListItem(
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                headlineContent = { Text("Restore from a file") },
                supportingContent = { Text("Works with this app's backups and the web app's export") },
            )
            ListItem(
                modifier = Modifier.clickable { confirmDelete = true },
                headlineContent = { Text("Delete all data", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Removes all plans, history and custom exercises") },
            )

            Section("About")
            ListItem(headlineContent = { Text("Version") }, supportingContent = { Text(BuildConfig.VERSION_NAME) })
            Text(
                "Your data stays on this device. Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    preview?.let { data ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(if (data.source == BackupSource.WEB) "Import from web app" else "Restore backup") },
            text = {
                Text(
                    "This file has ${data.exercises.count { !it.isPreset }} custom exercises, ${data.plans.size} plans " +
                        "and ${data.sessions.size} workouts.\n\nMerge adds them to what you have. Replace deletes your " +
                        "current data first. A safety copy is kept either way.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    vm.apply(data, replace = false) { e -> messenger.show(if (e == null) "Imported" else "Import failed: ${e.message}") }
                }) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = {
                    preview = null
                    vm.apply(data, replace = true) { e -> messenger.show(if (e == null) "Data replaced" else "Import failed: ${e.message}") }
                }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    if (confirmDelete) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = {
                Column {
                    Text("This can't be undone from the app. Type DELETE to confirm.")
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.padding(top = 12.dp))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim().equals("DELETE", ignoreCase = false),
                    onClick = {
                        confirmDelete = false
                        vm.deleteAll { messenger.show("All data deleted") }
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
