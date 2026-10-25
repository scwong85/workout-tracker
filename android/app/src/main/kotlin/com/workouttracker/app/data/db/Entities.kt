package com.workouttracker.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.workouttracker.domain.Category
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.SessionStatus
import com.workouttracker.domain.SetOutcome

@Entity(tableName = "exercise", indices = [Index("name"), Index("category")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: ExerciseType,
    val category: Category,
    val defaultWeightKg: Double,
    val isPreset: Boolean,
    val isArchived: Boolean,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPerformedAt: Long?,
)

@Entity(
    tableName = "plan_group",
    foreignKeys = [ForeignKey(PlanEntity::class, ["id"], ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId")],
)
data class PlanGroupEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val position: Int,
    val restAfterSec: Int,
)

@Entity(
    tableName = "plan_item",
    foreignKeys = [ForeignKey(PlanGroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupId"), Index("exerciseId")],
)
data class PlanItemEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val reps: Int,
    val durationSec: Int,
    val weightKg: Double,
)

@Entity(tableName = "session", indices = [Index("startedAt"), Index("planId")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val planId: String?,
    val planName: String,
    val startedAt: Long,
    val endedAt: Long,
    val status: SessionStatus,
    val rating: Int?,
    val note: String,
)

@Entity(
    tableName = "session_set",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionSetEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val position: Int,
    val exerciseId: String,
    /** Name at the time the set was logged; used if the exercise is later removed. */
    val exerciseName: String,
    val type: ExerciseType,
    val groupOrdinal: Int,
    val setOrdinal: Int,
    val targetReps: Int,
    val actualReps: Int,
    val targetDurationSec: Int,
    val actualDurationSec: Int,
    val weightKg: Double,
    val outcome: SetOutcome,
    val completedAt: Long,
)

/** Single-row table holding the in-progress workout as JSON so it survives process death. */
@Entity(tableName = "active_workout")
data class ActiveWorkoutEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "json") val json: String,
)

data class GroupWithItems(
    @Embedded val group: PlanGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val items: List<PlanItemEntity>,
)

data class PlanWithGroups(
    @Embedded val plan: PlanEntity,
    @Relation(entity = PlanGroupEntity::class, parentColumn = "id", entityColumn = "planId")
    val groups: List<GroupWithItems>,
)

data class SessionWithSets(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<SessionSetEntity>,
)
