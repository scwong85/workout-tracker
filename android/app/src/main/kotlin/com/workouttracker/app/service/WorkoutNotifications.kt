package com.workouttracker.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.workouttracker.app.MainActivity
import com.workouttracker.app.R
import com.workouttracker.domain.ActiveWorkout
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.Format
import com.workouttracker.domain.Phase

object WorkoutNotifications {
    const val CHANNEL_ONGOING = "workout_ongoing"
    /** v2: channel sound and vibration can't be changed after creation, so the old channel was replaced. */
    const val CHANNEL_ALERTS = "workout_alerts_v2"
    private const val CHANNEL_ALERTS_V1 = "workout_alerts"
    const val ID_ONGOING = 1
    const val ID_ALERT = 2

    const val EXTRA_OPEN_PLAYER = "open_player"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the current set and rest timer while you train."
                setShowBadge(false)
            },
        )
        manager.deleteNotificationChannel(CHANNEL_ALERTS_V1)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Rest finished", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts you when rest is over or a timed hold ends."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 120, 200, 120, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun canPost(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openPlayer(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WorkoutActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The ongoing notification; the chronometer counts down on its own, so it stays right while the CPU sleeps. */
    fun ongoing(context: Context, w: ActiveWorkout?): android.app.Notification {
        val b = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentIntent(openPlayer(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory("workout") // Notification.CATEGORY_WORKOUT; literal so older compat libs compile
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (w == null) return b.setContentTitle("Workout").build()
        b.setContentTitle(w.planName)
            .setProgress(w.queue.size.coerceAtLeast(1), w.cursor.coerceAtMost(w.queue.size), false)

        when (val phase = w.phase) {
            is Phase.Resting -> {
                b.setContentText("Rest · next: ${w.current?.exercises?.joinToString(" + ") { it.exerciseName } ?: "—"}")
                    .setWhen(phase.endsAt)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(0, "+15s", action(context, WorkoutActionReceiver.ACTION_ADD_REST, 1))
                    .addAction(0, "Skip rest", action(context, WorkoutActionReceiver.ACTION_SKIP_REST, 2))
            }
            is Phase.Performing -> {
                val set = w.current
                if (set == null) {
                    b.setContentText("Add exercises to begin")
                } else {
                    val first = set.exercises.first()
                    val target = if (first.type == ExerciseType.REPS) "${first.actualReps} reps" else Format.clock(first.actualDurationSec)
                    b.setContentText("Set ${w.cursor + 1} of ${w.queue.size} · ${set.exercises.joinToString(" + ") { it.exerciseName }} · $target")
                    val endsAt = phase.holdEndsAt
                    if (endsAt != null) {
                        b.setWhen(endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                    } else {
                        b.setShowWhen(false)
                    }
                    b.addAction(0, "Done", action(context, WorkoutActionReceiver.ACTION_DONE, 3))
                        .addAction(0, "Skip", action(context, WorkoutActionReceiver.ACTION_SKIP, 4))
                }
            }
            Phase.Complete -> b.setContentText("All sets done — tap to save your workout").setShowWhen(false)
        }
        return b.build()
    }

    /** Heads-up alert for rest/hold end. Posted only when the app isn't on screen. */
    /** Posts the heads-up alert. [silent] leaves the sound and vibration to the caller. Returns false if it couldn't be shown. */
    fun postAlert(context: Context, title: String, text: String, silent: Boolean): Boolean {
        if (!canPost(context)) return false
        val b = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPlayer(context))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSilent(silent)
        return try {
            NotificationManagerCompat.from(context).notify(ID_ALERT, b.build())
            true
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
            false
        }
    }

    fun cancelAlert(context: Context) = NotificationManagerCompat.from(context).cancel(ID_ALERT)
}
