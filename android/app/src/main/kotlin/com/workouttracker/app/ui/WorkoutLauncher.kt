package com.workouttracker.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.workouttracker.app.AppContainer
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanGroup
import com.workouttracker.domain.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Starts workouts from anywhere, asking first if one is already in progress. */
class WorkoutLauncher(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val openPlayer: () -> Unit,
) {
    sealed interface Request {
        data class FromPlan(val plan: Plan) : Request
        data class AdHoc(val name: String, val groups: List<PlanGroup>) : Request
        data object Quick : Request
    }

    var pending by mutableStateOf<Request?>(null)
        private set

    fun startPlan(plan: Plan) = request(Request.FromPlan(plan))
    fun startQuick() = request(Request.Quick)

    /** "Repeat this workout": the saved plan if it still exists, otherwise what was logged. */
    fun startSession(session: Session) {
        scope.launch {
            val (plan, groups) = container.sessions.repeatSource(session)
            if (plan != null) startPlan(plan) else startAdHoc(session.planName, groups)
        }
    }
    fun startAdHoc(name: String, groups: List<PlanGroup>) = request(Request.AdHoc(name, groups))
    fun resume() = openPlayer()

    private fun request(r: Request) {
        if (container.activeWorkout.state.value != null) pending = r else launch(r)
    }

    fun replaceCurrent() {
        val r = pending ?: return
        pending = null
        launch(r)
    }

    fun dismiss() {
        pending = null
    }

    private fun launch(r: Request) {
        scope.launch {
            when (r) {
                is Request.FromPlan -> container.activeWorkout.start(r.plan)
                is Request.AdHoc -> container.activeWorkout.startAdHoc(r.name, r.groups)
                Request.Quick -> container.activeWorkout.startEmpty()
            }
            openPlayer()
        }
    }
}

@Composable
fun WorkoutConflictDialog(launcher: WorkoutLauncher) {
    if (launcher.pending == null) return
    AlertDialog(
        onDismissRequest = launcher::dismiss,
        title = { Text("Workout in progress") },
        text = { Text("Resume it, or discard it and start the new one?") },
        confirmButton = { TextButton(onClick = { launcher.dismiss(); launcher.resume() }) { Text("Resume") } },
        dismissButton = { TextButton(onClick = launcher::replaceCurrent) { Text("Discard & start") } },
    )
}
