package com.workouttracker.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** Wall-clock time that recomposes every [periodMs]; use a short period only while a timer is visible. */
@Composable
fun rememberNow(periodMs: Long = 250): State<Long> =
    produceState(System.currentTimeMillis(), periodMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(periodMs)
        }
    }
