package com.workouttracker.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Tokens from docs/02-UX-REDESIGN.md §4.1.
internal val DarkSurface = Color(0xFF0F1115)
internal val DarkSurfaceContainer = Color(0xFF181B21)
internal val DarkSurfaceContainerHigh = Color(0xFF22262E)
internal val DarkOnSurface = Color(0xFFECEDEF)
internal val DarkOnSurfaceVariant = Color(0xFFA3A8B3)
internal val DarkPrimary = Color(0xFF3D8BFF)
internal val DarkSuccess = Color(0xFF2FD27A)
internal val DarkRest = Color(0xFF9B8CFF)
internal val DarkWarning = Color(0xFFFFB547)
internal val DarkError = Color(0xFFFF6B6B)

internal val LightSurface = Color(0xFFFAFAFB)
internal val LightSurfaceContainer = Color(0xFFFFFFFF)
internal val LightSurfaceContainerHigh = Color(0xFFF1F2F5)
internal val LightOnSurface = Color(0xFF14161A)
internal val LightOnSurfaceVariant = Color(0xFF5B616C)
internal val LightPrimary = Color(0xFF1F6FEB)
internal val LightSuccess = Color(0xFF10A85A)
internal val LightRest = Color(0xFF6B5BE6)
internal val LightWarning = Color(0xFFC77A00)
internal val LightError = Color(0xFFD93636)

/** Semantic colours that Material 3's scheme has no slot for. */
@Immutable
data class WorkoutColors(
    val success: Color,
    val onSuccess: Color,
    val rest: Color,
    val onRest: Color,
    val warning: Color,
    val onWarning: Color,
)

internal val DarkWorkoutColors = WorkoutColors(
    success = DarkSuccess, onSuccess = Color(0xFF00210F),
    rest = DarkRest, onRest = Color(0xFF14004A),
    warning = DarkWarning, onWarning = Color(0xFF2A1800),
)

internal val LightWorkoutColors = WorkoutColors(
    success = LightSuccess, onSuccess = Color.White,
    rest = LightRest, onRest = Color.White,
    warning = LightWarning, onWarning = Color.White,
)

val LocalWorkoutColors = staticCompositionLocalOf { DarkWorkoutColors }
