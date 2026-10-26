package com.workouttracker.app.service

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.workouttracker.app.data.repo.Settings
import com.workouttracker.app.data.repo.WorkoutEvent

/**
 * Tells the user a rest or hold has ended.
 *
 * On screen we vibrate and beep ourselves. In the background we post a heads-up notification whose
 * channel supplies sound and vibration (the system plays those reliably while the app is asleep);
 * if notifications are blocked, or only one of sound/vibration is wanted, we fall back to doing
 * it ourselves so the Settings toggles are always honoured.
 */
class RestAlerts(private val context: Context, private val feedback: Feedback) {

    fun onEvent(event: WorkoutEvent, settings: Settings, nextLabel: String?) {
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (foreground) {
            feedback.alarm()
            return
        }
        val (title, text) = when (event) {
            WorkoutEvent.RestEnded -> "Rest's over" to (nextLabel?.let { "Next: $it" } ?: "Time for your next set")
            WorkoutEvent.HoldEnded -> "Time!" to "Hold complete — set logged"
        }
        val channelDoesBoth = settings.sound && settings.vibration
        val posted = WorkoutNotifications.canPost(context) &&
            WorkoutNotifications.postAlert(context, title, text, silent = !channelDoesBoth)
        if (!posted || !channelDoesBoth) {
            if (settings.vibration) feedback.buzzOnly()
            if (settings.sound) feedback.beepOnly()
        }
    }
}
