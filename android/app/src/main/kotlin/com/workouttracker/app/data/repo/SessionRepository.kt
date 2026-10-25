package com.workouttracker.app.data.repo

import com.workouttracker.app.data.db.AppDatabase
import com.workouttracker.app.data.db.setEntities
import com.workouttracker.app.data.db.toDomain
import com.workouttracker.app.data.db.toEntity
import com.workouttracker.domain.Session
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

    suspend fun save(session: Session) = dao.replace(session.toEntity(), session.setEntities())

    suspend fun delete(id: String) = dao.delete(id)
}
