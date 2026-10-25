package com.workouttracker.app.data.db

import com.workouttracker.domain.Exercise
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanGroup
import com.workouttracker.domain.PlanItem
import com.workouttracker.domain.Session
import com.workouttracker.domain.SessionSet

fun Exercise.toEntity() = ExerciseEntity(
    id = id, name = name, type = type, category = category, defaultWeightKg = defaultWeightKg,
    isPreset = isPreset, isArchived = isArchived, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

fun ExerciseEntity.toDomain() = Exercise(
    id = id, name = name, type = type, category = category, defaultWeightKg = defaultWeightKg,
    isPreset = isPreset, isArchived = isArchived, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

/** Exercise names and types are joined from the exercise table so renames propagate. */
fun PlanWithGroups.toDomain(exercises: Map<String, ExerciseEntity>): Plan = Plan(
    id = plan.id,
    name = plan.name,
    createdAt = plan.createdAt,
    updatedAt = plan.updatedAt,
    lastPerformedAt = plan.lastPerformedAt,
    groups = groups.sortedBy { it.group.position }.map { g ->
        PlanGroup(
            id = g.group.id,
            restAfterSec = g.group.restAfterSec,
            items = g.items.sortedBy { it.position }.mapNotNull { item ->
                val ex = exercises[item.exerciseId] ?: return@mapNotNull null
                PlanItem(
                    id = item.id, exerciseId = item.exerciseId, exerciseName = ex.name, type = ex.type,
                    sets = item.sets, reps = item.reps, durationSec = item.durationSec, weightKg = item.weightKg,
                )
            },
        )
    }.filter { it.items.isNotEmpty() },
)

data class PlanRows(val plan: PlanEntity, val groups: List<PlanGroupEntity>, val items: List<PlanItemEntity>)

fun Plan.toRows(): PlanRows = PlanRows(
    plan = PlanEntity(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt, lastPerformedAt = lastPerformedAt),
    groups = groups.mapIndexed { i, g -> PlanGroupEntity(id = g.id, planId = id, position = i, restAfterSec = g.restAfterSec) },
    items = groups.flatMap { g ->
        g.items.mapIndexed { i, it ->
            PlanItemEntity(
                id = it.id, groupId = g.id, exerciseId = it.exerciseId, position = i,
                sets = it.sets, reps = it.reps, durationSec = it.durationSec, weightKg = it.weightKg,
            )
        }
    },
)

fun SessionWithSets.toDomain(exercises: Map<String, ExerciseEntity>): Session = Session(
    id = session.id,
    planId = session.planId,
    planName = session.planName,
    startedAt = session.startedAt,
    endedAt = session.endedAt,
    status = session.status,
    rating = session.rating,
    note = session.note,
    sets = sets.sortedBy { it.position }.map {
        SessionSet(
            id = it.id, exerciseId = it.exerciseId,
            exerciseName = exercises[it.exerciseId]?.name ?: it.exerciseName,
            type = it.type, groupOrdinal = it.groupOrdinal, setOrdinal = it.setOrdinal,
            targetReps = it.targetReps, actualReps = it.actualReps,
            targetDurationSec = it.targetDurationSec, actualDurationSec = it.actualDurationSec,
            weightKg = it.weightKg, outcome = it.outcome, completedAt = it.completedAt,
        )
    },
)

fun Session.toEntity() = SessionEntity(
    id = id, planId = planId, planName = planName, startedAt = startedAt, endedAt = endedAt,
    status = status, rating = rating, note = note,
)

fun Session.setEntities(): List<SessionSetEntity> = sets.mapIndexed { i, it ->
    SessionSetEntity(
        id = it.id, sessionId = id, position = i, exerciseId = it.exerciseId, exerciseName = it.exerciseName,
        type = it.type, groupOrdinal = it.groupOrdinal, setOrdinal = it.setOrdinal,
        targetReps = it.targetReps, actualReps = it.actualReps,
        targetDurationSec = it.targetDurationSec, actualDurationSec = it.actualDurationSec,
        weightKg = it.weightKg, outcome = it.outcome, completedAt = it.completedAt,
    )
}
