package com.workouttracker.app

import android.content.Context
import com.workouttracker.app.data.db.AppDatabase
import com.workouttracker.app.data.db.toEntity
import com.workouttracker.app.data.repo.ActiveWorkoutRepository
import com.workouttracker.app.data.repo.ExerciseRepository
import com.workouttracker.app.data.repo.PlanRepository
import com.workouttracker.app.data.repo.SessionRepository
import com.workouttracker.app.data.repo.SettingsRepository
import com.workouttracker.app.service.WorkoutBackground
import com.workouttracker.domain.Presets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase = AppDatabase.build(appContext)
    val exercises = ExerciseRepository(database)
    val plans = PlanRepository(database)
    val sessions = SessionRepository(database)
    val activeWorkout = ActiveWorkoutRepository(database, sessions, plans, appScope)
    val settings = SettingsRepository(appContext, appScope)
    val background = WorkoutBackground(appContext, activeWorkout, settings, appScope)

    fun start() {
        background.start()
        appScope.launch {
            // Idempotent: presets added in later app versions get inserted, existing rows are untouched.
            database.exerciseDao().insertIgnore(Presets.exercises.map { it.toEntity() })
        }
    }
}
