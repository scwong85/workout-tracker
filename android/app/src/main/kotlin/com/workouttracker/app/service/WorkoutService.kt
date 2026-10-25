package com.workouttracker.app.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.workouttracker.app.WorkoutApp
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive during a workout and owns the ongoing
 * notification. Timer state lives in [com.workouttracker.app.data.repo.ActiveWorkoutRepository];
 * this only mirrors it, so the service can be killed and recreated without losing anything.
 */
class WorkoutService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val container = (application as WorkoutApp).container
        val workout = container.activeWorkout.state.value
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        // startForeground must always be called after startForegroundService, even if the
        // workout ended in the meantime, or the system crashes the app.
        ServiceCompat.startForeground(this, WorkoutNotifications.ID_ONGOING, WorkoutNotifications.ongoing(this, workout), type)
        if (workout == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!observing) {
            observing = true
            lifecycleScope.launch {
                container.activeWorkout.state.collect { w ->
                    if (w == null) {
                        ServiceCompat.stopForeground(this@WorkoutService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (WorkoutNotifications.canPost(this@WorkoutService)) {
                        getSystemService(NotificationManager::class.java)
                            ?.notify(WorkoutNotifications.ID_ONGOING, WorkoutNotifications.ongoing(this@WorkoutService, w))
                    }
                }
            }
        }
        return START_STICKY
    }

    private var observing = false

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
            } catch (_: IllegalStateException) {
                // Background start not allowed (Android 12+); the app retries when it next comes to the foreground.
            } catch (_: SecurityException) {
                // Missing foreground-service permission on a modified ROM; the workout still works in-app.
            }
        }
    }
}
