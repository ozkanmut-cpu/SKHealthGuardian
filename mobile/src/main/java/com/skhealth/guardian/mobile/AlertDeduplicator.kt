package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.AlertDedupPolicy
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.AlertIdentity
import com.skhealth.guardian.shared.AlertType

object AlertDeduplicator {
    /**
     * Claim is process-atomic: two concurrent producers evaluating the same alert type
     * cannot both pass the read/check/write window and trigger duplicate SMS/call dispatch.
     * SharedPreferences keeps the claim across service/process recreation.
     *
     * Exact alarm identity separates a replay of the same alarm from a genuinely new alarm.
     * If the previous exact alarm was acknowledged, a new alarm instance must not be hidden by
     * the coarse five-minute type cooldown. Continuous unacknowledged alarms still use the
     * cooldown/worsening policy to avoid notification/SMS/call storms.
     */
    fun shouldDispatch(context: Context, alert: AlertEvent): Boolean = synchronized(this) {
        val prefs = context.getSharedPreferences("alert_dedup", Context.MODE_PRIVATE)
        val key = alert.type.name
        val currentId = AlertIdentity.of(alert)
        val lastId = prefs.getString("${key}_id", null)

        // Same exact alarm replay is always a duplicate, regardless of elapsed time.
        if (lastId == currentId) return@synchronized false

        val now = System.currentTimeMillis()
        val lastAt = prefs.getLong("${key}_at", 0L)
        val storedLastValue = prefs.getInt("${key}_value", Int.MIN_VALUE)
        val lastValue = storedLastValue.takeUnless { it == Int.MIN_VALUE }
        val value = when (alert.type) {
            AlertType.SPO2_CRITICAL, AlertType.SPO2_LOW_CONFIRMED -> alert.reading?.spo2
            AlertType.HEART_RATE_HIGH_CONFIRMED, AlertType.HEART_RATE_LOW_CONFIRMED -> alert.reading?.heartRate
            else -> null
        }

        val previousWasAcknowledged = lastId != null &&
            AlertIdentity.isValid(lastId) &&
            AlertAcknowledgementStore.isAcknowledged(context, lastId)

        if (!previousWasAcknowledged &&
            !AlertDedupPolicy.shouldDispatch(alert.type, now, lastAt, lastValue, value)
        ) {
            return@synchronized false
        }

        val editor = prefs.edit()
            .putLong("${key}_at", now)
            .putString("${key}_id", currentId)
        if (value != null) editor.putInt("${key}_value", value)
        editor.commit()
        true
    }
}
