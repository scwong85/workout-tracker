package com.workouttracker.app.service

import android.content.Context
import com.workouttracker.app.data.repo.ActiveWorkoutRepository
import com.workouttracker.app.data.repo.SettingsRepository
import com.workouttracker.domain.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Glue between the active workout and Android: runs the foreground service while a workout
 * exists, keeps one alarm armed for the next timer end, and turns timer events into alerts.
 */
class WorkoutBackground(
    private val context: Context,
    private val repo: ActiveWorkoutRepository,
    private val settings: SettingsRepository,
    feedback: Feedback,
    private val scope: CoroutineScope,
) {
    private val alarms = WorkoutAlarms(context)
    private val alerts = RestAlerts(context, feedback)

    fun start() {
        WorkoutNotifications.createChannels(context)
        repo.autoAdvanceRest = { settings.settings.value.autoAdvanceRest }

        scope.launch {
            repo.state.map { it != null }.distinctUntilChanged().collect { active ->
                if (active) WorkoutService.start(context) else WorkoutNotifications.cancelAlert(context)
            }
        }

        scope.launch {
            repo.state
                .map { w ->
                    when (val phase = w?.phase) {
                        is Phase.Resting -> phase.endsAt.takeIf { it > repo.now() }
                        is Phase.Performing -> phase.holdEndsAt
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collect { wakeAt -> if (wakeAt == null) alarms.cancel() else alarms.schedule(wakeAt) }
        }

        // UNDISPATCHED registers the subscriber before start() returns, so an alarm that wakes a
        // freshly started process can't emit before anyone is listening.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repo.events.collect { event ->
                val next = repo.state.value?.current?.exercises?.joinToString(" + ") { it.exerciseName }
                alerts.onEvent(event, settings.settings.value, next)
            }
        }
    }

    /** Called when the app returns to the foreground, in case a background start was refused. */
    fun ensureServiceRunning() {
        if (repo.state.value != null) WorkoutService.start(context)
    }
}
