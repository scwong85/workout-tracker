package com.workouttracker.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * One pending wake-up at the end of the current rest or hold.
 *
 * Uses setAlarmClock: it fires on time even in Doze and needs no special permission, unlike
 * exact alarms, which are denied by default on recent Android and then fire minutes late. The
 * trade-off is a small alarm icon in the status bar while resting, which suits a rest timer.
 */
class WorkoutAlarms(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun intent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WorkoutAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(atEpochMs: Long) {
        alarms?.setAlarmClock(AlarmManager.AlarmClockInfo(atEpochMs, WorkoutNotifications.openPlayer(context)), intent())
    }

    fun cancel() {
        alarms?.cancel(intent())
    }
}
