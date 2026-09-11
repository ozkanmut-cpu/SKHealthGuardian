package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.GlucoseScheduleEngine

/**
 * Mobile-side bridge between medication events (eventually sourced from Dosefolk)
 * and Orko's daily blood-glucose checkpoints.
 *
 * Dosefolk remains the medication source of truth. Orko Takip only derives
 * glucose measurement targets from medication takenAt timestamps.
 */
object GlucoseScheduleCoordinator {
    private const val PREFS = "glucose_schedule"

    private const val KEY_MORNING_FIRST_GROUP_AT = "morning_first_group_at"
    private const val KEY_BREAKFAST_MED_AT = "breakfast_med_at"
    private const val KEY_BREAKFAST_TARGET_AT = "breakfast_target_at"
    private const val KEY_MIDDAY_TARGET_AT = "midday_target_at"
    private const val KEY_DINNER_MED_AT = "dinner_med_at"
    private const val KEY_DINNER_TARGET_AT = "dinner_target_at"
    private const val KEY_TOUJEO_AT = "toujeo_at"

    enum class MedicationAnchor {
        MORNING_FIRST_GROUP,
        MORNING_SECOND_POST_MEAL_GROUP,
        EVENING_COMBINED_POST_MEAL_GROUP,
        BEDTIME_TOUJEO
    }

    data class MedicationDerivedTargets(
        val morningFirstGroupTakenAtMs: Long?,
        val breakfastMedicationTakenAtMs: Long?,
        val breakfastPostMealTargetAtMs: Long?,
        val middayTargetAtMs: Long?,
        val dinnerMedicationTakenAtMs: Long?,
        val dinnerPostMealTargetAtMs: Long?,
        val toujeoTakenAtMs: Long?
    )

    /** Single entry point for medication events coming from Dosefolk. */
    fun onMedicationTaken(
        context: Context,
        anchor: MedicationAnchor,
        takenAtMs: Long = System.currentTimeMillis()
    ): List<GlucoseScheduleEngine.PlannedCheckpoint> = when (anchor) {
        MedicationAnchor.MORNING_FIRST_GROUP -> {
            onMorningFirstGroupTaken(context, takenAtMs)
            listOf(
                GlucoseScheduleEngine.checkpointAtMedicationTime(
                    GlucoseScheduleEngine.Checkpoint.MORNING_FASTING,
                    takenAtMs
                )
            )
        }

        MedicationAnchor.MORNING_SECOND_POST_MEAL_GROUP -> {
            onMorningPostMealMedicationTaken(context, takenAtMs)
            listOf(
                GlucoseScheduleEngine.postMealCheckpointFromMedication(
                    GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL,
                    takenAtMs
                ),
                GlucoseScheduleEngine.middayCheckpointFromMorningPostMealMedication(takenAtMs)
            )
        }

        MedicationAnchor.EVENING_COMBINED_POST_MEAL_GROUP -> {
            onEveningPostMealMedicationTaken(context, takenAtMs)
            listOf(
                GlucoseScheduleEngine.postMealCheckpointFromMedication(
                    GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL,
                    takenAtMs
                )
            )
        }

        MedicationAnchor.BEDTIME_TOUJEO -> {
            onToujeoTaken(context, takenAtMs)
            listOf(
                GlucoseScheduleEngine.checkpointAtMedicationTime(
                    GlucoseScheduleEngine.Checkpoint.BEDTIME,
                    takenAtMs
                )
            )
        }
    }

    fun onMorningFirstGroupTaken(context: Context, takenAtMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_MORNING_FIRST_GROUP_AT, takenAtMs)
            .apply()
        refreshReminders(context)
    }

    fun onMorningPostMealMedicationTaken(context: Context, takenAtMs: Long = System.currentTimeMillis()): Long {
        val breakfastTarget = GlucoseScheduleEngine.postMealTargetFromPostMealMedication(takenAtMs)
        val middayTarget = GlucoseScheduleEngine.middayTargetFromMorningPostMealMedication(takenAtMs)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_BREAKFAST_MED_AT, takenAtMs)
            .putLong(KEY_BREAKFAST_TARGET_AT, breakfastTarget)
            .putLong(KEY_MIDDAY_TARGET_AT, middayTarget)
            .apply()
        refreshReminders(context)
        return breakfastTarget
    }

    fun onEveningPostMealMedicationTaken(context: Context, takenAtMs: Long = System.currentTimeMillis()): Long {
        val target = GlucoseScheduleEngine.postMealTargetFromPostMealMedication(takenAtMs)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DINNER_MED_AT, takenAtMs)
            .putLong(KEY_DINNER_TARGET_AT, target)
            .apply()
        refreshReminders(context)
        return target
    }

    fun onToujeoTaken(context: Context, takenAtMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TOUJEO_AT, takenAtMs)
            .apply()
        refreshReminders(context)
    }

    fun load(context: Context): MedicationDerivedTargets {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun nullableLong(key: String): Long? = prefs.getLong(key, 0L).takeIf { it > 0L }
        return MedicationDerivedTargets(
            morningFirstGroupTakenAtMs = nullableLong(KEY_MORNING_FIRST_GROUP_AT),
            breakfastMedicationTakenAtMs = nullableLong(KEY_BREAKFAST_MED_AT),
            breakfastPostMealTargetAtMs = nullableLong(KEY_BREAKFAST_TARGET_AT),
            middayTargetAtMs = nullableLong(KEY_MIDDAY_TARGET_AT),
            dinnerMedicationTakenAtMs = nullableLong(KEY_DINNER_MED_AT),
            dinnerPostMealTargetAtMs = nullableLong(KEY_DINNER_TARGET_AT),
            toujeoTakenAtMs = nullableLong(KEY_TOUJEO_AT)
        )
    }

    fun plannedCheckpoints(context: Context): List<GlucoseScheduleEngine.PlannedCheckpoint> {
        val targets = load(context)
        return buildList {
            targets.morningFirstGroupTakenAtMs?.let {
                add(
                    GlucoseScheduleEngine.checkpointAtMedicationTime(
                        GlucoseScheduleEngine.Checkpoint.MORNING_FASTING,
                        it
                    )
                )
            }
            targets.breakfastMedicationTakenAtMs?.let {
                add(
                    GlucoseScheduleEngine.postMealCheckpointFromMedication(
                        GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL,
                        it
                    )
                )
                add(GlucoseScheduleEngine.middayCheckpointFromMorningPostMealMedication(it))
            }
            targets.dinnerMedicationTakenAtMs?.let {
                add(
                    GlucoseScheduleEngine.postMealCheckpointFromMedication(
                        GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL,
                        it
                    )
                )
            }
            targets.toujeoTakenAtMs?.let {
                add(
                    GlucoseScheduleEngine.checkpointAtMedicationTime(
                        GlucoseScheduleEngine.Checkpoint.BEDTIME,
                        it
                    )
                )
            }
        }.sortedBy { it.targetAtMs }
    }

    fun refreshReminders(context: Context) {
        runCatching { GlucoseReminderReceiver.reschedule(context) }
    }

    /** Backwards-compatible alias for callers that only need the post-meal pair. */
    fun plannedPostMealCheckpoints(context: Context): List<GlucoseScheduleEngine.PlannedCheckpoint> =
        plannedCheckpoints(context).filter {
            it.checkpoint == GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL ||
                it.checkpoint == GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL
        }
}
