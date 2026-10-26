package com.workouttracker.domain

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

object PlanDefaults {
    const val SETS = 3
    const val REPS = 10
    const val DURATION_SEC = 30
    const val REST_SEC = 60

    fun itemFor(exercise: Exercise, id: String = Ids.new()) = PlanItem(
        id = id,
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        type = exercise.type,
        category = exercise.category,
        sets = SETS,
        reps = REPS,
        durationSec = DURATION_SEC,
        weightKg = exercise.defaultWeightKg,
    )
}

object PlanEstimator {
    /** Rough time per rep plus per-set setup; only used to show "~38 min". */
    private const val SEC_PER_REP = 3
    private const val SETUP_SEC = 10

    fun estimateSeconds(groups: List<PlanGroup>): Int {
        var total = 0
        groups.forEachIndexed { gi, group ->
            for (round in 0 until group.rounds) {
                group.items.filter { it.sets > round }.forEach { item ->
                    total += SETUP_SEC + when (item.type) {
                        ExerciseType.REPS -> item.reps * SEC_PER_REP
                        ExerciseType.DURATION -> item.durationSec
                    }
                }
                val isLastSet = gi == groups.lastIndex && round == group.rounds - 1
                if (!isLastSet) total += group.restAfterSec
            }
        }
        return total
    }

    fun estimateMinutes(groups: List<PlanGroup>): Int = (estimateSeconds(groups) + 59) / 60
}

/** Pure edits used by the plan editor, kept here so they are unit-testable. */
object PlanEdits {
    fun addExercises(
        groups: List<PlanGroup>,
        exercises: List<Exercise>,
        asSuperset: Boolean,
        restSec: Int,
        itemFor: (Exercise) -> PlanItem = { PlanDefaults.itemFor(it) },
    ): List<PlanGroup> {
        if (exercises.isEmpty()) return groups
        val items = exercises.map(itemFor)
        val added = if (asSuperset) {
            listOf(PlanGroup(id = Ids.new(), items = items, restAfterSec = restSec))
        } else {
            items.map { PlanGroup(id = Ids.new(), items = listOf(it), restAfterSec = restSec) }
        }
        return groups + added
    }

    fun move(groups: List<PlanGroup>, from: Int, to: Int): List<PlanGroup> {
        if (from !in groups.indices || to !in groups.indices || from == to) return groups
        val list = groups.toMutableList()
        list.add(to, list.removeAt(from))
        return list
    }

    /** Folds group [index + 1] into group [index], producing a superset. */
    fun mergeWithNext(groups: List<PlanGroup>, index: Int): List<PlanGroup> {
        if (index !in 0 until groups.lastIndex) return groups
        val a = groups[index]
        val b = groups[index + 1]
        val merged = a.copy(items = a.items + b.items, restAfterSec = maxOf(a.restAfterSec, b.restAfterSec))
        return groups.take(index) + merged + groups.drop(index + 2)
    }

    /** Splits a superset into one group per exercise, keeping its rest time. */
    fun ungroup(groups: List<PlanGroup>, index: Int): List<PlanGroup> {
        val g = groups.getOrNull(index) ?: return groups
        if (!g.isSuperset) return groups
        val split = g.items.mapIndexed { i, item ->
            PlanGroup(id = if (i == 0) g.id else Ids.new(), items = listOf(item), restAfterSec = g.restAfterSec)
        }
        return groups.take(index) + split + groups.drop(index + 1)
    }

    fun updateItem(groups: List<PlanGroup>, itemId: String, transform: (PlanItem) -> PlanItem): List<PlanGroup> =
        groups.map { g -> g.copy(items = g.items.map { if (it.id == itemId) transform(it).sanitized() else it }) }

    fun removeItem(groups: List<PlanGroup>, itemId: String): List<PlanGroup> =
        groups.map { g -> g.copy(items = g.items.filterNot { it.id == itemId }) }.filter { it.items.isNotEmpty() }

    fun setRest(groups: List<PlanGroup>, groupId: String, restSec: Int): List<PlanGroup> =
        groups.map { if (it.id == groupId) it.copy(restAfterSec = restSec.coerceIn(0, 600)) else it }

    private fun PlanItem.sanitized() = copy(
        sets = sets.coerceIn(1, 20),
        reps = reps.coerceIn(1, 999),
        durationSec = durationSec.coerceIn(1, 3600),
        weightKg = weightKg.coerceIn(0.0, 1000.0),
    )

    fun duplicate(plan: Plan, now: Long): Plan = plan.copy(
        id = Ids.new(),
        name = "${plan.name} (copy)",
        createdAt = now,
        updatedAt = now,
        lastPerformedAt = null,
        groups = plan.groups.map { g -> g.copy(id = Ids.new(), items = g.items.map { it.copy(id = Ids.new()) }) },
    )
}

object PlanUpdates {
    /**
     * "Update plan with today's numbers": each plan item takes the best set the user actually did
     * for it. Cards added mid-workout (group ordinals beyond the plan) are ignored.
     */
    fun applyActuals(plan: Plan, log: List<SessionSet>): Plan {
        val done = log.filter { it.outcome == SetOutcome.DONE }
        val groups = plan.groups.mapIndexed { gi, group ->
            group.copy(
                items = group.items.map { item ->
                    val sets = done.filter { it.groupOrdinal == gi && it.exerciseId == item.exerciseId }
                    if (sets.isEmpty()) item else item.copy(
                        reps = if (item.type == ExerciseType.REPS) sets.maxOf { it.actualReps }.coerceAtLeast(1) else item.reps,
                        durationSec = if (item.type == ExerciseType.DURATION) sets.maxOf { it.actualDurationSec }.coerceAtLeast(1) else item.durationSec,
                        weightKg = sets.maxOf { it.weightKg },
                    )
                },
            )
        }
        return plan.copy(groups = groups)
    }
}

object SessionPlans {
    /** Rebuilds plan groups from logged sets (for "Repeat this workout"); skipped sets are left out. */
    fun groupsFrom(
        sets: List<SessionSet>,
        categories: Map<String, Category>,
        restSec: Int = PlanDefaults.REST_SEC,
        newId: () -> String = Ids::new,
    ): List<PlanGroup> =
        sets.filter { it.outcome == SetOutcome.DONE }.groupBy { it.groupOrdinal }.toSortedMap().map { (_, entries) ->
            PlanGroup(
                id = newId(),
                restAfterSec = restSec,
                items = entries.groupBy { it.exerciseId }.map { (exerciseId, done) ->
                    val last = done.last()
                    PlanItem(
                        id = newId(),
                        exerciseId = exerciseId,
                        exerciseName = last.exerciseName,
                        type = last.type,
                        category = categories[exerciseId] ?: Category.FULL_BODY,
                        sets = done.size,
                        reps = done.maxOf { it.actualReps }.coerceAtLeast(1),
                        durationSec = done.maxOf { it.actualDurationSec }.coerceAtLeast(1),
                        weightKg = done.maxOf { it.weightKg },
                    )
                },
            )
        }
}

object PlanOrder {
    /**
     * Most recently performed first; plans never performed follow, newest edit first.
     *
     * Deliberately uses `Long` keys throughout: a selector like `lastPerformedAt ?: 0` yields a
     * `Comparable<*>` that mixes Long and Int, which throws ClassCastException at runtime as
     * soon as a performed plan is compared with an unperformed one.
     */
    fun recentFirst(plans: List<Plan>): List<Plan> =
        plans.sortedWith(
            compareByDescending<Plan> { it.lastPerformedAt ?: Long.MIN_VALUE }
                .thenByDescending { it.updatedAt },
        )
}
