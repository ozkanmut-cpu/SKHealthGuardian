package com.skhealth.guardian.shared

/**
 * Converts a high-frequency sensor stream into alarm-engine evaluation points.
 * Critical SpO2 is never delayed. Values that require confirmation get one
 * initial evaluation and one confirmation evaluation only after confirmDelayMs.
 */
class ContinuousAlarmGate(
    private var config: AlarmConfig = AlarmConfig(),
    private val confirmDelayMs: Long = 2 * 60_000L
) {
    private var lowSpo2Since: Long? = null
    private var highHrSince: Long? = null
    private var lowHrSince: Long? = null

    fun updateConfig(newConfig: AlarmConfig) {
        config = newConfig
        reset()
    }

    fun select(reading: HealthReading): List<HealthReading> {
        if (!reading.valid) return emptyList()
        val out = mutableListOf<HealthReading>()

        reading.spo2?.let { spo2 ->
            when {
                spo2 < config.spo2CriticalImmediate -> {
                    lowSpo2Since = null
                    out += reading.copy(heartRate = null)
                }
                spo2 < config.spo2LowThreshold -> {
                    val since = lowSpo2Since
                    if (since == null) {
                        lowSpo2Since = reading.timestampMs
                        out += reading.copy(heartRate = null)
                    } else if (reading.timestampMs - since >= confirmDelayMs) {
                        lowSpo2Since = null
                        out += reading.copy(heartRate = null)
                    }
                }
                else -> {
                    lowSpo2Since = null
                    out += reading.copy(heartRate = null)
                }
            }
        }

        reading.heartRate?.let { hr ->
            if (hr > config.heartRateHighThreshold) {
                val since = highHrSince
                if (since == null) {
                    highHrSince = reading.timestampMs
                    out += reading.copy(spo2 = null)
                } else if (reading.timestampMs - since >= confirmDelayMs) {
                    highHrSince = null
                    out += reading.copy(spo2 = null)
                }
            } else {
                highHrSince = null
                out += reading.copy(spo2 = null)
            }

            if (config.heartRateLowEnabled) {
                if (hr < config.heartRateLowThreshold) {
                    val since = lowHrSince
                    if (since == null) lowHrSince = reading.timestampMs
                    else if (reading.timestampMs - since >= confirmDelayMs) lowHrSince = null
                } else lowHrSince = null
            }
        }
        return out
    }

    fun reset() {
        lowSpo2Since = null
        highHrSince = null
        lowHrSince = null
    }
}
