package com.skhealth.guardian.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.skhealth.guardian.shared.BloodGlucoseReading

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

        val old = AccuChekStatusStore.load(context)
        val pending = BloodGlucoseStore.pendingOwnership(context).size
        AccuChekStatusStore.save(
            context,
            old.copy(
                state = if (pending == 0) {
                    if (updated.belongsToOrko) "Ölçüm Orko olarak doğrulandı" else "Ölçüm başka kişi olarak ayrıldı"
                } else {
                    "$pending ölçüm doğrulama bekliyor"
                },
                pendingOwnershipCount = pending
            )
        )
    }

    companion object {
        const val ACTION_CONFIRM_ORKO = "com.skhealth.guardian.mobile.GLUCOSE_CONFIRM_ORKO"
        const val ACTION_CONFIRM_OTHER = "com.skhealth.guardian.mobile.GLUCOSE_CONFIRM_OTHER"
        const val EXTRA_READING_ID = "reading_id"
    }
}
