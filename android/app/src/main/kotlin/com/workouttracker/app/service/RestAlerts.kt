package com.workouttracker.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.workouttracker.app.data.repo.Settings
import com.workouttracker.app.data.repo.WorkoutEvent

/** Sound and vibration when a rest or hold ends; a heads-up notification when the app is in the background. */
class RestAlerts(private val context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun onEvent(event: WorkoutEvent, settings: Settings, nextLabel: String?) {
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val (title, text) = when (event) {
            WorkoutEvent.RestEnded -> "Rest's over" to (nextLabel?.let { "Next: $it" } ?: "Time for your next set")
            WorkoutEvent.HoldEnded -> "Time!" to "Hold complete — set logged"
        }
        if (foreground) {
            if (settings.vibration) vibrate()
            if (settings.sound) beep()
        } else {
            // The alert channel makes the sound; vibrate ourselves so the pattern is consistent.
            if (settings.vibration) vibrate()
            WorkoutNotifications.postAlert(context, title, text, sound = settings.sound, vibrate = settings.vibration)
        }
    }

    private fun vibrate() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180, 120, 400), -1))
    }

    private fun beep() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.play()
    }
}
