package com.skhealth.guardian.shared

enum class WatchSpO2Decision {
    NONE,
    RECOVERED,
    ALARM_IMMEDIATE,
    ALARM_EARLY
}

/**
 * Galaxy Watch SpO2 policy optimized for on-demand watch measurements.
 *
 * The watch only enters an intensified follow-up episode after a valid SpO2
 * reading below 85%. Once a valid reading reaches 85% or above, the episode is
 * closed immediately and the watch returns to its normal measurement cadence.
 * Invalid/missed samples never create a health alarm and do not erase an active
 * low-reading observation window.
 */
class WatchSpO2AlarmPolicy(
    private val immediateThreshold: Int = 75,
    private val recoveryThreshold: Int = 85,
    private val confirmationWindowMs: Long = 3 * 60_000L
) {
    private var observationSince: Long? = null
    private var alarmLatched = false

    fun evaluate(timestampMs: Long, spo2: Int?, valid: Boolean): WatchSpO2Decision {
        if (!valid || spo2 == null || spo2 !in 1..100) {
            return WatchSpO2Decision.NONE
        }

        if (spo2 >= recoveryThreshold) {
            val hadEpisode = observationSince != null || alarmLatched
            reset()
            return if (hadEpisode) WatchSpO2Decision.RECOVERED else WatchSpO2Decision.NONE
        }

        if (alarmLatched) {
            return WatchSpO2Decision.NONE
        }

        if (spo2 < immediateThreshold) {
            alarmLatched = true
            return WatchSpO2Decision.ALARM_IMMEDIATE
        }

        val since = observationSince ?: timestampMs.also { observationSince = it }
        if (timestampMs - since >= confirmationWindowMs) {
            alarmLatched = true
            return WatchSpO2Decision.ALARM_EARLY
        }

        return WatchSpO2Decision.NONE
    }

    fun reset() {
        observationSince = null
        alarmLatched = false
    }
}
