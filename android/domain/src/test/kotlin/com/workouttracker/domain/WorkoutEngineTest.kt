package com.workouttracker.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutEngineTest {
    private var counter = 0
    private val ids: () -> String = { "id${counter++}" }

    private fun item(name: String, type: ExerciseType = ExerciseType.REPS, sets: Int = 2, reps: Int = 10, duration: Int = 30) =
        PlanItem(id = ids(), exerciseId = "ex-$name", exerciseName = name, type = type, sets = sets, reps = reps, durationSec = duration)

    private fun plan(vararg groups: PlanGroup) = Plan(id = "plan", name = "Test", groups = groups.toList())

    @Test fun expandHonoursEachItemsSetCountInSuperset() {
        // Web app bug #3: only the first item's set count was used.
        val p = plan(PlanGroup("g", listOf(item("A", sets = 3), item("B", sets = 1)), restAfterSec = 60))
        val w = WorkoutEngine.start(p, now = 0, newId = ids)
        assertEquals(3, w.queue.size)
        assertEquals(listOf("A", "B"), w.queue[0].exercises.map { it.exerciseName })
        assertEquals(listOf("A"), w.queue[1].exercises.map { it.exerciseName })
        assertEquals(listOf(1, 2, 3), w.queue.map { it.setOrdinal })
        assertTrue(w.queue.all { it.totalSetsInGroup == 3 })
    }

    @Test fun completeLogsAndStartsRestThenAutoAdvances() {
        val p = plan(PlanGroup("g", listOf(item("A", sets = 2)), restAfterSec = 60))
        var w = WorkoutEngine.start(p, now = 0, newId = ids)
        w = WorkoutEngine.complete(w, now = 1_000, newId = ids)
        val rest = assertIs<Phase.Resting>(w.phase)
        assertEquals(61_000, rest.endsAt)
        assertEquals(1, w.cursor)
        assertEquals(SetOutcome.DONE, w.log.single().outcome)

        assertIs<Phase.Resting>(WorkoutEngine.tick(w, now = 60_999).phase)
        assertIs<Phase.Performing>(WorkoutEngine.tick(w, now = 61_000).phase)
        // Auto-advance off: the rest stays until skipped.
        assertIs<Phase.Resting>(WorkoutEngine.tick(w, now = 99_000, autoAdvanceRest = false).phase)
    }

    @Test fun skipLogsSkippedRowsWithoutRest() {
        val p = plan(PlanGroup("g", listOf(item("A"), item("B")), restAfterSec = 60))
        var w = WorkoutEngine.start(p, now = 0, newId = ids)
        w = WorkoutEngine.skip(w, now = 5, newId = ids)
        assertIs<Phase.Performing>(w.phase)
        // Web app bug #4: skipped count is exact, one row per exercise.
        assertEquals(2, w.log.count { it.outcome == SetOutcome.SKIPPED })
        assertTrue(w.log.all { it.actualReps == 0 })
    }

    @Test fun lastSetGoesToCompleteAndFinishIsCompleted() {
        val p = plan(PlanGroup("g", listOf(item("A", sets = 1)), restAfterSec = 60))
        var w = WorkoutEngine.start(p, now = 0, newId = ids)
        w = WorkoutEngine.complete(w, now = 10, newId = ids)
        assertEquals(Phase.Complete, w.phase)
        val s = WorkoutEngine.finish(w, now = 20, newId = ids)
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertEquals(1, s.sets.size)
        assertEquals(20, s.endedAt)
    }

    @Test fun finishEarlyIsPartial() {
        val p = plan(PlanGroup("g", listOf(item("A", sets = 3)), restAfterSec = 0))
        var w = WorkoutEngine.start(p, now = 0, newId = ids)
        w = WorkoutEngine.complete(w, now = 10, newId = ids)
        val s = WorkoutEngine.finish(w, now = 20, newId = ids)
        assertEquals(SessionStatus.PARTIAL, s.status)
        assertEquals(1, s.sets.size)
    }

    @Test fun undoRestoresPreviousStateOnce() {
        val p = plan(PlanGroup("g", listOf(item("A", sets = 2)), restAfterSec = 60))
        val start = WorkoutEngine.start(p, now = 0, newId = ids)
        val done = WorkoutEngine.complete(start, now = 1, newId = ids)
        val undone = WorkoutEngine.undo(done)
        assertEquals(start, undone)
        assertNull(undone.previous)
        assertEquals(undone, WorkoutEngine.undo(undone))
    }

    @Test fun adjustChangesActualsNotTargets() {
        val p = plan(PlanGroup("g", listOf(item("A", reps = 10)), restAfterSec = 0))
        var w = WorkoutEngine.start(p, now = 0, newId = ids)
        w = WorkoutEngine.adjust(w, 0, AdjustField.REPS, 2.0)
        w = WorkoutEngine.adjust(w, 0, AdjustField.WEIGHT, 2.5)
        w = WorkoutEngine.complete(w, now = 1, newId = ids)
        val logged = w.log.single()
        assertEquals(10, logged.targetReps)
        assertEquals(12, logged.actualReps)
        assertEquals(2.5, logged.weightKg)
        // Adjust can't go negative.
        val clamped = WorkoutEngine.adjust(WorkoutEngine.start(p, 0, ids), 0, AdjustField.REPS, -50.0)
        assertEquals(0, clamped.current!!.exercises[0].actualReps)
    }

    @Test fun holdCountsDownAndAutoCompletes() {
        val p = plan(PlanGroup("g", listOf(item("Plank", ExerciseType.DURATION, sets = 2, duration = 45)), restAfterSec = 30))
        var w = WorkoutEngine.start(p, now = 0, newId = ids)
        w = WorkoutEngine.startHold(w, now = 1_000)
        assertEquals(46_000, assertIs<Phase.Performing>(w.phase).holdEndsAt)
        assertEquals(w, WorkoutEngine.tick(w, now = 45_999))
        w = WorkoutEngine.tick(w, now = 46_000, newId = ids)
        assertIs<Phase.Resting>(w.phase)
        assertEquals(45, w.log.single().actualDurationSec)
    }

    @Test fun holdStoppedEarlyRecordsTimeHeld() {
        val p = plan(PlanGroup("g", listOf(item("Plank", ExerciseType.DURATION, sets = 1, duration = 60)), restAfterSec = 0))
        var w = WorkoutEngine.start(p, now = 0, newId = ids)
        w = WorkoutEngine.startHold(w, now = 0)
        w = WorkoutEngine.complete(w, now = 20_500, newId = ids)
        assertEquals(20, w.log.single().actualDurationSec)
    }

    @Test fun adjustRestNeverEndsInThePast() {
        val p = plan(PlanGroup("g", listOf(item("A", sets = 2)), restAfterSec = 60))
        var w = WorkoutEngine.complete(WorkoutEngine.start(p, 0, ids), now = 0, newId = ids)
        w = WorkoutEngine.adjustRest(w, 15, now = 10_000)
        assertEquals(75_000, assertIs<Phase.Resting>(w.phase).endsAt)
        w = WorkoutEngine.adjustRest(w, -600, now = 10_000)
        assertEquals(10_000, assertIs<Phase.Resting>(w.phase).endsAt)
        assertIs<Phase.Performing>(WorkoutEngine.skipRest(w).phase)
    }

    @Test fun injectGoesNextAndReopensCompletedWorkout() {
        val p = plan(PlanGroup("g", listOf(item("A", sets = 1)), restAfterSec = 60))
        var w = WorkoutEngine.complete(WorkoutEngine.start(p, 0, ids), now = 1, newId = ids)
        assertEquals(Phase.Complete, w.phase)
        val extra = Exercise(id = "ex-B", name = "B", type = ExerciseType.REPS, category = Category.CORE)
        w = WorkoutEngine.inject(w, listOf(extra), asSuperset = false, sets = 2, restSec = 30, newId = ids)
        // Web app bug #2: the workout continues instead of having been saved already.
        assertIs<Phase.Performing>(w.phase)
        assertEquals("B", w.current!!.exercises.single().exerciseName)
        assertEquals(3, w.queue.size)
        assertEquals(1, w.queue[1].groupOrdinal)
        w = WorkoutEngine.complete(w, 2, ids)
        w = WorkoutEngine.skipRest(w)
        w = WorkoutEngine.complete(w, 3, ids)
        assertEquals(Phase.Complete, w.phase)
        assertEquals(SessionStatus.COMPLETED, WorkoutEngine.finish(w, 4, newId = ids).status)
    }

    @Test fun injectDuringRestInterruptsRest() {
        val p = plan(PlanGroup("g", listOf(item("A", sets = 2)), restAfterSec = 60))
        var w = WorkoutEngine.complete(WorkoutEngine.start(p, 0, ids), now = 1, newId = ids)
        val extra = Exercise(id = "ex-B", name = "B", type = ExerciseType.REPS, category = Category.CORE)
        w = WorkoutEngine.inject(w, listOf(extra), asSuperset = false, sets = 1, restSec = 30, newId = ids)
        assertIs<Phase.Performing>(w.phase)
        assertEquals("B", w.current!!.exercises.single().exerciseName)
        assertEquals("A", w.next!!.exercises.single().exerciseName)
    }

    @Test fun startEmptyThenInject() {
        var w = WorkoutEngine.startEmpty(now = 0)
        assertNull(w.current)
        val extra = Exercise(id = "ex-B", name = "B", type = ExerciseType.REPS, category = Category.CORE)
        w = WorkoutEngine.inject(w, listOf(extra, extra.copy(id = "ex-C", name = "C")), asSuperset = true, sets = 3, restSec = 45, newId = ids)
        assertEquals(3, w.queue.size)
        assertEquals(2, w.current!!.exercises.size)
    }

    @Test fun queueEditsOnlyTouchUpcomingCards() {
        val p = plan(
            PlanGroup("g1", listOf(item("A", sets = 1)), restAfterSec = 0),
            PlanGroup("g2", listOf(item("B", sets = 1)), restAfterSec = 0),
            PlanGroup("g3", listOf(item("C", sets = 1)), restAfterSec = 0),
        )
        var w = WorkoutEngine.complete(WorkoutEngine.start(p, 0, ids), 1, ids)
        assertEquals(w, WorkoutEngine.removeQueued(w, 0))
        val jumped = WorkoutEngine.jumpTo(w, 2)
        assertEquals("C", jumped.current!!.exercises.single().exerciseName)
        assertEquals("B", jumped.next!!.exercises.single().exerciseName)
        w = WorkoutEngine.removeQueued(w, 1)
        assertEquals("C", w.current!!.exercises.single().exerciseName)
        w = WorkoutEngine.removeQueued(w, 1)
        assertEquals(Phase.Complete, w.phase)
    }

    @Test fun toPlanGroupsRebuildsWhatWasDone() {
        val p = plan(
            PlanGroup("g1", listOf(item("A", sets = 2, reps = 8)), restAfterSec = 90),
            PlanGroup("g2", listOf(item("B", sets = 1)), restAfterSec = 30),
        )
        var w = WorkoutEngine.start(p, 0, ids)
        w = WorkoutEngine.adjust(w, 0, AdjustField.REPS, 2.0)
        w = WorkoutEngine.skipRest(WorkoutEngine.complete(w, 1, ids))
        w = WorkoutEngine.complete(w, 2, ids)
        w = WorkoutEngine.skip(WorkoutEngine.skipRest(w), 3, ids)
        val groups = WorkoutEngine.toPlanGroups(w, ids)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].items.single().sets)
        assertEquals(10, groups[0].items.single().reps)
        assertEquals(90, groups[0].restAfterSec)
    }

    @Test fun stateRoundTripsThroughJson() {
        val p = plan(PlanGroup("g", listOf(item("A", sets = 2)), restAfterSec = 60))
        val w = WorkoutEngine.complete(WorkoutEngine.start(p, 0, ids), 1, ids)
        val json = Json { ignoreUnknownKeys = true }
        val text = json.encodeToString(ActiveWorkout.serializer(), w)
        assertEquals(w, json.decodeFromString(ActiveWorkout.serializer(), text))
    }
}

class PlanUpdatesTest {
    @Test fun appliesBestActualsAndIgnoresInjectedCards() {
        var n = 0
        val ids = { "i${n++}" }
        val a = PlanItem(id = "a", exerciseId = "ex-A", exerciseName = "A", type = ExerciseType.REPS, sets = 2, reps = 8, weightKg = 20.0)
        val plan = Plan(id = "p", name = "P", groups = listOf(PlanGroup("g", listOf(a), restAfterSec = 0)))
        var w = WorkoutEngine.start(plan, 0, ids)
        w = WorkoutEngine.adjust(w, 0, AdjustField.REPS, 3.0)
        w = WorkoutEngine.complete(w, 1, ids)
        w = WorkoutEngine.adjust(w, 0, AdjustField.WEIGHT, 2.5)
        w = WorkoutEngine.complete(w, 2, ids)
        val updated = PlanUpdates.applyActuals(plan, w.log)
        val item = updated.groups.single().items.single()
        assertEquals(11, item.reps)
        assertEquals(22.5, item.weightKg)
        assertEquals(2, item.sets)
    }
}
