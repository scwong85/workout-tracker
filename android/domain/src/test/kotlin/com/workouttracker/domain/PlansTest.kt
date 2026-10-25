package com.workouttracker.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlansTest {
    private val pushUps = Presets.exercises.first { it.name == "Standard Push-ups" }
    private val plank = Presets.exercises.first { it.name == "Plank (Front)" }
    private val dips = Presets.exercises.first { it.name == "Dips (Parallel Bars)" }

    @Test fun addExercisesCreatesOneGroupEachOrOneSuperset() {
        val singles = PlanEdits.addExercises(emptyList(), listOf(pushUps, plank), asSuperset = false, restSec = 60)
        assertEquals(2, singles.size)
        val superset = PlanEdits.addExercises(emptyList(), listOf(pushUps, plank), asSuperset = true, restSec = 90)
        assertEquals(1, superset.size)
        assertTrue(superset[0].isSuperset)
        assertEquals(90, superset[0].restAfterSec)
    }

    @Test fun mergeAndUngroupRoundTrip() {
        val groups = PlanEdits.addExercises(emptyList(), listOf(pushUps, plank, dips), asSuperset = false, restSec = 60)
        val merged = PlanEdits.mergeWithNext(groups, 0)
        assertEquals(2, merged.size)
        assertEquals(listOf(pushUps.id, plank.id), merged[0].items.map { it.exerciseId })
        val split = PlanEdits.ungroup(merged, 0)
        assertEquals(groups.map { g -> g.items.map { it.exerciseId } }, split.map { g -> g.items.map { it.exerciseId } })
        // Merging the last group is a no-op.
        assertEquals(groups, PlanEdits.mergeWithNext(groups, 2))
    }

    @Test fun moveReorders() {
        val groups = PlanEdits.addExercises(emptyList(), listOf(pushUps, plank, dips), asSuperset = false, restSec = 60)
        val moved = PlanEdits.move(groups, 2, 0)
        assertEquals(dips.id, moved[0].items[0].exerciseId)
        assertEquals(groups, PlanEdits.move(groups, 0, 5))
    }

    @Test fun updateItemClampsValues() {
        val groups = PlanEdits.addExercises(emptyList(), listOf(pushUps), asSuperset = false, restSec = 60)
        val id = groups[0].items[0].id
        val updated = PlanEdits.updateItem(groups, id) { it.copy(reps = -4, sets = 99) }
        assertEquals(1, updated[0].items[0].reps)
        assertEquals(20, updated[0].items[0].sets)
    }

    @Test fun removingLastItemDropsGroup() {
        val groups = PlanEdits.addExercises(emptyList(), listOf(pushUps, plank), asSuperset = false, restSec = 60)
        val removed = PlanEdits.removeItem(groups, groups[0].items[0].id)
        assertEquals(1, removed.size)
    }

    @Test fun estimateSkipsRestAfterFinalSet() {
        val item = PlanDefaults.itemFor(pushUps).copy(sets = 2, reps = 10)
        val groups = listOf(PlanGroup(id = "g", items = listOf(item), restAfterSec = 60))
        // 2 x (10s setup + 30s work) + 1 rest
        assertEquals(2 * 40 + 60, PlanEstimator.estimateSeconds(groups))
    }

    @Test fun duplicateGetsFreshIds() {
        val plan = Plan(id = "p", name = "Push", groups = PlanEdits.addExercises(emptyList(), listOf(pushUps), false, 60))
        val copy = PlanEdits.duplicate(plan, now = 5)
        assertNotEquals(plan.id, copy.id)
        assertNotEquals(plan.groups[0].id, copy.groups[0].id)
        assertEquals("Push (copy)", copy.name)
    }
}
