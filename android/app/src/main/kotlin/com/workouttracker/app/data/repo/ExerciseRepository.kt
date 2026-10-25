package com.workouttracker.app.data.repo

import com.workouttracker.app.data.db.AppDatabase
import com.workouttracker.app.data.db.toDomain
import com.workouttracker.app.data.db.toEntity
import com.workouttracker.domain.Exercise
import com.workouttracker.domain.Ids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.exerciseDao()

    /** Every exercise, archived ones included (history and plans still reference them). */
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<List<Exercise>> = observeAll().map { list -> list.filterNot { it.isArchived } }

    suspend fun get(id: String): Exercise? = dao.get(id)?.toDomain()

    /** Inserts or updates; returns the saved exercise (with an id and timestamps). */
    suspend fun save(exercise: Exercise): Exercise {
        val now = clock()
        val existing = exercise.id.takeIf { it.isNotBlank() }?.let { dao.get(it) }
        val saved = exercise.copy(
            id = existing?.id ?: exercise.id.ifBlank { Ids.new() },
            name = exercise.name.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(saved.toEntity())
        return saved
    }

    suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived, clock())

    suspend fun planNamesUsing(id: String): List<String> = db.planDao().planNamesUsing(id)
}
