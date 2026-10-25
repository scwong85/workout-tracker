package com.workouttracker.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseType { REPS, DURATION }

@Serializable
enum class Category(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    LEGS("Legs"),
    CORE("Core"),
    ARMS("Arms"),
    SHOULDERS("Shoulders"),
    FULL_BODY("Full body"),
}

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val type: ExerciseType,
    val category: Category,
    val defaultWeightKg: Double = 0.0,
    val isPreset: Boolean = false,
    val isArchived: Boolean = false,
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** One exercise inside a [PlanGroup], with its targets. */
@Serializable
data class PlanItem(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val type: ExerciseType,
    val category: Category = Category.FULL_BODY,
    val sets: Int = 3,
    val reps: Int = 10,
    val durationSec: Int = 30,
    val weightKg: Double = 0.0,
)

/** A block of a plan. A group with more than one item is a superset. */
@Serializable
data class PlanGroup(
    val id: String,
    val items: List<PlanItem>,
    val restAfterSec: Int = 60,
) {
    val isSuperset: Boolean get() = items.size > 1
    val rounds: Int get() = items.maxOfOrNull { it.sets } ?: 0
}

@Serializable
data class Plan(
    val id: String,
    val name: String,
    val groups: List<PlanGroup>,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastPerformedAt: Long? = null,
) {
    val totalSets: Int get() = groups.sumOf { it.rounds }
    val exerciseCount: Int get() = groups.sumOf { it.items.size }
}

@Serializable
enum class SetOutcome { DONE, SKIPPED }

@Serializable
data class SessionSet(
    val id: String,
    val exerciseId: String,
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

@Serializable
enum class SessionStatus { COMPLETED, PARTIAL }

@Serializable
data class Session(
    val id: String,
    val planId: String?,
    val planName: String,
    val startedAt: Long,
    val endedAt: Long,
    val status: SessionStatus,
    val rating: Int? = null,
    val note: String = "",
    val sets: List<SessionSet> = emptyList(),
) {
    val doneSets: List<SessionSet> get() = sets.filter { it.outcome == SetOutcome.DONE }
    val durationMs: Long get() = (endedAt - startedAt).coerceAtLeast(0)
}
