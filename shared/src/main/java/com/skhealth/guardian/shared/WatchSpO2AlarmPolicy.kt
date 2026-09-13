package com.skhealth.guardian.shared

enum class WatchSpO2Decision {
    NONE,
    RECOVERED,
    ALARM_IMMEDIATE,
    ALARM_EARLY,
    ALARM_TIMEOUT
}

/**
 * Galaxy Watch SpO2 policy aligned with the PC-60FW staged alarm model.
 *
 * The watch is sampled intermittently, so invalid/missed samples do not create a
 * medical alarm and do not erase the original low-reading observation window.
 * They only break a pending recovery streak. A health alarm is emitted only from
 * a valid low SpO2 sample.
 */
class WatchSpO2AlarmPolicy(
    private val immediateThreshold: Int = 75,
    private val intermediateThreshold: Int = 85,
    private val fullRecoveryThreshold: Int = 90,
    private val earlyWindowMs: Long = 3 * 60_000L,
    private val totalWindowMs: Long = 5 * 60_000L,
    private val recoveryStableMs: Long = 15_000L
) {
    private var observationSince: Long? = null
    private var recoverySince: Long? = null
    private var alarmLatched = false

    fun evaluate(timestampMs: Long, spo2: Int?, valid: Boolean): WatchSpO2Decision {
        if (!valid || spo2 == null || spo2 !in 1..100) {
            recoverySince = null
            return WatchSpO2Decision.NONE
        }

        if (alarmLatched) {
            if (spo2 >= fullRecoveryThreshold) {
                val since = recoverySince ?: timestampMs.also { recoverySince = it }
                if (timestampMs - since >= recoveryStableMs) {
                    reset()
                    return WatchSpO2Decision.RECOVERED
                }
            } else {
                recoverySince = null
            }
            return WatchSpO2Decision.NONE
        }

        if (spo2 < immediateThreshold) {
            alarmLatched = true
            recoverySince = null
            return WatchSpO2Decision.ALARM_IMMEDIATE
        }

        if (observationSince == null) {
            if (spo2 >= fullRecoveryThreshold) return WatchSpO2Decision.NONE
            observationSince = timestampMs
        }

        if (spo2 >= fullRecoveryThreshold) {
            val since = recoverySince ?: timestampMs.also { recoverySince = it }
            if (timestampMs - since >= recoveryStableMs) {
                reset()
                return WatchSpO2Decision.RECOVERED
            }
            return WatchSpO2Decision.NONE
        }

        recoverySince = null
        val elapsed = timestampMs - (observationSince ?: timestampMs)

        if (elapsed >= earlyWindowMs && spo2 < intermediateThreshold) {
            alarmLatched = true
            return WatchSpO2Decision.ALARM_EARLY
        }

        if (elapsed >= totalWindowMs) {
            alarmLatched = true
            return WatchSpO2Decision.ALARM_TIMEOUT
        }

        return WatchSpO2Decision.NONE
    }

    fun reset() {
        observationSince = null
        recoverySince = null
        alarmLatched = false
    }
}
