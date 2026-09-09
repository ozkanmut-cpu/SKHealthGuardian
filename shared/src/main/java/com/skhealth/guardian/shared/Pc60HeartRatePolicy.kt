package com.skhealth.guardian.shared

enum class Pc60HrDecision { NONE, HIGH_ALARM, LOW_ALARM }

class Pc60HeartRatePolicy(
    private var highThreshold: Int = 130,
    private var highConfirmCount: Int = 2,
    private var lowEnabled: Boolean = false,
    private var lowThreshold: Int = 45,
    private var lowConfirmCount: Int = 2,
    private var confirmIntervalMs: Long = 120_000L
) {
    private var highCount = 0
    private var lowCount = 0
    private var lastHighQualifiedAt = 0L
    private var lastLowQualifiedAt = 0L

    fun update(
        highThreshold: Int,
        highConfirmCount: Int,
        lowEnabled: Boolean,
        lowThreshold: Int,
        lowConfirmCount: Int,
        confirmIntervalMs: Long
    ) {
        this.highThreshold = highThreshold
        this.highConfirmCount = highConfirmCount.coerceAtLeast(1)
        this.lowEnabled = lowEnabled
        this.lowThreshold = lowThreshold
        this.lowConfirmCount = lowConfirmCount.coerceAtLeast(1)
        this.confirmIntervalMs = confirmIntervalMs.coerceAtLeast(1_000L)
        reset()
    }

    fun evaluate(sample: Pc60Sample): Pc60HrDecision {
        val hrValid = !sample.probeOff && !sample.pulseSearching &&
            sample.pulseRate in 1..511 && sample.perfusionIndex > 0.0
        if (!hrValid) {
            reset()
            return Pc60HrDecision.NONE
        }

        val now = sample.timestampMs
        if (sample.pulseRate > highThreshold) {
            lowCount = 0
            lastLowQualifiedAt = 0L
            if (highCount == 0) {
                highCount = 1
                lastHighQualifiedAt = now
            } else if (now - lastHighQualifiedAt >= confirmIntervalMs) {
                highCount++
                lastHighQualifiedAt = now
            }
            if (highCount >= highConfirmCount) {
                highCount = 0
                lastHighQualifiedAt = 0L
                return Pc60HrDecision.HIGH_ALARM
            }
            return Pc60HrDecision.NONE
        }

        highCount = 0
        lastHighQualifiedAt = 0L

        if (lowEnabled && sample.pulseRate < lowThreshold) {
            if (lowCount == 0) {
                lowCount = 1
                lastLowQualifiedAt = now
            } else if (now - lastLowQualifiedAt >= confirmIntervalMs) {
                lowCount++
                lastLowQualifiedAt = now
            }
            if (lowCount >= lowConfirmCount) {
                lowCount = 0
                lastLowQualifiedAt = 0L
                return Pc60HrDecision.LOW_ALARM
            }
        } else {
            lowCount = 0
            lastLowQualifiedAt = 0L
        }
        return Pc60HrDecision.NONE
    }

    private fun reset() {
        highCount = 0
        lowCount = 0
        lastHighQualifiedAt = 0L
        lastLowQualifiedAt = 0L
    }
}
