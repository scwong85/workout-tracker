package com.workouttracker.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        PlanEntity::class,
        PlanGroupEntity::class,
        PlanItemEntity::class,
        SessionEntity::class,
        SessionSetEntity::class,
        ActiveWorkoutEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun planDao(): PlanDao
    abstract fun sessionDao(): SessionDao
    abstract fun activeWorkoutDao(): ActiveWorkoutDao

    companion object {
        const val NAME = "workout.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
