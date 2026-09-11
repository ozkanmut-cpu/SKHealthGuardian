package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseScheduleEngineTest {
    @Test
    fun `post meal target is 90 minutes after medication when meal lasts 30 minutes`() {
        val medicationTakenAt = 8L * 60L * 60_000L + 30L * 60_000L // 08:30

        val mealStart = GlucoseScheduleEngine.mealStartFromPostMealMedication(medicationTakenAt)
        val target = GlucoseScheduleEngine.postMealTargetFromPostMealMedication(medicationTakenAt)

        assertEquals(8L * 60L * 60_000L, mealStart) // 08:00
        assertEquals(10L * 60L * 60_000L, target)   // 10:00
    }

    @Test
    fun `dinner medication follows same meal start rule`() {
        val medicationTakenAt = 20L * 60L * 60_000L + 15L * 60_000L // 20:15
        val target = GlucoseScheduleEngine.postMealTargetFromPostMealMedication(medicationTakenAt)

        assertEquals(21L * 60L * 60_000L + 45L * 60_000L, target) // 21:45
    }

    @Test
    fun `medication derived checkpoints are restricted to breakfast and dinner post meal`() {
        val breakfast = GlucoseScheduleEngine.postMealCheckpointFromMedication(
            GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL,
            1_000_000L
        )
        assertTrue(breakfast.inferredFromMedication)

        var failed = false
        try {
            GlucoseScheduleEngine.postMealCheckpointFromMedication(
                GlucoseScheduleEngine.Checkpoint.MIDDAY,
                1_000_000L
            )
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `reading is matched to closest planned checkpoint only inside window`() {
        val first = GlucoseScheduleEngine.PlannedCheckpoint(
            checkpoint = GlucoseScheduleEngine.Checkpoint.MORNING_FASTING,
            targetAtMs = 8L * 60L * 60_000L,
            windowStartMs = 7L * 60L * 60_000L + 30L * 60_000L,
            windowEndMs = 8L * 60L * 60_000L + 30L * 60_000L
        )
        val breakfastPost = GlucoseScheduleEngine.PlannedCheckpoint(
            checkpoint = GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL,
            targetAtMs = 10L * 60L * 60_000L,
            windowStartMs = 9L * 60L * 60_000L + 30L * 60_000L,
            windowEndMs = 10L * 60L * 60_000L + 45L * 60_000L
        )

        val matched = GlucoseScheduleEngine.matchReading(
            10L * 60L * 60_000L + 5L * 60_000L,
            listOf(first, breakfastPost)
        )
        assertNotNull(matched)
        assertEquals(GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL, matched!!.checkpoint)

        assertNull(
            GlucoseScheduleEngine.matchReading(
                12L * 60L * 60_000L,
                listOf(first, breakfastPost)
            )
        )
    }

    @Test
    fun `only confirmed Orko readings complete a checkpoint`() {
        val checkpoint = GlucoseScheduleEngine.PlannedCheckpoint(
            checkpoint = GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL,
            targetAtMs = 22L * 60L * 60_000L,
            windowStartMs = 21L * 60L * 60_000L + 30L * 60_000L,
            windowEndMs = 22L * 60L * 60_000L + 45L * 60_000L
        )

        val otherPersonsReading = 22L * 60L * 60_000L
        val orkosReading = 22L * 60L * 60_000L + 10L * 60_000L

        assertFalse(GlucoseScheduleEngine.isCompleted(checkpoint, emptyList()))
        assertTrue(GlucoseScheduleEngine.isCompleted(checkpoint, listOf(orkosReading)))
        assertFalse(
            GlucoseScheduleEngine.isCompleted(
                checkpoint,
                listOf(otherPersonsReading).filter { false }
            )
        )
    }
}
