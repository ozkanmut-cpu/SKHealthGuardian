package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.GlucoseScheduleEngine
import java.time.LocalDate

/**
 * Mobile-side bridge between medication events sourced from Dosefolk and
 * Orko's daily blood-glucose checkpoints.
 *
 * Dosefolk remains the medication source of truth. Orko Takip only derives
 * glucose measurement targets from medication takenAt timestamps.
 */
object GlucoseScheduleCoordinator {
    private const val PREFS = "glucose_schedule"

    private const val KEY_PLAN_DATE = "plan_date"
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
        takenAtMs: Long = System.currentTimeMillis(),
        scheduledDate: String = LocalDate.now().toString()
    ): List<GlucoseScheduleEngine.PlannedCheckpoint> {
        prepareDate(context, scheduledDate)
        return when (anchor) {
            MedicationAnchor.MORNING_FIRST_GROUP -> {
                onMorningFirstGroupTaken(context, takenAtMs, scheduledDate)
                listOf(
                    GlucoseScheduleEngine.checkpointAtMedicationTime(
                        GlucoseScheduleEngine.Checkpoint.MORNING_FASTING,
                        takenAtMs
                    )
                )
            }

            MedicationAnchor.MORNING_SECOND_POST_MEAL_GROUP -> {
                onMorningPostMealMedicationTaken(context, takenAtMs, scheduledDate)
                listOf(
                    GlucoseScheduleEngine.postMealCheckpointFromMedication(
                        GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL,
                        takenAtMs
                    ),
                    GlucoseScheduleEngine.middayCheckpointFromMorningPostMealMedication(takenAtMs)
                )
            }

            MedicationAnchor.EVENING_COMBINED_POST_MEAL_GROUP -> {
                onEveningPostMealMedicationTaken(context, takenAtMs, scheduledDate)
                listOf(
                    GlucoseScheduleEngine.postMealCheckpointFromMedication(
                        GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL,
                        takenAtMs
                    )
                )
            }

            MedicationAnchor.BEDTIME_TOUJEO -> {
                onToujeoTaken(context, takenAtMs, scheduledDate)
                listOf(
                    GlucoseScheduleEngine.checkpointAtMedicationTime(
                        GlucoseScheduleEngine.Checkpoint.BEDTIME,
                        takenAtMs
                    )
                )
            }
        }
    }

    /**
     * Compensating path for Dosefolk undo/missed corrections. Removing an anchor
     * also removes all checkpoints derived from it and immediately reschedules
     * reminders so a stale glucose target cannot survive a medication correction.
     */
    fun onMedicationCleared(
        context: Context,
        anchor: MedicationAnchor,
        scheduledDate: String = LocalDate.now().toString()
    ) {
        if (scheduledDate != LocalDate.now().toString()) return
        prepareDate(context, scheduledDate)
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        when (anchor) {
            MedicationAnchor.MORNING_FIRST_GROUP -> edit.remove(KEY_MORNING_FIRST_GROUP_AT)
            MedicationAnchor.MORNING_SECOND_POST_MEAL_GROUP -> edit
                .remove(KEY_BREAKFAST_MED_AT)
                .remove(KEY_BREAKFAST_TARGET_AT)
                .remove(KEY_MIDDAY_TARGET_AT)
            MedicationAnchor.EVENING_COMBINED_POST_MEAL_GROUP -> edit
                .remove(KEY_DINNER_MED_AT)
                .remove(KEY_DINNER_TARGET_AT)
            MedicationAnchor.BEDTIME_TOUJEO -> edit.remove(KEY_TOUJEO_AT)
        }
        edit.apply()
        refreshReminders(context)
    }

    fun onMorningFirstGroupTaken(
        context: Context,
        takenAtMs: Long = System.currentTimeMillis(),
        scheduledDate: String = LocalDate.now().toString()
    ) {
        prepareDate(context, scheduledDate)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_MORNING_FIRST_GROUP_AT, takenAtMs)
            .apply()
        refreshReminders(context)
    }

    fun onMorningPostMealMedicationTaken(
        context: Context,
        takenAtMs: Long = System.currentTimeMillis(),
        scheduledDate: String = LocalDate.now().toString()
    ): Long {
        prepareDate(context, scheduledDate)
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

    fun onEveningPostMealMedicationTaken(
        context: Context,
        takenAtMs: Long = System.currentTimeMillis(),
        scheduledDate: String = LocalDate.now().toString()
    ): Long {
        prepareDate(context, scheduledDate)
        val target = GlucoseScheduleEngine.postMealTargetFromPostMealMedication(takenAtMs)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DINNER_MED_AT, takenAtMs)
            .putLong(KEY_DINNER_TARGET_AT, target)
            .apply()
        refreshReminders(context)
        return target
    }

    fun onToujeoTaken(
        context: Context,
        takenAtMs: Long = System.currentTimeMillis(),
        scheduledDate: String = LocalDate.now().toString()
    ) {
        prepareDate(context, scheduledDate)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TOUJEO_AT, takenAtMs)
            .apply()
        refreshReminders(context)
    }

    fun load(context: Context): MedicationDerivedTargets {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_PLAN_DATE, "") != LocalDate.now().toString()) return emptyTargets()
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

    private fun prepareDate(context: Context, scheduledDate: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_PLAN_DATE, "") == scheduledDate) return
        prefs.edit().clear().putString(KEY_PLAN_DATE, scheduledDate).commit()
    }

    private fun emptyTargets() = MedicationDerivedTargets(null, null, null, null, null, null, null)
}
