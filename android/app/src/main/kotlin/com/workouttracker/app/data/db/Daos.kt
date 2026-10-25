package com.workouttracker.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: ExerciseEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<ExerciseEntity>)

    @Query("UPDATE exercise SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("DELETE FROM exercise")
    suspend fun clear()
}

@Dao
abstract class PlanDao {
    @Transaction
    @Query("SELECT * FROM plan ORDER BY updatedAt DESC")
    abstract fun observeAll(): Flow<List<PlanWithGroups>>

    @Transaction
    @Query("SELECT * FROM plan")
    abstract suspend fun getAll(): List<PlanWithGroups>

    @Transaction
    @Query("SELECT * FROM plan WHERE id = :id")
    abstract suspend fun get(id: String): PlanWithGroups?

    @Transaction
    @Query("SELECT * FROM plan WHERE id = :id")
    abstract fun observe(id: String): Flow<PlanWithGroups?>

    @Query("SELECT DISTINCT p.name FROM plan p JOIN plan_group g ON g.planId = p.id JOIN plan_item i ON i.groupId = g.id WHERE i.exerciseId = :exerciseId")
    abstract suspend fun planNamesUsing(exerciseId: String): List<String>

    @Upsert
    abstract suspend fun upsertPlan(plan: PlanEntity)

    @Insert
    abstract suspend fun insertGroups(groups: List<PlanGroupEntity>)

    @Insert
    abstract suspend fun insertItems(items: List<PlanItemEntity>)

    @Query("DELETE FROM plan_group WHERE planId = :planId")
    abstract suspend fun deleteGroups(planId: String)

    @Query("DELETE FROM plan WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("UPDATE plan SET lastPerformedAt = :at WHERE id = :id")
    abstract suspend fun markPerformed(id: String, at: Long)

    @Query("DELETE FROM plan")
    abstract suspend fun clear()

    /** Replaces a plan and its whole group/item tree. */
    @Transaction
    open suspend fun replace(plan: PlanEntity, groups: List<PlanGroupEntity>, items: List<PlanItemEntity>) {
        upsertPlan(plan)
        deleteGroups(plan.id)
        insertGroups(groups)
        insertItems(items)
    }
}

@Dao
abstract class SessionDao {
    @Transaction
    @Query("SELECT * FROM session ORDER BY startedAt DESC")
    abstract fun observeAll(): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM session")
    abstract suspend fun getAll(): List<SessionWithSets>

    @Transaction
    @Query("SELECT * FROM session WHERE id = :id")
    abstract fun observe(id: String): Flow<SessionWithSets?>

    @Upsert
    abstract suspend fun upsertSession(session: SessionEntity)

    @Query("DELETE FROM session_set WHERE sessionId = :sessionId")
    abstract suspend fun deleteSets(sessionId: String)

    @Insert
    abstract suspend fun insertSets(sets: List<SessionSetEntity>)

    @Query("DELETE FROM session WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("DELETE FROM session")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replace(session: SessionEntity, sets: List<SessionSetEntity>) {
        upsertSession(session)
        deleteSets(session.id)
        insertSets(sets)
    }
}

@Dao
interface ActiveWorkoutDao {
    @Query("SELECT * FROM active_workout WHERE id = 0")
    suspend fun get(): ActiveWorkoutEntity?

    @Upsert
    suspend fun put(entity: ActiveWorkoutEntity)

    @Query("DELETE FROM active_workout")
    suspend fun clear()
}
