package com.skhealth.guardian.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.skhealth.guardian.shared.BloodGlucoseReading
import com.skhealth.guardian.shared.GlucoseScheduleEngine

/** Handles the explicit Orko / other-person ownership decision. */
class GlucoseOwnershipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val readingId = intent.getStringExtra(EXTRA_READING_ID)?.takeIf { it.isNotBlank() } ?: return
        val ownership = when (intent.action) {
            ACTION_CONFIRM_ORKO -> BloodGlucoseReading.Ownership.ORKO
            ACTION_CONFIRM_OTHER -> BloodGlucoseReading.Ownership.OTHER_PERSON
            else -> return
        }

        val updated = BloodGlucoseStore.confirmOwnership(
            context = context,
            readingId = readingId,
            ownership = ownership
        ) ?: return

        GlucoseOwnershipNotification.dismiss(context, readingId)

        val completedSlot = if (updated.belongsToOrko) {
            GlucoseDailyPlan.statusForReading(context, updated)
                ?.takeIf { it.state == GlucoseDailyPlan.State.COMPLETED }
        } else {
            null
        }

        val old = AccuChekStatusStore.load(context)
        val pending = BloodGlucoseStore.pendingOwnership(context).size
        val ownershipState = when {
            !updated.belongsToOrko -> "Ölçüm başka kişi olarak ayrıldı"
            completedSlot != null -> "${checkpointLabel(completedSlot.checkpoint)} ölçümü tamamlandı"
            else -> "Ölçüm Orko olarak doğrulandı"
        }
        AccuChekStatusStore.save(
            context,
            old.copy(
                state = if (pending == 0) ownershipState else "$ownershipState • $pending ölçüm doğrulama bekliyor",
                pendingOwnershipCount = pending
            )
        )
    }

    private fun checkpointLabel(checkpoint: GlucoseScheduleEngine.Checkpoint): String = when (checkpoint) {
        GlucoseScheduleEngine.Checkpoint.MORNING_FASTING -> "Sabah açlık"
        GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL -> "Kahvaltı +2 saat"
        GlucoseScheduleEngine.Checkpoint.MIDDAY -> "Öğlen"
        GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL -> "Akşam +2 saat"
        GlucoseScheduleEngine.Checkpoint.BEDTIME -> "Yatmadan önce"
    }

    companion object {
        const val ACTION_CONFIRM_ORKO = "com.skhealth.guardian.mobile.GLUCOSE_CONFIRM_ORKO"
        const val ACTION_CONFIRM_OTHER = "com.skhealth.guardian.mobile.GLUCOSE_CONFIRM_OTHER"
        const val EXTRA_READING_ID = "reading_id"
    }
}
