package com.skhealth.guardian.shared

/**
 * Converts a high-frequency sensor stream into alarm-engine evaluation points.
 *
 * PC-60FW is used intermittently, so transient values while the finger settles
 * must not trigger a remote alarm. SpO2 at or below recoveryThreshold starts a
 * 2-minute observation window. If SpO2 stays above recoveryThreshold for
 * recoveryStableMs, the pending alarm is cancelled. If the low state survives
 * the full window, readings are emitted in the shape expected by AlarmEngine.
 *
 * Heart-rate confirmation keeps the existing 2-minute high-frequency gate.
 */
class ContinuousAlarmGate(
    private var config: AlarmConfig = AlarmConfig(),
    private val confirmDelayMs: Long = 2 * 60_000L,
    private val recoveryThreshold: Int = 85,
    private val recoveryStableMs: Long = 10_000L
) {
    private var lowSpo2Since: Long? = null
    private var recoveryAboveSince: Long? = null
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
            val now = reading.timestampMs
            if (spo2 > recoveryThreshold) {
                val pending = lowSpo2Since
                if (pending != null) {
                    val recoverySince = recoveryAboveSince
                    if (recoverySince == null) {
                        recoveryAboveSince = now
                    } else if (now - recoverySince >= recoveryStableMs) {
                        lowSpo2Since = null
                        recoveryAboveSince = null
                    }
                } else {
                    recoveryAboveSince = null
                }
            } else {
                recoveryAboveSince = null
                val since = lowSpo2Since
                if (since == null) {
                    lowSpo2Since = now
                } else if (now - since >= confirmDelayMs) {
                    // The low state persisted for two minutes. For <80 one
                    // reading is enough for AlarmEngine's immediate critical
                    // rule. For 80..85 emit two evaluations now so its normal
                    // two-reading confirmation rule fires at the end of the
                    // observation window rather than two minutes later again.
                    val spo2Only = reading.copy(heartRate = null)
                    if (spo2 < config.spo2CriticalImmediate) {
                        out += spo2Only
                    } else {
                        out += spo2Only
                        out += spo2Only.copy(id = "${spo2Only.id}-confirm")
                    }
                    lowSpo2Since = null
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
        recoveryAboveSince = null
        highHrSince = null
        lowHrSince = null
    }
}
