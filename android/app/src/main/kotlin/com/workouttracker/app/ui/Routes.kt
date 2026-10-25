package com.workouttracker.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val TODAY = "today"
    const val PLANS = "plans"
    const val PROGRESS = "progress"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYER = "player"

    const val PLAN_EDIT = "plan/{planId}"
    fun planEdit(planId: String = NEW) = "plan/$planId"

    const val EXERCISE_DETAIL = "exercise/{exerciseId}"
    fun exerciseDetail(id: String) = "exercise/$id"

    const val EXERCISE_EDIT = "exercise/{exerciseId}/edit"
    fun exerciseEdit(id: String = NEW) = "exercise/$id/edit"

    const val SESSION_DETAIL = "session/{sessionId}"
    fun sessionDetail(id: String) = "session/$id"

    const val NEW = "new"
}

enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    TODAY(Routes.TODAY, "Today", Icons.Outlined.Home),
    PLANS(Routes.PLANS, "Plans", Icons.AutoMirrored.Outlined.List),
    PROGRESS(Routes.PROGRESS, "Progress", Icons.Outlined.Insights),
    LIBRARY(Routes.LIBRARY, "Library", Icons.Outlined.FitnessCenter),
}
