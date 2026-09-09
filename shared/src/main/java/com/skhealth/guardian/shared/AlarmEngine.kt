package com.skhealth.guardian.shared

class AlarmEngine(private var config: AlarmConfig = AlarmConfig()) {
    private var lowSpo2Count = 0
    private var highHrCount = 0
    private var lowHrCount = 0

    private enum class Spo2Episode { NONE, LOW, CRITICAL }
    private var spo2Episode = Spo2Episode.NONE
    private var highHrEpisodeActive = false
    private var lowHrEpisodeActive = false

    fun updateConfig(newConfig: AlarmConfig) {
        config = newConfig
        resetAllState()
    }

    fun evaluate(reading: HealthReading): List<AlertEvent> {
        if (!reading.valid) return emptyList()
        val alerts = mutableListOf<AlertEvent>()

        reading.spo2?.let { spo2 ->
            when {
                spo2 >= config.spo2LowThreshold -> {
                    lowSpo2Count = 0
                    spo2Episode = Spo2Episode.NONE
                }

                spo2 < config.spo2CriticalImmediate -> {
                    lowSpo2Count = 0
                    if (spo2Episode != Spo2Episode.CRITICAL) {
                        alerts += AlertEvent(
                            AlertType.SPO2_CRITICAL,
                            reading.timestampMs,
                            reading,
                            "Kritik SpO₂: %$spo2"
                        )
                        spo2Episode = Spo2Episode.CRITICAL
                    }
                }

                else -> {
                    // A critical episode remains the same episode while SpO₂ is still below
                    // the normal threshold. Do not downgrade it into a second LOW alarm.
                    if (spo2Episode == Spo2Episode.NONE) {
                        lowSpo2Count++
                        if (lowSpo2Count >= config.spo2ConfirmCount) {
                            alerts += AlertEvent(
                                AlertType.SPO2_LOW_CONFIRMED,
                                reading.timestampMs,
                                reading,
                                "Düşük SpO₂ doğrulandı: %$spo2"
                            )
                            lowSpo2Count = 0
                            spo2Episode = Spo2Episode.LOW
                        }
                    } else {
                        lowSpo2Count = 0
                    }
                }
            }
        }

        reading.heartRate?.let { hr ->
            if (hr > config.heartRateHighThreshold) {
                if (!highHrEpisodeActive) {
                    highHrCount++
                    if (highHrCount >= config.heartRateHighConfirmCount) {
                        alerts += AlertEvent(
                            AlertType.HEART_RATE_HIGH_CONFIRMED,
                            reading.timestampMs,
                            reading,
                            "Yüksek nabız doğrulandı: $hr bpm"
                        )
                        highHrCount = 0
                        highHrEpisodeActive = true
                    }
                } else {
                    highHrCount = 0
                }
            } else {
                highHrCount = 0
                highHrEpisodeActive = false
            }

            if (config.heartRateLowEnabled && hr < config.heartRateLowThreshold) {
                if (!lowHrEpisodeActive) {
                    lowHrCount++
                    if (lowHrCount >= config.heartRateLowConfirmCount) {
                        alerts += AlertEvent(
                            AlertType.HEART_RATE_LOW_CONFIRMED,
                            reading.timestampMs,
                            reading,
                            "Düşük nabız doğrulandı: $hr bpm"
                        )
                        lowHrCount = 0
                        lowHrEpisodeActive = true
                    }
                } else {
                    lowHrCount = 0
                }
            } else {
                lowHrCount = 0
                lowHrEpisodeActive = false
            }
        }

        return alerts
    }

    private fun resetAllState() {
        lowSpo2Count = 0
        highHrCount = 0
        lowHrCount = 0
        spo2Episode = Spo2Episode.NONE
        highHrEpisodeActive = false
        lowHrEpisodeActive = false
    }
}
