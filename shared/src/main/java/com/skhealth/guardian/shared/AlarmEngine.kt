package com.skhealth.guardian.shared

class AlarmEngine(private var config: AlarmConfig = AlarmConfig()) {
    private var lowSpo2Count = 0
    private var highHrCount = 0
    private var lowHrCount = 0

    fun updateConfig(newConfig: AlarmConfig) { config = newConfig; resetCounters() }

    fun evaluate(reading: HealthReading): List<AlertEvent> {
        if (!reading.valid) return emptyList()
        val alerts = mutableListOf<AlertEvent>()
        reading.spo2?.let { spo2 ->
            when {
                spo2 < config.spo2CriticalImmediate -> {
                    lowSpo2Count = 0
                    alerts += AlertEvent(AlertType.SPO2_CRITICAL, reading.timestampMs, reading, "Kritik SpO₂: %$spo2")
                }
                spo2 < config.spo2LowThreshold -> {
                    lowSpo2Count++
                    if (lowSpo2Count >= config.spo2ConfirmCount) {
                        alerts += AlertEvent(AlertType.SPO2_LOW_CONFIRMED, reading.timestampMs, reading, "Düşük SpO₂ doğrulandı: %$spo2")
                        lowSpo2Count = 0
                    }
                }
                else -> lowSpo2Count = 0
            }
        }
        reading.heartRate?.let { hr ->
            if (hr > config.heartRateHighThreshold) {
                highHrCount++
                if (highHrCount >= config.heartRateHighConfirmCount) {
                    alerts += AlertEvent(AlertType.HEART_RATE_HIGH_CONFIRMED, reading.timestampMs, reading, "Yüksek nabız doğrulandı: $hr bpm")
                    highHrCount = 0
                }
            } else highHrCount = 0
            if (config.heartRateLowEnabled && hr < config.heartRateLowThreshold) {
                lowHrCount++
                if (lowHrCount >= config.heartRateLowConfirmCount) {
                    alerts += AlertEvent(AlertType.HEART_RATE_LOW_CONFIRMED, reading.timestampMs, reading, "Düşük nabız doğrulandı: $hr bpm")
                    lowHrCount = 0
                }
            } else lowHrCount = 0
        }
        return alerts
    }

    private fun resetCounters() { lowSpo2Count = 0; highHrCount = 0; lowHrCount = 0 }
}
