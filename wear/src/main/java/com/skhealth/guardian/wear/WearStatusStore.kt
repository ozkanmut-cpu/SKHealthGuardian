package com.skhealth.guardian.wear

import android.content.Context
import com.skhealth.guardian.shared.HealthReading

data class WearStatus(
    val lastReadingAt: Long = 0L,
    val spo2: Int? = null,
    val heartRate: Int? = null
)

object WearStatusStore {
    private const val PREF = "wear_status"

    fun update(context: Context, reading: HealthReading) {
        if (!reading.valid) return
        val spo2 = reading.spo2
        val heartRate = reading.heartRate
        if (spo2 == null && heartRate == null) return

        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val e = p.edit()
        spo2?.let { e.putInt("spo2", it) }
        heartRate?.let { e.putInt("hr", it) }
        e.putLong("ts", reading.timestampMs)
        e.apply()
    }

    fun load(context: Context): WearStatus {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return WearStatus(
            lastReadingAt = p.getLong("ts", 0L),
            spo2 = if (p.contains("spo2")) p.getInt("spo2", 0) else null,
            heartRate = if (p.contains("hr")) p.getInt("hr", 0) else null
        )
    }
}
