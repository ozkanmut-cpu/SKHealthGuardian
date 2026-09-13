package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.AlertType
import com.skhealth.guardian.shared.HealthReading
import com.skhealth.guardian.shared.MonotonicTimestampGate
import com.skhealth.guardian.shared.Pc60AlarmPolicy
import com.skhealth.guardian.shared.Pc60Decision
import com.skhealth.guardian.shared.Pc60HeartRatePolicy
import com.skhealth.guardian.shared.Pc60HrDecision
import com.skhealth.guardian.shared.Pc60Sample

class Pc60AlarmController(private val context: Context) {
    private var signature = ""
    private var policy = Pc60AlarmPolicy()
    private var hrPolicy = Pc60HeartRatePolicy()
    private val sampleGate = MonotonicTimestampGate(Pc60StatusStore.load(context).lastPacketAt)
    private var immediateThreshold = 75
    private var intermediateThreshold = 85
    private var fullRecoveryThreshold = 90
    private var earlyWindowMinutes = 3
    private var totalWindowMinutes = 5

    @Synchronized
    fun onSample(sample: Pc60Sample) {
        if (!sampleGate.accept(sample.timestampMs)) {
            AlarmTimelineStore.add(context,"PC-60FW STALE DROP","Gecikmiş paket yok sayıldı: sample=${sample.timestampMs}, son=${sampleGate.lastAccepted()}")
            return
        }
        refreshPolicyIfNeeded()
        val reading = sample.toHealthReading()
        HistoryStore.add(context, reading)
        if (sample.valid) MonitoringState.markReading(context, System.currentTimeMillis())
        SpO2ReliabilityStore.onPc60Reading(context, sample.timestampMs, sample.spo2, sample.valid, sample.pulseRate)
        val recent = HistoryStore.formatted(context, 4).lines().filter { it.isNotBlank() }

        when (policy.evaluate(sample)) {
            Pc60Decision.ALARM_IMMEDIATE -> dispatchSpo2Alert(sample,reading,recent,"PC-60FW SpO₂ %${sample.spo2}: anlık kritik eşik (<%$immediateThreshold)")
            Pc60Decision.ALARM_EARLY -> dispatchSpo2Alert(sample,reading,recent,"PC-60FW SpO₂ ilk $earlyWindowMinutes dakika içinde %$intermediateThreshold üzerine toparlanmadı: %${sample.spo2}")
            Pc60Decision.ALARM_TIMEOUT -> dispatchSpo2Alert(sample,reading,recent,"PC-60FW SpO₂ $totalWindowMinutes dakika içinde %$fullRecoveryThreshold üzerine toparlanmadı: %${sample.spo2}")
            Pc60Decision.RECOVERED -> AlarmTimelineStore.add(context,"PC-60FW TOPARLANDI","SpO₂ %${sample.spo2}; %$fullRecoveryThreshold üzeri stabil toparlanma doğrulandı")
            Pc60Decision.NONE -> Unit
        }

        val hrReading = HealthReading(timestampMs=sample.timestampMs,spo2=null,heartRate=sample.pulseRate.takeIf{!sample.probeOff&&!sample.pulseSearching&&it in 1..511&&sample.perfusionIndex>0.0},valid=!sample.probeOff&&!sample.pulseSearching&&sample.pulseRate in 1..511&&sample.perfusionIndex>0.0,source="pc60fw")
        when (hrPolicy.evaluate(sample)) {
            Pc60HrDecision.HIGH_ALARM -> AlertDispatcher(context).dispatch(AlertEvent(AlertType.HEART_RATE_HIGH_CONFIRMED,sample.timestampMs,hrReading,"PC-60FW yüksek nabız doğrulandı: ${sample.pulseRate} bpm"),recent,hrReading)
            Pc60HrDecision.LOW_ALARM -> AlertDispatcher(context).dispatch(AlertEvent(AlertType.HEART_RATE_LOW_CONFIRMED,sample.timestampMs,hrReading,"PC-60FW düşük nabız doğrulandı: ${sample.pulseRate} bpm"),recent,hrReading)
            Pc60HrDecision.NONE -> Unit
        }
    }

    private fun dispatchSpo2Alert(sample:Pc60Sample,reading:HealthReading,recent:List<String>,message:String){
        AlertDispatcher(context).dispatch(AlertEvent(AlertType.SPO2_LOW_CONFIRMED,sample.timestampMs,reading,message),recent,reading)
    }

    private fun refreshPolicyIfNeeded() {
        val cfg = AppSettings.load(context)
        val hrConfirmMinutes = AppSettings.watchConfirmMinutes(context)
        val immediate = AppSettings.pc60ImmediateThreshold(context)
        val intermediate = AppSettings.pc60IntermediateThreshold(context)
        val fullRecovery = AppSettings.pc60FullRecoveryThreshold(context)
        val earlyMinutes = AppSettings.pc60EarlyWindowMinutes(context)
        val totalMinutes = AppSettings.pc60TotalWindowMinutes(context)
        val stableSeconds = AppSettings.pc60StableSeconds(context)
        val newSignature = listOf(immediate,intermediate,fullRecovery,earlyMinutes,totalMinutes,stableSeconds,cfg.heartRateHighThreshold,cfg.heartRateHighConfirmCount,cfg.heartRateLowEnabled,cfg.heartRateLowThreshold,cfg.heartRateLowConfirmCount,hrConfirmMinutes).joinToString("|")
        if (newSignature == signature) return
        signature = newSignature
        immediateThreshold=immediate;intermediateThreshold=intermediate;fullRecoveryThreshold=fullRecovery;earlyWindowMinutes=earlyMinutes;totalWindowMinutes=totalMinutes
        policy.update(immediate,intermediate,fullRecovery,earlyMinutes*60_000L,totalMinutes*60_000L,stableSeconds*1_000L)
        hrPolicy.update(cfg.heartRateHighThreshold,cfg.heartRateHighConfirmCount,cfg.heartRateLowEnabled,cfg.heartRateLowThreshold,cfg.heartRateLowConfirmCount,hrConfirmMinutes*60_000L)
    }
}
