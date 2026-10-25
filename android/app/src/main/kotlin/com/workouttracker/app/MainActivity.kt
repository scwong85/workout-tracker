package com.workouttracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workouttracker.app.service.WorkoutNotifications
import com.workouttracker.app.ui.AppRoot
import com.workouttracker.app.ui.theme.WorkoutTheme

class MainActivity : ComponentActivity() {
    /** Bumped each time the notification asks to open the player. */
    private var openPlayerSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as WorkoutApp).container
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            WorkoutTheme(mode = settings.theme, dynamicColor = settings.dynamicColor) {
                AppRoot(openPlayerSignal = openPlayerSignal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        (application as WorkoutApp).container.background.ensureServiceRunning()
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(WorkoutNotifications.EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerSignal++
            intent.removeExtra(WorkoutNotifications.EXTRA_OPEN_PLAYER)
        }
    }
}
