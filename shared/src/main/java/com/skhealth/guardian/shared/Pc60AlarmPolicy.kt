package com.skhealth.guardian.shared

enum class Pc60Decision { NONE, RECOVERED, ALARM }

/**
 * Intermittent fingertip oximeter policy.
 * A valid SpO2 at or below alarmThreshold starts a confirmation window.
 * If SpO2 remains above recoveryThreshold for recoveryStableMs, the pending
 * alarm is cancelled. Invalid signal resets the pending session so probe-off
 * or searching time never counts toward the confirmation delay.
 */
class Pc60AlarmPolicy(
    private var alarmThreshold: Int = 85,
    private var confirmDelayMs: Long = 2 * 60_000L,
    private var recoveryThreshold: Int = 85,
    private var recoveryStableMs: Long = 10_000L
) {
    private var lowSince: Long? = null
    private var recoverySince: Long? = null

    fun update(
        alarmThreshold: Int,
        confirmDelayMs: Long,
        recoveryThreshold: Int,
        recoveryStableMs: Long
    ) {
        val changed = this.alarmThreshold != alarmThreshold ||
            this.confirmDelayMs != confirmDelayMs ||
            this.recoveryThreshold != recoveryThreshold ||
            this.recoveryStableMs != recoveryStableMs
        this.alarmThreshold = alarmThreshold
        this.confirmDelayMs = confirmDelayMs
        this.recoveryThreshold = recoveryThreshold
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

        if (spo2 <= alarmThreshold) {
            recoverySince = null
            val since = lowSince
            if (since == null) {
                lowSince = now
                return Pc60Decision.NONE
            }
            if (now - since >= confirmDelayMs) {
                reset()
                return Pc60Decision.ALARM
            }
            return Pc60Decision.NONE
        }

        if (lowSince != null && spo2 > recoveryThreshold) {
            val since = recoverySince
            if (since == null) {
                recoverySince = now
            } else if (now - since >= recoveryStableMs) {
                reset()
                return Pc60Decision.RECOVERED
            }
        } else if (spo2 <= recoveryThreshold) {
            recoverySince = null
        }

        return Pc60Decision.NONE
    }

    fun reset() {
        lowSince = null
        recoverySince = null
    }
}
