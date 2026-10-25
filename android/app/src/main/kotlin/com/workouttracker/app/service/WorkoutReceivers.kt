package com.workouttracker.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.workouttracker.app.WorkoutApp
import com.workouttracker.domain.WorkoutEngine
import kotlinx.coroutines.launch

/** Handles the buttons on the ongoing notification. */
class WorkoutActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WorkoutApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.activeWorkout.update { w, now ->
                    when (intent.action) {
                        ACTION_DONE -> WorkoutEngine.complete(w, now)
                        ACTION_SKIP -> WorkoutEngine.skip(w, now)
                        ACTION_SKIP_REST -> WorkoutEngine.skipRest(w)
                        ACTION_ADD_REST -> WorkoutEngine.adjustRest(w, 15, now)
                        else -> w
                    }
                }
                WorkoutNotifications.cancelAlert(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.workouttracker.action.DONE"
        const val ACTION_SKIP = "com.workouttracker.action.SKIP"
        const val ACTION_SKIP_REST = "com.workouttracker.action.SKIP_REST"
        const val ACTION_ADD_REST = "com.workouttracker.action.ADD_REST"
    }
}

/** Woken by [WorkoutAlarms] when a rest or hold ends, so alerts fire even if the CPU was asleep. */
class WorkoutAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WorkoutApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.activeWorkout.tick()
            } finally {
                pending.finish()
            }
        }
    }
}
