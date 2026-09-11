package com.skhealth.guardian.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate

/**
 * Receives the data-minimised one-way medication timing bridge from Dosefolk.
 * Medication names/doses are intentionally not part of this protocol.
 */
class DosefolkMedicationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MEDICATION_TAKEN) return

        if (Build.VERSION.SDK_INT >= 34) {
            val sender = sentFromPackage
            if (sender != null && sender != DOSEFOLK_PACKAGE) return
        }

        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        val anchorName = intent.getStringExtra(EXTRA_ANCHOR).orEmpty()
        val takenAtMs = intent.getLongExtra(EXTRA_TAKEN_AT_MS, 0L)
        val scheduledDate = intent.getStringExtra(EXTRA_SCHEDULED_DATE).orEmpty()
            .ifBlank { LocalDate.now().toString() }
        val operation = intent.getStringExtra(EXTRA_OPERATION).orEmpty().ifBlank { OP_SET }

        if (eventId.isBlank() || takenAtMs <= 0L) return
        if (scheduledDate != LocalDate.now().toString()) return
        if (operation != OP_SET && operation != OP_CLEAR) return

        val anchor = runCatching {
            GlucoseScheduleCoordinator.MedicationAnchor.valueOf(anchorName)
        }.getOrNull() ?: return
        if (!markIfNew(context, eventId)) return

        when (operation) {
            OP_SET -> GlucoseScheduleCoordinator.onMedicationTaken(
                context = context,
                anchor = anchor,
                takenAtMs = takenAtMs,
                scheduledDate = scheduledDate
            )
            OP_CLEAR -> GlucoseScheduleCoordinator.onMedicationCleared(
                context = context,
                anchor = anchor,
                scheduledDate = scheduledDate
            )
        }
        DosefolkBridgeStatusStore.markReceived(
            context = context,
            eventId = eventId,
            anchor = anchor.name,
            takenAtMs = takenAtMs,
            operation = operation
        )
    }

    private fun markIfNew(context: Context, eventId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_EVENT_IDS, emptySet()).orEmpty().toMutableSet()
        if (eventId in current) return false
        current += eventId
        val bounded = current.toList().takeLast(MAX_EVENT_IDS).toSet()
        prefs.edit().putStringSet(KEY_EVENT_IDS, bounded).apply()
        return true
    }

    companion object {
        const val ACTION_MEDICATION_TAKEN = "com.dosefolk.action.ORKO_MEDICATION_TAKEN"
        const val DOSEFOLK_PACKAGE = "com.ozkanmut.ilactakip"
        private const val EXTRA_EVENT_ID = "eventId"
        private const val EXTRA_ANCHOR = "anchor"
        private const val EXTRA_TAKEN_AT_MS = "takenAtMs"
        private const val EXTRA_SCHEDULED_DATE = "scheduledDate"
        private const val EXTRA_OPERATION = "operation"
        private const val OP_SET = "SET"
        private const val OP_CLEAR = "CLEAR"
        private const val PREFS = "dosefolk_bridge"
        private const val KEY_EVENT_IDS = "processed_event_ids"
        private const val MAX_EVENT_IDS = 256
    }
}
