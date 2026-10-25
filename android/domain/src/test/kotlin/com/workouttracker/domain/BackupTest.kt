package com.workouttracker.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {
    private var n = 0
    private val ids = { "n${n++}" }

    /** Shaped exactly like scripts.js exportData() output. */
    private val webExport = """
    {
      "exercises": [
        {"id": "w1", "name": "Standard Push-ups", "type": 1, "category": "chest", "isPreset": true, "createdAt": 1700000000000},
        {"id": "w2", "name": "Plank (Front)", "type": 2, "category": "core", "isPreset": true, "createdAt": 1700000000000},
        {"id": "w3", "name": "Ring Rows", "type": 1, "category": "back", "defaultWeight": 0, "isPreset": false, "createdAt": 1700000001000}
      ],
      "workoutPlans": [
        {"id": "p1", "name": "Push", "createdAt": 1700000002000, "updatedAt": 1700000003000,
         "exercises": [
           {"setIndex": 0, "restAfterSet": 90, "exercises": [
             {"exerciseId": "w1", "exerciseName": "Standard Push-ups", "type": 1, "sets": 3, "reps": 12, "duration": 0, "weight": 0},
             {"exerciseId": "w2", "exerciseName": "Plank (Front)", "type": 2, "sets": 3, "reps": 0, "duration": 45, "weight": 0}
           ]},
           {"setIndex": 1, "restAfterSet": 60, "exercises": [
             {"exerciseId": "gone", "exerciseName": "Deleted Thing", "type": 1, "sets": 2, "reps": 8, "duration": 0, "weight": 10}
           ]}
         ]}
      ],
      "workoutSessions": [
        {"id": "s1", "workoutPlanId": "p1", "workoutPlanName": "Push", "startTime": 1700000100000, "endTime": 1700001900000,
         "completedSets": [
           {"exerciseId": "w1", "exerciseName": "Standard Push-ups", "type": 1, "reps": 12, "duration": 0, "weight": 0, "completedAt": 1700000200000},
           {"exerciseId": "w2", "exerciseName": "Plank (Front)", "type": 2, "reps": 0, "duration": 45, "weight": 0, "completedAt": 1700000210000},
           {"exerciseId": "w1", "exerciseName": "Standard Push-ups", "type": 1, "reps": 10, "duration": 0, "weight": 0, "completedAt": 1700000300000}
         ],
         "skippedSets": 3, "status": "completed"},
        {"id": "s2", "workoutPlanId": null, "workoutPlanName": "Custom Workout", "startTime": 1700100000000, "endTime": 1700100600000,
         "completedSets": [{"exerciseId": "w3", "exerciseName": "Ring Rows", "type": 1, "reps": "8", "duration": 0, "weight": 5.5, "completedAt": 1700100100000}],
         "skippedSets": 0, "status": "completed"}
      ],
      "exportDate": 1700200000000
    }
    """.trimIndent()

    @Test fun importsWebExport() {
        val data = Backup.decode(webExport, ids)
        assertEquals(BackupSource.WEB, data.source)

        // Presets map to the built-in ids; the custom exercise keeps its id; the dangling reference becomes archived.
        val byName = data.exercises.associateBy { it.name }
        assertEquals(Presets.presetId("Standard Push-ups"), byName.getValue("Standard Push-ups").id)
        assertEquals("w3", byName.getValue("Ring Rows").id)
        assertTrue(byName.getValue("Deleted Thing").isArchived)

        val plan = data.plans.single()
        assertEquals(2, plan.groups.size)
        assertTrue(plan.groups[0].isSuperset)
        assertEquals(90, plan.groups[0].restAfterSec)
        assertEquals(ExerciseType.DURATION, plan.groups[0].items[1].type)
        assertEquals(45, plan.groups[0].items[1].durationSec)
        assertEquals(10.0, plan.groups[1].items[0].weightKg)

        val s1 = data.sessions.first { it.id == "s1" }
        assertEquals("p1", s1.planId)
        assertEquals(3, s1.sets.size)
        assertTrue(s1.sets.all { it.outcome == SetOutcome.DONE })
        assertEquals(listOf(1, 1, 2), s1.sets.map { it.setOrdinal })
        assertEquals(45, s1.sets[1].actualDurationSec)
        assertEquals(Presets.presetId("Standard Push-ups"), s1.sets[0].exerciseId)

        val s2 = data.sessions.first { it.id == "s2" }
        assertNull(s2.planId)
        assertEquals(8, s2.sets.single().actualReps) // numeric string tolerated
        assertEquals(5.5, s2.sets.single().weightKg)
    }

    @Test fun nativeBackupRoundTrips() {
        val data = Backup.decode(webExport, ids)
        val text = Backup.encode(data.exercises, data.plans, data.sessions, now = 42)
        val back = Backup.decode(text)
        assertEquals(BackupSource.ANDROID, back.source)
        assertEquals(data.exercises, back.exercises)
        assertEquals(data.plans, back.plans)
        assertEquals(data.sessions, back.sessions)
    }

    @Test fun rejectsOtherFiles() {
        assertFailsWith<BackupFormatException> { Backup.decode("not json") }
        assertFailsWith<BackupFormatException> { Backup.decode("""{"hello": 1}""") }
    }
}
