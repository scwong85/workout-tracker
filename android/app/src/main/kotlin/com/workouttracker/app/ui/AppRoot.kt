package com.workouttracker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.workouttracker.app.ui.components.LocalMessenger
import com.workouttracker.app.ui.library.ExerciseDetailScreen
import com.workouttracker.app.ui.library.ExerciseEditScreen
import com.workouttracker.app.ui.library.LibraryScreen
import com.workouttracker.app.ui.plans.PlanEditorScreen
import com.workouttracker.app.ui.plans.PlansScreen
import com.workouttracker.app.ui.player.MiniPlayer
import com.workouttracker.app.ui.player.PlayerScreen
import com.workouttracker.app.ui.components.Messenger

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val messenger = remember { Messenger(snackbar, uiScope) }
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = TopLevel.entries.any { top -> destination?.hierarchy?.any { it.route == top.route } == true }
    val container = appContainer()
    val activeWorkout by container.activeWorkout.state.collectAsStateWithLifecycle()
    val launcher = remember {
        WorkoutLauncher(container, uiScope) { navController.navigate(Routes.PLAYER) { launchSingleTop = true } }
    }

    CompositionLocalProvider(LocalMessenger provides messenger) {
        Scaffold(
            // Screens own their top insets; this scaffold only reserves room for the bottom bar.
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBottomBar) {
                    Column {
                        activeWorkout?.let { MiniPlayer(it, onResume = launcher::resume) }
                        NavigationBar {
                            TopLevel.entries.forEach { top ->
                                val selected = destination?.hierarchy?.any { it.route == top.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navController.navigateTopLevel(top.route) },
                                    icon = { Icon(top.icon, contentDescription = null) },
                                    label = { Text(top.label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            WorkoutConflictDialog(launcher)
            NavHost(
                navController = navController,
                startDestination = Routes.TODAY,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                composable(Routes.TODAY) { Placeholder("Today") }
                composable(Routes.PLANS) {
                    PlansScreen(
                        onEditPlan = { navController.navigate(Routes.planEdit(it)) },
                        onCreatePlan = { navController.navigate(Routes.planEdit()) },
                        onStartPlan = launcher::startPlan,
                    )
                }
                composable(Routes.PLAYER) {
                    PlayerScreen(
                        onClose = { navController.popBackStack(Routes.PLAYER, inclusive = true) },
                        onSaved = { navController.popBackStack(Routes.PLAYER, inclusive = true) },
                    )
                }
                composable(Routes.PLAN_EDIT) { entry ->
                    PlanEditorScreen(
                        planId = entry.arguments?.getString("planId") ?: Routes.NEW,
                        onDone = { navController.popBackStack() },
                    )
                }
                composable(Routes.PROGRESS) { Placeholder("Progress") }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onOpenExercise = { navController.navigate(Routes.exerciseDetail(it)) },
                        onCreateExercise = { navController.navigate(Routes.exerciseEdit()) },
                    )
                }
                composable(Routes.EXERCISE_DETAIL) { entry ->
                    ExerciseDetailScreen(
                        exerciseId = entry.arguments?.getString("exerciseId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.exerciseEdit(it)) },
                    )
                }
                composable(Routes.EXERCISE_EDIT) { entry ->
                    ExerciseEditScreen(
                        exerciseId = entry.arguments?.getString("exerciseId") ?: Routes.NEW,
                        onDone = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
