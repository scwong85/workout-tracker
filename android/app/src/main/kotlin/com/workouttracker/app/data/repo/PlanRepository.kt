package com.workouttracker.app.data.repo

import com.workouttracker.app.data.db.AppDatabase
import com.workouttracker.app.data.db.toDomain
import com.workouttracker.app.data.db.toRows
import com.workouttracker.domain.Ids
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanEdits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class PlanRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.planDao()
    private val exerciseDao = db.exerciseDao()

    fun observeAll(): Flow<List<Plan>> =
        combine(dao.observeAll(), exerciseDao.observeAll()) { plans, exercises ->
            val byId = exercises.associateBy { it.id }
            plans.map { it.toDomain(byId) }
        }

    fun observe(id: String): Flow<Plan?> =
        combine(dao.observe(id), exerciseDao.observeAll()) { plan, exercises ->
            plan?.toDomain(exercises.associateBy { it.id })
        }

    suspend fun get(id: String): Plan? {
        val plan = dao.get(id) ?: return null
        return plan.toDomain(exerciseDao.getAll().associateBy { it.id })
    }

    suspend fun save(plan: Plan): Plan {
        val now = clock()
        val existing = plan.id.takeIf { it.isNotBlank() }?.let { dao.get(it) }
        val saved = plan.copy(
            id = existing?.plan?.id ?: plan.id.ifBlank { Ids.new() },
            name = plan.name.trim(),
            createdAt = existing?.plan?.createdAt ?: plan.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
            lastPerformedAt = existing?.plan?.lastPerformedAt ?: plan.lastPerformedAt,
        )
        val rows = saved.toRows()
        dao.replace(rows.plan, rows.groups, rows.items)
        return saved
    }

    /** Restores a plan exactly as it was (used by Undo), keeping its timestamps. */
    suspend fun restore(plan: Plan) {
        val rows = plan.toRows()
        dao.replace(rows.plan, rows.groups, rows.items)
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun duplicate(id: String): Plan? {
        val plan = get(id) ?: return null
        val copy = PlanEdits.duplicate(plan, clock())
        restore(copy)
        return copy
    }

    suspend fun markPerformed(id: String, at: Long) = dao.markPerformed(id, at)
}
