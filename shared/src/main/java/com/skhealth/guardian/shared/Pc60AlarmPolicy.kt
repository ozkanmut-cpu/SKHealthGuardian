package com.skhealth.guardian.shared

enum class Pc60Decision {
    NONE,
    RECOVERED,
    ALARM_IMMEDIATE,
    ALARM_EARLY,
    ALARM_TIMEOUT
}

/**
 * Continuous fingertip oximeter policy for PC-60FW.
 *
 * User-configured staged behavior:
 * - SpO2 < immediateThreshold: alarm immediately.
 * - Otherwise, any valid reading below fullRecoveryThreshold starts an observation session.
 * - By earlyWindowMs the signal must have recovered to at least intermediateThreshold;
 *   otherwise alarm early.
 * - Even after reaching intermediateThreshold, recovery is not complete until SpO2 stays at or
 *   above fullRecoveryThreshold for recoveryStableMs.
 * - If that full recovery does not happen by totalWindowMs, alarm.
 *
 * Invalid/probe-off/searching samples never count toward a health alarm and reset the session.
 */
class Pc60AlarmPolicy(
    private var immediateThreshold: Int = 75,
    private var intermediateThreshold: Int = 85,
    private var fullRecoveryThreshold: Int = 90,
    private var earlyWindowMs: Long = 3 * 60_000L,
    private var totalWindowMs: Long = 5 * 60_000L,
    private var recoveryStableMs: Long = 15_000L
) {
    private var observationSince: Long? = null
    private var fullRecoverySince: Long? = null
    private var alarmLatched = false

    fun update(
        immediateThreshold: Int,
        intermediateThreshold: Int,
        fullRecoveryThreshold: Int,
        earlyWindowMs: Long,
        totalWindowMs: Long,
        recoveryStableMs: Long
    ) {
        val changed = this.immediateThreshold != immediateThreshold ||
            this.intermediateThreshold != intermediateThreshold ||
            this.fullRecoveryThreshold != fullRecoveryThreshold ||
            this.earlyWindowMs != earlyWindowMs ||
            this.totalWindowMs != totalWindowMs ||
            this.recoveryStableMs != recoveryStableMs
        this.immediateThreshold = immediateThreshold
        this.intermediateThreshold = intermediateThreshold
        this.fullRecoveryThreshold = fullRecoveryThreshold
        this.earlyWindowMs = earlyWindowMs
        this.totalWindowMs = totalWindowMs
        this.recoveryStableMs = recoveryStableMs
        if (changed) reset()
    }

    fun evaluate(sample: Pc60Sample): Pc60Decision {
        if (!sample.valid) {
            reset()
            return Pc60Decision.NONE
        }

        val now = sample.timestampMs
        val spo2 = sample.spo2

        if (alarmLatched) {
            return evaluateRecoveryAfterAlarm(now, spo2)
        }

        if (spo2 < immediateThreshold) {
            observationSince = now
            fullRecoverySince = null
            alarmLatched = true
            return Pc60Decision.ALARM_IMMEDIATE
        }

        if (observationSince == null) {
            if (spo2 >= fullRecoveryThreshold) return Pc60Decision.NONE
            observationSince = now
        }

        if (spo2 >= fullRecoveryThreshold) {
            val since = fullRecoverySince
            if (since == null) {
                fullRecoverySince = now
            } else if (now - since >= recoveryStableMs) {
                reset()
                return Pc60Decision.RECOVERED
            }
        } else {
            fullRecoverySince = null
        }

        val started = observationSince ?: return Pc60Decision.NONE
        val elapsed = now - started

        if (elapsed >= earlyWindowMs && spo2 < intermediateThreshold) {
            alarmLatched = true
            fullRecoverySince = null
            return Pc60Decision.ALARM_EARLY
        }

        if (elapsed >= totalWindowMs) {
            alarmLatched = true
            fullRecoverySince = null
            return Pc60Decision.ALARM_TIMEOUT
        }

        return Pc60Decision.NONE
    }

    private fun evaluateRecoveryAfterAlarm(now: Long, spo2: Int): Pc60Decision {
        if (spo2 >= fullRecoveryThreshold) {
            val since = fullRecoverySince
            if (since == null) {
                fullRecoverySince = now
            } else if (now - since >= recoveryStableMs) {
                reset()
                return Pc60Decision.RECOVERED
            }
        } else {
            fullRecoverySince = null
        }
        return Pc60Decision.NONE
    }

    fun reset() {
        observationSince = null
        fullRecoverySince = null
        alarmLatched = false
    }
}
