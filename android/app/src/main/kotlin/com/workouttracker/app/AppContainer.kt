package com.workouttracker.app

import android.content.Context
import com.workouttracker.app.data.db.AppDatabase
import com.workouttracker.app.data.db.toEntity
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

    fun start() {
        appScope.launch {
            // Idempotent: presets added in later app versions get inserted, existing rows are untouched.
            database.exerciseDao().insertIgnore(Presets.exercises.map { it.toEntity() })
        }
    }
}
