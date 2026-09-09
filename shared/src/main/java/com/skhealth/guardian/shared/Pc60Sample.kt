package com.skhealth.guardian.shared

data class Pc60Sample(
    val timestampMs: Long,
    val spo2: Int,
    val pulseRate: Int,
    val perfusionIndex: Double,
    val probeOff: Boolean,
    val pulseSearching: Boolean,
    val batteryLevel: Int? = null
) {
    val valid: Boolean
        get() = !probeOff && !pulseSearching && spo2 in 1..100 && pulseRate in 1..511 && perfusionIndex > 0.0

    fun toHealthReading(): HealthReading = HealthReading(
        timestampMs = timestampMs,
        spo2 = spo2.takeIf { valid },
        heartRate = pulseRate.takeIf { valid },
        valid = valid,
        source = "pc60fw"
    )
}
