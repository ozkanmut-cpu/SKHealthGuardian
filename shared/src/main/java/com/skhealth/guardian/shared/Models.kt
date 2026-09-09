package com.skhealth.guardian.shared

import java.util.UUID

data class HealthReading(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long,
    val spo2: Int? = null,
    val heartRate: Int? = null,
    val valid: Boolean = true,
    val source: String = "galaxy_watch8"
)

data class AlarmConfig(
    val spo2CriticalImmediate: Int = 80,
    val spo2LowThreshold: Int = 90,
    val spo2ConfirmCount: Int = 2,
    val heartRateHighThreshold: Int = 130,
    val heartRateHighConfirmCount: Int = 2,
    val heartRateLowEnabled: Boolean = false,
    val heartRateLowThreshold: Int = 45,
    val heartRateLowConfirmCount: Int = 2,
    val staleDataMs: Long = 10 * 60_000L
)

enum class AlertType {
    SPO2_CRITICAL,
    SPO2_LOW_CONFIRMED,
    HEART_RATE_HIGH_CONFIRMED,
    HEART_RATE_LOW_CONFIRMED,
    DATA_STALE,
    SENSOR_FAILURE,
    WATCH_DISCONNECTED
}

data class AlertEvent(
    val type: AlertType,
    val timestampMs: Long,
    val reading: HealthReading? = null,
    val message: String,
    val eventId: String = UUID.randomUUID().toString()
)
