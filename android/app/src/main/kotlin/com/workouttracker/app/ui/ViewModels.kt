package com.workouttracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workouttracker.app.AppContainer
import com.workouttracker.app.WorkoutApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as WorkoutApp).container

/** Creates a ViewModel scoped to the current nav destination, built from the app container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key) { create(container) }
}
