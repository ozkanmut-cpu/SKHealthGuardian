package com.skhealth.guardian.shared

enum class WatchSpO2Decision { NONE, RECOVERED, ALARM_IMMEDIATE, ALARM_EARLY }

data class WatchSpO2AlarmSnapshot(val observationSince: Long?, val alarmLatched: Boolean)

class WatchSpO2AlarmPolicy(
    private var immediateThreshold: Int = 75,
    private var recoveryThreshold: Int = 85,
    private var confirmationWindowMs: Long = 3 * 60_000L
) {
    private var observationSince: Long? = null
    private var alarmLatched = false

    fun update(immediateThreshold:Int,recoveryThreshold:Int,confirmationWindowMs:Long) {
        val changed = this.immediateThreshold != immediateThreshold || this.recoveryThreshold != recoveryThreshold || this.confirmationWindowMs != confirmationWindowMs
        this.immediateThreshold = immediateThreshold
        this.recoveryThreshold = recoveryThreshold
        this.confirmationWindowMs = confirmationWindowMs
        if (changed) reset()
    }

    fun evaluate(timestampMs: Long, spo2: Int?, valid: Boolean): WatchSpO2Decision {
        if (!valid || spo2 == null || spo2 !in 1..100) return WatchSpO2Decision.NONE
        if (spo2 >= recoveryThreshold) {
            val hadEpisode = observationSince != null || alarmLatched
            reset()
            return if (hadEpisode) WatchSpO2Decision.RECOVERED else WatchSpO2Decision.NONE
        }
        if (alarmLatched) return WatchSpO2Decision.NONE
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

    fun snapshot() = WatchSpO2AlarmSnapshot(observationSince, alarmLatched)
    fun restore(snapshot:WatchSpO2AlarmSnapshot){ observationSince=snapshot.observationSince;alarmLatched=snapshot.alarmLatched }
    fun reset(){ observationSince=null;alarmLatched=false }
}
