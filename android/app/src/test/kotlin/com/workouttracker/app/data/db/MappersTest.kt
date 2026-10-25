package com.workouttracker.app.data.db

import com.workouttracker.domain.Category
import com.workouttracker.domain.ExerciseType
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanGroup
import com.workouttracker.domain.PlanItem
import com.workouttracker.domain.Presets
import com.workouttracker.domain.Session
import com.workouttracker.domain.SessionSet
import com.workouttracker.domain.SessionStatus
import com.workouttracker.domain.SetOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    private val exercises = Presets.exercises.take(3).map { it.toEntity() }.associateBy { it.id }
    private val a = exercises.values.elementAt(0)
    private val b = exercises.values.elementAt(1)
    private val c = exercises.values.elementAt(2)

    private fun item(id: String, ex: ExerciseEntity, sets: Int = 3) = PlanItem(
        id = id, exerciseId = ex.id, exerciseName = ex.name, type = ex.type, category = ex.category, sets = sets,
    )

    @Test fun planRoundTripsThroughRowsKeepingOrder() {
        val plan = Plan(
            id = "p", name = "Push", createdAt = 1, updatedAt = 2, lastPerformedAt = 3,
            groups = listOf(
                PlanGroup("g1", listOf(item("i1", a), item("i2", b)), restAfterSec = 90),
                PlanGroup("g2", listOf(item("i3", c, sets = 5)), restAfterSec = 30),
            ),
        )
        val rows = plan.toRows()
        // Shuffle to prove positions, not insertion order, drive the result.
        val loaded = PlanWithGroups(
            plan = rows.plan,
            groups = rows.groups.reversed().map { g -> GroupWithItems(g, rows.items.filter { it.groupId == g.id }.reversed()) },
        ).toDomain(exercises)
        assertEquals(plan, loaded)
    }

    @Test fun renamedExerciseNamePropagatesAndMissingOnesAreDropped() {
        val plan = Plan(id = "p", name = "P", groups = listOf(PlanGroup("g", listOf(item("i", a)), 60)))
        val rows = plan.toRows()
        val renamed = exercises + (a.id to a.copy(name = "Renamed", category = Category.CORE))
        val loaded = PlanWithGroups(rows.plan, rows.groups.map { g -> GroupWithItems(g, rows.items) }).toDomain(renamed)
        assertEquals("Renamed", loaded.groups.single().items.single().exerciseName)
        assertEquals(Category.CORE, loaded.groups.single().items.single().category)

        val missing = PlanWithGroups(rows.plan, rows.groups.map { g -> GroupWithItems(g, rows.items) }).toDomain(emptyMap())
        assertEquals(0, missing.groups.size)
    }

    @Test fun sessionRoundTripsWithSetOrder() {
        val sets = (0 until 4).map { i ->
            SessionSet(
                id = "s$i", exerciseId = a.id, exerciseName = a.name, type = ExerciseType.REPS, groupOrdinal = 0, setOrdinal = i + 1,
                targetReps = 10, actualReps = 10 + i, targetDurationSec = 0, actualDurationSec = 0, weightKg = 0.0,
                outcome = if (i == 3) SetOutcome.SKIPPED else SetOutcome.DONE, completedAt = i.toLong(),
            )
        }
        val session = Session("x", "p", "Push", 0, 10, SessionStatus.PARTIAL, rating = 4, note = "ok", sets = sets)
        val loaded = SessionWithSets(session.toEntity(), session.setEntities().shuffled()).toDomain(exercises)
        assertEquals(session, loaded)
    }
}
