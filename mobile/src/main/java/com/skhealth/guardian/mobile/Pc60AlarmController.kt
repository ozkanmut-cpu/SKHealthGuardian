package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.AlertType
import com.skhealth.guardian.shared.Pc60AlarmPolicy
import com.skhealth.guardian.shared.Pc60Decision
import com.skhealth.guardian.shared.Pc60Sample

class Pc60AlarmController(private val context: Context) {
    private var signature = ""
    private var policy = Pc60AlarmPolicy()

    fun onSample(sample: Pc60Sample) {
        refreshPolicyIfNeeded()

        val reading = sample.toHealthReading()
        HistoryStore.add(context, reading)
        if (sample.valid) MonitoringState.markReading(context, System.currentTimeMillis())
        SpO2ReliabilityStore.onPc60Reading(context, sample.timestampMs, sample.spo2, sample.valid)

        when (policy.evaluate(sample)) {
            Pc60Decision.ALARM -> {
                val recent = HistoryStore.formatted(context, 4).lines().filter { it.isNotBlank() }
                val alert = AlertEvent(
                    type = AlertType.SPO2_LOW_CONFIRMED,
                    timestampMs = sample.timestampMs,
                    reading = reading,
                    message = "PC-60FW düşük SpO₂ ${AppSettings.pc60ConfirmMinutes(context)} dk boyunca düzelmedi: %${sample.spo2}"
                )
                AlertDispatcher(context).dispatch(alert, recent, reading)
            }
            Pc60Decision.RECOVERED -> {
                AlarmTimelineStore.add(
                    context,
                    "PC-60FW TOPARLANDI",
                    "SpO₂ %${sample.spo2}; alarm beklemesi iptal edildi"
                )
            }
            Pc60Decision.NONE -> Unit
        }
    }

    private fun refreshPolicyIfNeeded() {
        val alarm = AppSettings.pc60AlarmThreshold(context)
        val confirm = AppSettings.pc60ConfirmMinutes(context)
        val recovery = AppSettings.pc60RecoveryThreshold(context)
        val stable = AppSettings.pc60StableSeconds(context)
        val newSignature = "$alarm|$confirm|$recovery|$stable"
        if (newSignature == signature) return
        signature = newSignature
        policy.update(
            alarmThreshold = alarm,
            confirmDelayMs = confirm * 60_000L,
            recoveryThreshold = recovery,
            recoveryStableMs = stable * 1_000L
        )
    }
}
