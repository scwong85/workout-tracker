package com.workouttracker.app.data.repo

import com.workouttracker.app.data.db.AppDatabase
import com.workouttracker.app.data.db.setEntities
import com.workouttracker.app.data.db.toDomain
import com.workouttracker.app.data.db.toEntity
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanGroup
import com.workouttracker.domain.Session
import com.workouttracker.domain.SessionPlans
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SessionRepository(private val db: AppDatabase) {
    private val dao = db.sessionDao()
    private val exerciseDao = db.exerciseDao()

    /** Newest first. */
    fun observeAll(): Flow<List<Session>> =
        combine(dao.observeAll(), exerciseDao.observeAll()) { sessions, exercises ->
            val byId = exercises.associateBy { it.id }
            sessions.map { it.toDomain(byId) }
        }

    fun observe(id: String): Flow<Session?> =
        combine(dao.observe(id), exerciseDao.observeAll()) { s, exercises -> s?.toDomain(exercises.associateBy { it.id }) }

    suspend fun getAll(): List<Session> {
        val byId = exerciseDao.getAll().associateBy { it.id }
        return dao.getAll().map { it.toDomain(byId) }
    }

    /**
     * What "repeat this workout" should start: the saved plan if it still exists, otherwise
     * groups rebuilt from what was logged (skipped sets are left out).
     */
    suspend fun repeatSource(session: Session): Pair<Plan?, List<PlanGroup>> {
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val plan = session.planId?.let { db.planDao().get(it)?.toDomain(exercises) }
        if (plan != null && plan.totalSets > 0) return plan to plan.groups
        return null to SessionPlans.groupsFrom(session.sets, exercises.mapValues { it.value.category })
    }

    suspend fun save(session: Session) = dao.replace(session.toEntity(), session.setEntities())

    suspend fun delete(id: String) = dao.delete(id)
}
