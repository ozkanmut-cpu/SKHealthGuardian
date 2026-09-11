package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.GlucoseScheduleEngine

/**
 * Mobile-side bridge for Orko's glucose plan.
 *
 * Morning/evening post-meal medication timestamps are treated as meal-end
 * proxies. With the configured 30-minute meal assumption, the post-meal
 * glucose target is medicationTakenAt + 90 minutes (meal start + 2 hours).
 */
object GlucoseScheduleCoordinator {
    private const val PREFS = "glucose_schedule"
    private const val KEY_BREAKFAST_MED_AT = "breakfast_med_at"
    private const val KEY_BREAKFAST_TARGET_AT = "breakfast_target_at"
    private const val KEY_DINNER_MED_AT = "dinner_med_at"
    private const val KEY_DINNER_TARGET_AT = "dinner_target_at"

    data class MedicationDerivedTargets(
        val breakfastMedicationTakenAtMs: Long?,
        val breakfastPostMealTargetAtMs: Long?,
        val dinnerMedicationTakenAtMs: Long?,
        val dinnerPostMealTargetAtMs: Long?
    )

    fun onMorningPostMealMedicationTaken(context: Context, takenAtMs: Long = System.currentTimeMillis()): Long {
        val target = GlucoseScheduleEngine.postMealTargetFromPostMealMedication(takenAtMs)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_BREAKFAST_MED_AT, takenAtMs)
            .putLong(KEY_BREAKFAST_TARGET_AT, target)
            .apply()
        return target
    }

    fun onEveningPostMealMedicationTaken(context: Context, takenAtMs: Long = System.currentTimeMillis()): Long {
        val target = GlucoseScheduleEngine.postMealTargetFromPostMealMedication(takenAtMs)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DINNER_MED_AT, takenAtMs)
            .putLong(KEY_DINNER_TARGET_AT, target)
            .apply()
        return target
    }

    fun load(context: Context): MedicationDerivedTargets {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun nullableLong(key: String): Long? = prefs.getLong(key, 0L).takeIf { it > 0L }
        return MedicationDerivedTargets(
            breakfastMedicationTakenAtMs = nullableLong(KEY_BREAKFAST_MED_AT),
            breakfastPostMealTargetAtMs = nullableLong(KEY_BREAKFAST_TARGET_AT),
            dinnerMedicationTakenAtMs = nullableLong(KEY_DINNER_MED_AT),
            dinnerPostMealTargetAtMs = nullableLong(KEY_DINNER_TARGET_AT)
        )
    }

    fun plannedPostMealCheckpoints(context: Context): List<GlucoseScheduleEngine.PlannedCheckpoint> {
        val targets = load(context)
        return buildList {
            targets.breakfastMedicationTakenAtMs?.let {
                add(
                    GlucoseScheduleEngine.postMealCheckpointFromMedication(
                        GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL,
                        it
                    )
                )
            }
            targets.dinnerMedicationTakenAtMs?.let {
                add(
                    GlucoseScheduleEngine.postMealCheckpointFromMedication(
                        GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL,
                        it
                    )
                )
            }
        }
    }
}
