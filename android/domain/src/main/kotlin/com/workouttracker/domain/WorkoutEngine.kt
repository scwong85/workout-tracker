package com.workouttracker.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One exercise on a set card: the target plus what the user actually did. */
@Serializable
data class QueuedExercise(
    val exerciseId: String,
    val exerciseName: String,
    val type: ExerciseType,
    val category: Category = Category.FULL_BODY,
    val targetReps: Int,
    val targetDurationSec: Int,
    val weightKg: Double,
    val actualReps: Int = targetReps,
    val actualDurationSec: Int = targetDurationSec,
)

/** One card in the workout queue. */
@Serializable
data class QueuedSet(
    val id: String,
    val groupOrdinal: Int,
    val setOrdinal: Int,
    val totalSetsInGroup: Int,
    val exercises: List<QueuedExercise>,
    val restAfterSec: Int,
)

@Serializable
sealed interface Phase {
    /** Doing the current set. [holdEndsAt] is set while a timed exercise is counting down. */
    @Serializable @SerialName("performing")
    data class Performing(val holdStartedAt: Long? = null, val holdEndsAt: Long? = null) : Phase

    /** Resting before the set at the cursor. Times are wall-clock epoch ms so they survive restarts. */
    @Serializable @SerialName("resting")
    data class Resting(val startedAt: Long, val endsAt: Long) : Phase

    /** Every queued set has been done or skipped; waiting for the user to save. */
    @Serializable @SerialName("complete")
    data object Complete : Phase
}

@Serializable
data class ActiveWorkout(
    val planId: String?,
    val planName: String,
    val startedAt: Long,
    val queue: List<QueuedSet>,
    val cursor: Int = 0,
    val phase: Phase = Phase.Performing(),
    val log: List<SessionSet> = emptyList(),
    /** One level of undo for the last Done/Skip. */
    val previous: ActiveWorkout? = null,
) {
    val current: QueuedSet? get() = queue.getOrNull(cursor)
    val next: QueuedSet? get() = queue.getOrNull(cursor + 1)
    val upcoming: List<QueuedSet> get() = queue.drop(cursor)
    val isComplete: Boolean get() = phase == Phase.Complete
    val canUndo: Boolean get() = previous != null

    /** Fraction of queued sets that are behind the cursor. */
    val progress: Float get() = if (queue.isEmpty()) 0f else cursor.coerceAtMost(queue.size).toFloat() / queue.size

    /** The set after the rest, shown as a preview on the rest screen. */
    val afterRest: QueuedSet? get() = current
}

enum class AdjustField { REPS, DURATION, WEIGHT }

/** Every transition of the workout player as a pure function of (state, now). */
object WorkoutEngine {

    /** Expands a plan into its card queue. Each item honours its own set count inside a superset. */
    fun start(plan: Plan, now: Long, newId: () -> String = Ids::new): ActiveWorkout =
        ActiveWorkout(
            planId = plan.id,
            planName = plan.name,
            startedAt = now,
            queue = expand(plan.groups, newId),
        ).let(::settle)

    fun startEmpty(now: Long, name: String = "Quick workout"): ActiveWorkout =
        ActiveWorkout(planId = null, planName = name, startedAt = now, queue = emptyList(), phase = Phase.Performing())

    fun expand(groups: List<PlanGroup>, newId: () -> String = Ids::new, ordinalOffset: Int = 0): List<QueuedSet> =
        groups.flatMapIndexed { gi, group ->
            val rounds = group.rounds
            (0 until rounds).map { round ->
                QueuedSet(
                    id = newId(),
                    groupOrdinal = gi + ordinalOffset,
                    setOrdinal = round + 1,
                    totalSetsInGroup = rounds,
                    exercises = group.items.filter { it.sets > round }.map { it.toQueued() },
                    restAfterSec = group.restAfterSec,
                )
            }
        }

    private fun PlanItem.toQueued() = QueuedExercise(
        exerciseId = exerciseId, exerciseName = exerciseName, type = type, category = category,
        targetReps = reps, targetDurationSec = durationSec, weightKg = weightKg,
    )

    /** Adjusts what the user actually did on the current card before logging it. */
    fun adjust(state: ActiveWorkout, exerciseIndex: Int, field: AdjustField, delta: Double): ActiveWorkout {
        val set = state.current ?: return state
        if (state.phase !is Phase.Performing) return state
        val exercises = set.exercises.mapIndexed { i, ex ->
            if (i != exerciseIndex) ex else when (field) {
                AdjustField.REPS -> ex.copy(actualReps = (ex.actualReps + delta.toInt()).coerceIn(0, 999))
                AdjustField.DURATION -> ex.copy(actualDurationSec = (ex.actualDurationSec + delta.toInt()).coerceIn(0, 3600))
                AdjustField.WEIGHT -> ex.copy(weightKg = (ex.weightKg + delta).coerceIn(0.0, 1000.0))
            }
        }
        return state.replaceCurrent(set.copy(exercises = exercises))
    }

    /** Sets an exact value (used by the numpad). */
    fun setValue(state: ActiveWorkout, exerciseIndex: Int, field: AdjustField, value: Double): ActiveWorkout {
        val ex = state.current?.exercises?.getOrNull(exerciseIndex) ?: return state
        val currentValue = when (field) {
            AdjustField.REPS -> ex.actualReps.toDouble()
            AdjustField.DURATION -> ex.actualDurationSec.toDouble()
            AdjustField.WEIGHT -> ex.weightKg
        }
        return adjust(state, exerciseIndex, field, value - currentValue)
    }

    /** Starts the countdown for a timed card. */
    fun startHold(state: ActiveWorkout, now: Long): ActiveWorkout {
        val set = state.current ?: return state
        val phase = state.phase as? Phase.Performing ?: return state
        if (phase.holdEndsAt != null) return state
        val seconds = set.exercises.filter { it.type == ExerciseType.DURATION }.maxOfOrNull { it.actualDurationSec } ?: return state
        return state.copy(phase = Phase.Performing(holdStartedAt = now, holdEndsAt = now + seconds * 1000L))
    }

    fun cancelHold(state: ActiveWorkout): ActiveWorkout =
        if (state.phase is Phase.Performing) state.copy(phase = Phase.Performing()) else state

    fun complete(state: ActiveWorkout, now: Long, newId: () -> String = Ids::new): ActiveWorkout =
        logCurrent(state, now, SetOutcome.DONE, newId)

    fun skip(state: ActiveWorkout, now: Long, newId: () -> String = Ids::new): ActiveWorkout =
        logCurrent(state, now, SetOutcome.SKIPPED, newId)

    private fun logCurrent(state: ActiveWorkout, now: Long, outcome: SetOutcome, newId: () -> String): ActiveWorkout {
        val set = state.current ?: return state
        if (state.phase !is Phase.Performing) return state
        val phase = state.phase
        // A hold stopped early records the time actually held.
        val heldSec = if (phase.holdStartedAt != null && phase.holdEndsAt != null && now < phase.holdEndsAt) {
            ((now - phase.holdStartedAt) / 1000L).toInt()
        } else null
        val entries = set.exercises.map { ex ->
            SessionSet(
                id = newId(),
                exerciseId = ex.exerciseId,
                exerciseName = ex.exerciseName,
                type = ex.type,
                groupOrdinal = set.groupOrdinal,
                setOrdinal = set.setOrdinal,
                targetReps = ex.targetReps,
                actualReps = if (outcome == SetOutcome.DONE && ex.type == ExerciseType.REPS) ex.actualReps else 0,
                targetDurationSec = ex.targetDurationSec,
                actualDurationSec = when {
                    outcome != SetOutcome.DONE || ex.type != ExerciseType.DURATION -> 0
                    heldSec != null -> heldSec.coerceAtMost(ex.actualDurationSec)
                    else -> ex.actualDurationSec
                },
                weightKg = ex.weightKg,
                outcome = outcome,
                completedAt = now,
            )
        }
        val nextCursor = state.cursor + 1
        val hasMore = nextCursor < state.queue.size
        val rest = set.restAfterSec
        val nextPhase = when {
            !hasMore -> Phase.Complete
            outcome == SetOutcome.DONE && rest > 0 -> Phase.Resting(startedAt = now, endsAt = now + rest * 1000L)
            else -> Phase.Performing()
        }
        return state.copy(
            cursor = nextCursor,
            phase = nextPhase,
            log = state.log + entries,
            previous = state.copy(previous = null),
        )
    }

    fun undo(state: ActiveWorkout): ActiveWorkout = state.previous ?: state

    fun adjustRest(state: ActiveWorkout, deltaSec: Int, now: Long): ActiveWorkout {
        val phase = state.phase as? Phase.Resting ?: return state
        val endsAt = (phase.endsAt + deltaSec * 1000L).coerceAtLeast(now)
        return state.copy(phase = phase.copy(endsAt = endsAt))
    }

    fun skipRest(state: ActiveWorkout): ActiveWorkout =
        if (state.phase is Phase.Resting) state.copy(phase = Phase.Performing()) else state

    /**
     * Advances time-based phases. Rest ends into the next card when [autoAdvanceRest] is on;
     * a finished hold logs the set automatically.
     */
    fun tick(state: ActiveWorkout, now: Long, autoAdvanceRest: Boolean = true, newId: () -> String = Ids::new): ActiveWorkout =
        when (val phase = state.phase) {
            is Phase.Resting -> if (autoAdvanceRest && now >= phase.endsAt) state.copy(phase = Phase.Performing()) else state
            is Phase.Performing -> if (phase.holdEndsAt != null && now >= phase.holdEndsAt) complete(state, now, newId) else state
            Phase.Complete -> state
        }

    /**
     * Inserts new cards at the cursor so they come next. Interrupts a rest; reopens a completed
     * workout (nothing has been saved yet, so this can't create a duplicate session).
     */
    fun inject(
        state: ActiveWorkout,
        exercises: List<Exercise>,
        asSuperset: Boolean,
        sets: Int,
        restSec: Int,
        itemFor: (Exercise) -> PlanItem = { PlanDefaults.itemFor(it) },
        newId: () -> String = Ids::new,
    ): ActiveWorkout {
        if (exercises.isEmpty()) return state
        val items = exercises.map { itemFor(it).copy(sets = sets) }
        val groups = if (asSuperset) {
            listOf(PlanGroup(id = newId(), items = items, restAfterSec = restSec))
        } else {
            items.map { PlanGroup(id = newId(), items = listOf(it), restAfterSec = restSec) }
        }
        val nextOrdinal = (state.queue.maxOfOrNull { it.groupOrdinal } ?: -1) + 1
        val cards = expand(groups, newId, ordinalOffset = nextOrdinal)
        val queue = state.queue.take(state.cursor) + cards + state.queue.drop(state.cursor)
        return state.copy(queue = queue, phase = Phase.Performing(), previous = null)
    }

    /** Removes an upcoming card (index into the whole queue). Cards already done can't be removed. */
    fun removeQueued(state: ActiveWorkout, index: Int): ActiveWorkout {
        if (index < state.cursor || index >= state.queue.size) return state
        val queue = state.queue.filterIndexed { i, _ -> i != index }
        val phase = when {
            state.cursor >= queue.size -> Phase.Complete
            index == state.cursor && state.phase is Phase.Performing -> Phase.Performing()
            else -> state.phase
        }
        return state.copy(queue = queue, phase = phase, previous = null)
    }

    /** Moves an upcoming card within the upcoming part of the queue. */
    fun moveQueued(state: ActiveWorkout, from: Int, to: Int): ActiveWorkout {
        val range = state.cursor until state.queue.size
        if (from !in range || to !in range || from == to) return state
        val queue = state.queue.toMutableList()
        queue.add(to, queue.removeAt(from))
        val phase = if ((from == state.cursor || to == state.cursor) && state.phase is Phase.Performing) Phase.Performing() else state.phase
        return state.copy(queue = queue, phase = phase, previous = null)
    }

    /** Jumps straight to an upcoming card; the cards in between stay queued after it. */
    fun jumpTo(state: ActiveWorkout, index: Int): ActiveWorkout = moveQueued(state, index, state.cursor)

    /** Builds the session to save. Unreached cards are not logged; the session is PARTIAL if any remain. */
    fun finish(state: ActiveWorkout, now: Long, rating: Int? = null, note: String = "", newId: () -> String = Ids::new): Session =
        Session(
            id = newId(),
            planId = state.planId,
            planName = state.planName,
            startedAt = state.startedAt,
            endedAt = now,
            status = if (state.cursor >= state.queue.size) SessionStatus.COMPLETED else SessionStatus.PARTIAL,
            rating = rating,
            note = note.trim(),
            sets = state.log,
        )

    /** Turns what was actually done into a plan (for "Save as plan" / "Update plan"). */
    fun toPlanGroups(state: ActiveWorkout, newId: () -> String = Ids::new): List<PlanGroup> {
        val done = state.log.filter { it.outcome == SetOutcome.DONE }
        val categories = state.queue.flatMap { it.exercises }.associate { it.exerciseId to it.category }
        val restByGroup = state.queue.groupBy { it.groupOrdinal }.mapValues { (_, cards) -> cards.first().restAfterSec }
        return done.groupBy { it.groupOrdinal }.toSortedMap().map { (ordinal, entries) ->
            val items = entries.groupBy { it.exerciseId }.map { (exerciseId, sets) ->
                val last = sets.last()
                PlanItem(
                    id = newId(),
                    exerciseId = exerciseId,
                    exerciseName = last.exerciseName,
                    type = last.type,
                    category = categories[exerciseId] ?: Category.FULL_BODY,
                    sets = sets.size,
                    reps = sets.maxOf { it.actualReps }.coerceAtLeast(1),
                    durationSec = sets.maxOf { it.actualDurationSec }.coerceAtLeast(1),
                    weightKg = sets.maxOf { it.weightKg },
                )
            }
            PlanGroup(id = newId(), items = items, restAfterSec = restByGroup[ordinal] ?: PlanDefaults.REST_SEC)
        }
    }

    /** Moves the cursor forward when the queue is empty or exhausted (e.g. a plan with no sets). */
    private fun settle(state: ActiveWorkout): ActiveWorkout =
        if (state.queue.isNotEmpty() && state.cursor >= state.queue.size) state.copy(phase = Phase.Complete) else state

    private fun ActiveWorkout.replaceCurrent(set: QueuedSet): ActiveWorkout =
        copy(queue = queue.mapIndexed { i, s -> if (i == cursor) set else s })
}
