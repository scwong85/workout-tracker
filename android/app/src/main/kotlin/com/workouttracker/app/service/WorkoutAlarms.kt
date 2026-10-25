package com.workouttracker.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** One pending wake-up at the end of the current rest or hold. */
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
        val manager = alarms ?: return
        val exactAllowed = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, intent())
            } else {
                // Without the exact-alarm grant the system may delay this by a few minutes;
                // the notification chronometer still shows the right time.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, intent())
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, intent())
        }
    }

    fun cancel() {
        alarms?.cancel(intent())
    }
}
