package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.AlertDedupPolicy
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.AlertType

object AlertDeduplicator {
    fun shouldDispatch(context: Context, alert: AlertEvent): Boolean {
        val prefs = context.getSharedPreferences("alert_dedup", Context.MODE_PRIVATE)
        val key = alert.type.name
        val now = System.currentTimeMillis()
        val lastAt = prefs.getLong("${key}_at", 0L)
        val storedLastValue = prefs.getInt("${key}_value", Int.MIN_VALUE)
        val lastValue = storedLastValue.takeUnless { it == Int.MIN_VALUE }
        val value = when (alert.type) {
            AlertType.SPO2_CRITICAL, AlertType.SPO2_LOW_CONFIRMED -> alert.reading?.spo2
            AlertType.HEART_RATE_HIGH_CONFIRMED, AlertType.HEART_RATE_LOW_CONFIRMED -> alert.reading?.heartRate
            else -> null
        }

        if (!AlertDedupPolicy.shouldDispatch(alert.type, now, lastAt, lastValue, value)) return false

        prefs.edit()
            .putLong("${key}_at", now)
            .apply { if (value != null) putInt("${key}_value", value) }
            .apply()
        return true
    }
}
