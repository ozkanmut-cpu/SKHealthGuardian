package com.skhealth.guardian.shared

/**
 * Pure scheduling/classification logic for Orko's blood-glucose routine.
 *
 * Agreed medication anchors:
 * - morning first medication group -> morning fasting measurement
 * - morning second (post-meal) group -> breakfast meal-end proxy
 * - evening combined post-meal group -> dinner meal-end proxy
 * - bedtime Toujeo -> bedtime measurement
 *
 * A meal is assumed to last 30 minutes. Therefore a post-meal medication
 * timestamp implies:
 *   mealStart = medicationTakenAt - 30 min
 *   postMealTarget = mealStart + 2 h = medicationTakenAt + 90 min
 *   middayTarget = breakfastMealStart + 5 h = morning post-meal medication + 4 h 30 min
 */
object GlucoseScheduleEngine {
    const val DEFAULT_MEAL_DURATION_MS = 30L * 60_000L
    const val POST_MEAL_FROM_START_MS = 2L * 60L * 60_000L
    const val MIDDAY_FROM_BREAKFAST_START_MS = 5L * 60L * 60_000L
    const val POST_MEAL_FROM_MEDICATION_MS = POST_MEAL_FROM_START_MS - DEFAULT_MEAL_DURATION_MS
    const val MIDDAY_FROM_BREAKFAST_MEDICATION_MS = MIDDAY_FROM_BREAKFAST_START_MS - DEFAULT_MEAL_DURATION_MS

    /** Default tolerance for matching a reading to a planned checkpoint. */
    const val DEFAULT_WINDOW_BEFORE_MS = 30L * 60_000L
    const val DEFAULT_WINDOW_AFTER_MS = 45L * 60_000L

    enum class Checkpoint {
        MORNING_FASTING,
        BREAKFAST_POST_MEAL,
        MIDDAY,
        DINNER_POST_MEAL,
        BEDTIME
    }

    data class PlannedCheckpoint(
        val checkpoint: Checkpoint,
        val targetAtMs: Long,
        val windowStartMs: Long = targetAtMs - DEFAULT_WINDOW_BEFORE_MS,
        val windowEndMs: Long = targetAtMs + DEFAULT_WINDOW_AFTER_MS,
        val inferredFromMedication: Boolean = false
    ) {
        fun contains(timestampMs: Long): Boolean = timestampMs in windowStartMs..windowEndMs
    }

    data class Match(
        val checkpoint: Checkpoint,
        val targetAtMs: Long,
        val deltaMs: Long,
        val inferredFromMedication: Boolean
    )

    fun mealStartFromPostMealMedication(
        medicationTakenAtMs: Long,
        assumedMealDurationMs: Long = DEFAULT_MEAL_DURATION_MS
    ): Long = medicationTakenAtMs - assumedMealDurationMs

    fun postMealTargetFromMealStart(mealStartedAtMs: Long): Long =
        mealStartedAtMs + POST_MEAL_FROM_START_MS

    fun middayTargetFromBreakfastStart(breakfastStartedAtMs: Long): Long =
        breakfastStartedAtMs + MIDDAY_FROM_BREAKFAST_START_MS

    fun postMealTargetFromPostMealMedication(
        medicationTakenAtMs: Long,
        assumedMealDurationMs: Long = DEFAULT_MEAL_DURATION_MS
    ): Long = postMealTargetFromMealStart(
        mealStartFromPostMealMedication(medicationTakenAtMs, assumedMealDurationMs)
    )

    fun middayTargetFromMorningPostMealMedication(
        medicationTakenAtMs: Long,
        assumedMealDurationMs: Long = DEFAULT_MEAL_DURATION_MS
    ): Long = middayTargetFromBreakfastStart(
        mealStartFromPostMealMedication(medicationTakenAtMs, assumedMealDurationMs)
    )

    fun checkpointAtMedicationTime(
        checkpoint: Checkpoint,
        medicationTakenAtMs: Long,
        windowBeforeMs: Long = DEFAULT_WINDOW_BEFORE_MS,
        windowAfterMs: Long = DEFAULT_WINDOW_AFTER_MS
    ): PlannedCheckpoint {
        require(checkpoint == Checkpoint.MORNING_FASTING || checkpoint == Checkpoint.BEDTIME) {
            "Medication-time anchoring is only valid for morning fasting and bedtime checkpoints"
        }
        return PlannedCheckpoint(
            checkpoint = checkpoint,
            targetAtMs = medicationTakenAtMs,
            windowStartMs = medicationTakenAtMs - windowBeforeMs,
            windowEndMs = medicationTakenAtMs + windowAfterMs,
            inferredFromMedication = true
        )
    }

    fun postMealCheckpointFromMedication(
        checkpoint: Checkpoint,
        medicationTakenAtMs: Long,
        assumedMealDurationMs: Long = DEFAULT_MEAL_DURATION_MS,
        windowBeforeMs: Long = DEFAULT_WINDOW_BEFORE_MS,
        windowAfterMs: Long = DEFAULT_WINDOW_AFTER_MS
    ): PlannedCheckpoint {
        require(checkpoint == Checkpoint.BREAKFAST_POST_MEAL || checkpoint == Checkpoint.DINNER_POST_MEAL) {
            "Medication-derived scheduling is only valid for post-meal checkpoints"
        }
        val target = postMealTargetFromPostMealMedication(medicationTakenAtMs, assumedMealDurationMs)
        return PlannedCheckpoint(
            checkpoint = checkpoint,
            targetAtMs = target,
            windowStartMs = target - windowBeforeMs,
            windowEndMs = target + windowAfterMs,
            inferredFromMedication = true
        )
    }

    fun middayCheckpointFromMorningPostMealMedication(
        medicationTakenAtMs: Long,
        assumedMealDurationMs: Long = DEFAULT_MEAL_DURATION_MS,
        windowBeforeMs: Long = DEFAULT_WINDOW_BEFORE_MS,
        windowAfterMs: Long = DEFAULT_WINDOW_AFTER_MS
    ): PlannedCheckpoint {
        val target = middayTargetFromMorningPostMealMedication(medicationTakenAtMs, assumedMealDurationMs)
        return PlannedCheckpoint(
            checkpoint = Checkpoint.MIDDAY,
            targetAtMs = target,
            windowStartMs = target - windowBeforeMs,
            windowEndMs = target + windowAfterMs,
            inferredFromMedication = true
        )
    }

    /**
     * Returns the closest eligible planned checkpoint for a reading.
     * This is only a scheduling hint. It must NOT be used as proof that the
     * reading belongs to Orko; ownership remains independently confirmed.
     */
    fun matchReading(
        readingAtMs: Long,
        planned: Collection<PlannedCheckpoint>
    ): Match? = planned
        .asSequence()
        .filter { it.contains(readingAtMs) }
        .map {
            Match(
                checkpoint = it.checkpoint,
                targetAtMs = it.targetAtMs,
                deltaMs = readingAtMs - it.targetAtMs,
                inferredFromMedication = it.inferredFromMedication
            )
        }
        .minWithOrNull(compareBy<Match> { kotlin.math.abs(it.deltaMs) }.thenBy { it.targetAtMs })

    /**
     * A planned checkpoint is complete only when a reading confirmed as Orko's
     * falls within its window. Measurements by other people never complete it.
     */
    fun isCompleted(
        checkpoint: PlannedCheckpoint,
        confirmedOrkoReadingTimesMs: Collection<Long>
    ): Boolean = confirmedOrkoReadingTimesMs.any(checkpoint::contains)
}
