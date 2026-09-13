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

    @Synchronized
    fun onSample(sample: Pc60Sample) {
        if (!sampleGate.accept(sample.timestampMs)) {
            AlarmTimelineStore.add(
                context,
                "PC-60FW STALE DROP",
                "Gecikmiş paket yok sayıldı: sample=${sample.timestampMs}, son=${sampleGate.lastAccepted()}"
            )
            return
        }

        refreshPolicyIfNeeded()

        val reading = sample.toHealthReading()
        HistoryStore.add(context, reading)
        if (sample.valid) MonitoringState.markReading(context, System.currentTimeMillis())
        SpO2ReliabilityStore.onPc60Reading(context, sample.timestampMs, sample.spo2, sample.valid, sample.pulseRate)

        val recent = HistoryStore.formatted(context, 4).lines().filter { it.isNotBlank() }

        when (policy.evaluate(sample)) {
            Pc60Decision.ALARM_IMMEDIATE -> dispatchSpo2Alert(
                sample,
                reading,
                recent,
                "PC-60FW SpO₂ %${sample.spo2}: anlık kritik eşik (<%$IMMEDIATE_THRESHOLD)"
            )
            Pc60Decision.ALARM_EARLY -> dispatchSpo2Alert(
                sample,
                reading,
                recent,
                "PC-60FW SpO₂ ilk 3 dakika içinde %$INTERMEDIATE_THRESHOLD üzerine toparlanmadı: %${sample.spo2}"
            )
            Pc60Decision.ALARM_TIMEOUT -> dispatchSpo2Alert(
                sample,
                reading,
                recent,
                "PC-60FW SpO₂ 5 dakika içinde %$FULL_RECOVERY_THRESHOLD üzerine toparlanmadı: %${sample.spo2}"
            )
            Pc60Decision.RECOVERED -> {
                AlarmTimelineStore.add(
                    context,
                    "PC-60FW TOPARLANDI",
                    "SpO₂ %${sample.spo2}; %$FULL_RECOVERY_THRESHOLD üzeri stabil toparlanma doğrulandı"
                )
            }
            Pc60Decision.NONE -> Unit
        }

        val hrReading = HealthReading(
            timestampMs = sample.timestampMs,
            spo2 = null,
            heartRate = sample.pulseRate.takeIf {
                !sample.probeOff && !sample.pulseSearching && it in 1..511 && sample.perfusionIndex > 0.0
            },
            valid = !sample.probeOff && !sample.pulseSearching && sample.pulseRate in 1..511 && sample.perfusionIndex > 0.0,
            source = "pc60fw"
        )
        when (hrPolicy.evaluate(sample)) {
            Pc60HrDecision.HIGH_ALARM -> AlertDispatcher(context).dispatch(
                AlertEvent(
                    type = AlertType.HEART_RATE_HIGH_CONFIRMED,
                    timestampMs = sample.timestampMs,
                    reading = hrReading,
                    message = "PC-60FW yüksek nabız doğrulandı: ${sample.pulseRate} bpm"
                ),
                recent,
                hrReading
            )
            Pc60HrDecision.LOW_ALARM -> AlertDispatcher(context).dispatch(
                AlertEvent(
                    type = AlertType.HEART_RATE_LOW_CONFIRMED,
                    timestampMs = sample.timestampMs,
                    reading = hrReading,
                    message = "PC-60FW düşük nabız doğrulandı: ${sample.pulseRate} bpm"
                ),
                recent,
                hrReading
            )
            Pc60HrDecision.NONE -> Unit
        }
    }

    private fun dispatchSpo2Alert(
        sample: Pc60Sample,
        reading: HealthReading,
        recent: List<String>,
        message: String
    ) {
        AlertDispatcher(context).dispatch(
            AlertEvent(
                type = AlertType.SPO2_LOW_CONFIRMED,
                timestampMs = sample.timestampMs,
                reading = reading,
                message = message
            ),
            recent,
            reading
        )
    }

    private fun refreshPolicyIfNeeded() {
        val cfg = AppSettings.load(context)
        val hrConfirmMinutes = AppSettings.watchConfirmMinutes(context)
        val newSignature = listOf(
            IMMEDIATE_THRESHOLD,
            INTERMEDIATE_THRESHOLD,
            FULL_RECOVERY_THRESHOLD,
            EARLY_WINDOW_MS,
            TOTAL_WINDOW_MS,
            RECOVERY_STABLE_MS,
            cfg.heartRateHighThreshold,
            cfg.heartRateHighConfirmCount,
            cfg.heartRateLowEnabled,
            cfg.heartRateLowThreshold,
            cfg.heartRateLowConfirmCount,
            hrConfirmMinutes
        ).joinToString("|")
        if (newSignature == signature) return
        signature = newSignature
        policy.update(
            immediateThreshold = IMMEDIATE_THRESHOLD,
            intermediateThreshold = INTERMEDIATE_THRESHOLD,
            fullRecoveryThreshold = FULL_RECOVERY_THRESHOLD,
            earlyWindowMs = EARLY_WINDOW_MS,
            totalWindowMs = TOTAL_WINDOW_MS,
            recoveryStableMs = RECOVERY_STABLE_MS
        )
        hrPolicy.update(
            highThreshold = cfg.heartRateHighThreshold,
            highConfirmCount = cfg.heartRateHighConfirmCount,
            lowEnabled = cfg.heartRateLowEnabled,
            lowThreshold = cfg.heartRateLowThreshold,
            lowConfirmCount = cfg.heartRateLowConfirmCount,
            confirmIntervalMs = hrConfirmMinutes * 60_000L
        )
    }

    companion object {
        private const val IMMEDIATE_THRESHOLD = 75
        private const val INTERMEDIATE_THRESHOLD = 85
        private const val FULL_RECOVERY_THRESHOLD = 90
        private const val EARLY_WINDOW_MS = 3 * 60_000L
        private const val TOTAL_WINDOW_MS = 5 * 60_000L
        private const val RECOVERY_STABLE_MS = 15_000L
    }
}
