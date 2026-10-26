package com.workouttracker.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.workouttracker.app.data.repo.Settings

/**
 * All vibration and sound in the app, behind the Vibration and Sound settings.
 *
 * Vibrations are sent with the ALARM usage so they still happen when the phone's "touch
 * feedback" is off or the ringer is silent (Compose's built-in haptics obey those settings,
 * which is why they can feel dead). The beep is a tone generator on the alarm stream, which
 * doesn't depend on the media volume.
 */
class Feedback(context: Context, private val settings: () -> Settings) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }

    // Kept so a fallback ringtone isn't garbage-collected while it plays.
    private var ringtone: Ringtone? = null

    /** Swipe crossed the commit threshold. */
    fun tick() = pulse(15)

    /** A set was logged. */
    fun logged() = pattern(longArrayOf(0, 40, 50, 40))

    /** A set was skipped. */
    fun skipped() = pulse(70)

    /** An exercise was added, or a small adjustment applied. */
    fun added() = pulse(30)

    /** Workout saved. */
    fun saved() = pattern(longArrayOf(0, 100, 60, 100, 60, 250))

    /** Rest over or hold finished: unmistakable pattern plus a beep. */
    fun alarm() {
        pattern(longArrayOf(0, 200, 120, 200, 120, 500))
        beep()
    }

    /** Sound only, for when the notification supplies the vibration. */
    fun beepOnly() = beep()

    /** Vibration only. */
    fun buzzOnly() = pattern(longArrayOf(0, 200, 120, 200, 120, 500))

    /** Something the user can fix if the test alert is silent, or null if nothing looks wrong. */
    fun diagnose(): String? {
        val s = settings()
        val audio = appContext.getSystemService(AudioManager::class.java)
        return when {
            !s.sound && !s.vibration -> "Sound and vibration are both turned off in Settings."
            s.vibration && vibrator?.hasVibrator() != true -> "This device has no vibration motor."
            s.sound && audio != null && audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0 ->
                "Alarm volume is at zero. Raise it in your phone's volume settings."
            else -> null
        }
    }

    private fun pulse(ms: Long) = vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))

    private fun pattern(timings: LongArray) = vibrate(VibrationEffect.createWaveform(timings, -1))

    private fun vibrate(effect: VibrationEffect) {
        if (!settings().vibration) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, ALARM_ATTRIBUTES)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "vibrate failed", e)
        }
    }

    private fun beep() {
        if (!settings().sound) return
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            // The generator must outlive the tone, then be released.
            main.postDelayed({ tone.release() }, 900)
        } catch (e: RuntimeException) {
            Log.w(TAG, "tone failed, falling back to the notification sound", e)
            playRingtone()
        }
    }

    private fun playRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            ringtone?.stop()
            ringtone = RingtoneManager.getRingtone(appContext, uri)?.apply {
                audioAttributes = ALARM_ATTRIBUTES
                play()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "ringtone failed", e)
        }
    }

    private companion object {
        const val TAG = "Feedback"
        val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
