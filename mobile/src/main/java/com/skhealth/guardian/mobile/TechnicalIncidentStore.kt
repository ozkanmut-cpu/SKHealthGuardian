package com.skhealth.guardian.mobile

import android.content.Context

object TechnicalIncidentStore {
    private const val PREF = "technical_incident_state"
    private const val KEY_SENSOR_FAILURE_READING = "sensor_failure_for_reading"

    fun sensorFailureAlertedForReading(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_SENSOR_FAILURE_READING, 0L)

    fun markSensorFailureForReading(context: Context, readingTimestampMs: Long) {
        if (readingTimestampMs <= 0L) return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SENSOR_FAILURE_READING, readingTimestampMs)
            .commit()
    }
}
