package com.workouttracker.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatTest {
    @Test fun clockFormatsMinutesAndSeconds() {
        assertEquals("0:00", Format.clock(0))
        assertEquals("1:15", Format.clock(75))
        assertEquals("0:00", Format.clock(-5))
    }

    @Test fun clockCeilNeverShowsZeroEarly() {
        assertEquals("0:01", Format.clockCeil(1))
        assertEquals("0:01", Format.clockCeil(1000))
        assertEquals("0:02", Format.clockCeil(1001))
    }

    @Test fun elapsedSwitchesToHours() {
        assertEquals("12:34", Format.elapsed((12 * 60 + 34) * 1000L))
        assertEquals("1:02:03", Format.elapsed((3600 + 2 * 60 + 3) * 1000L))
    }

    @Test fun weightRoundTripsThroughPounds() {
        val kg = Units.fromDisplay(100.0, WeightUnit.LB)
        assertEquals(100.0, Units.toDisplay(kg, WeightUnit.LB), 1e-9)
        assertEquals("20 kg", Format.weight(20.0, WeightUnit.KG))
        assertEquals("22.5 kg", Format.weight(22.5, WeightUnit.KG))
    }

    @Test fun presetIdsAreStableAndUnique() {
        assertEquals("preset-dips-parallel-bars", Presets.presetId("Dips (Parallel Bars)"))
        val ids = Presets.exercises.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(60, ids.size)
        assertTrue(Presets.exercises.all { it.isPreset })
    }
}
