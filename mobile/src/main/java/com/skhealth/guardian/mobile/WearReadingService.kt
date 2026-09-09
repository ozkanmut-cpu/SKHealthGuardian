package com.skhealth.guardian.mobile

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.skhealth.guardian.shared.AlarmEngine
import com.skhealth.guardian.shared.HealthReading

class WearReadingService : WearableListenerService() {
    private var engine: AlarmEngine? = null
    private var activeConfig: com.skhealth.guardian.shared.AlarmConfig? = null
    private var lastSpo2Pc60: Boolean? = null
    private var lastHrPc60: Boolean? = null

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/health/status") {
            WatchStatusStore.mark(this, String(event.data))
            return
        }
        if (event.path == "/health/heartbeat") {
            val p = String(event.data).split('|')
            val battery = p.getOrNull(1)?.toIntOrNull() ?: -1
            WatchHeartbeatStore.mark(this, battery)
            BatteryAlertHelper.update(this, "watch", "Saat", battery)
            return
        }
        if (event.path != "/health/reading") return

        val receivedAt = System.currentTimeMillis()
        val p = String(event.data).split('|')
        val reading = when {
            p.size >= 6 -> HealthReading(
                id = p[0],
                timestampMs = p[1].toLongOrNull() ?: receivedAt,
                spo2 = p[2].toIntOrNull(),
                heartRate = p[3].toIntOrNull(),
                valid = p[4].toBooleanStrictOrNull() ?: false,
                source = p[5]
            )
            p.size >= 5 -> HealthReading(
                timestampMs = p[0].toLongOrNull() ?: receivedAt,
                spo2 = p[1].toIntOrNull(),
                heartRate = p[2].toIntOrNull(),
                valid = p[3].toBooleanStrictOrNull() ?: false,
                source = p[4]
            )
            else -> return
        }

        if (HistoryStore.contains(this, reading.id)) return
        HistoryStore.add(this, reading)
        MonitoringState.markReading(this, receivedAt)
        SpO2ReliabilityStore.onWatchReading(this, reading.id, reading.timestampMs, reading.spo2, reading.valid, reading.heartRate)

        val cfg = AppSettings.load(this)
        val maxLiveAgeMs = maxOf(cfg.staleDataMs, MIN_LIVE_REPLAY_AGE_MS)
        val ageMs = (receivedAt - reading.timestampMs).coerceAtLeast(0L)
        if (ageMs > maxLiveAgeMs) return

        val spo2Pc60 = SourcePriorityCoordinator.isPc60Spo2Authoritative(this, receivedAt)
        val hrPc60 = SourcePriorityCoordinator.isPc60HeartRateAuthoritative(this, receivedAt)

        // Kaynak otoritesi değiştiğinde eski Watch doğrulama sayaçlarını taşımayız.
        if (lastSpo2Pc60 != null && (lastSpo2Pc60 != spo2Pc60 || lastHrPc60 != hrPc60)) {
            engine = null
            activeConfig = null
        }
        lastSpo2Pc60 = spo2Pc60
        lastHrPc60 = hrPc60

        if (spo2Pc60 || hrPc60) {
            val detail = when {
                spo2Pc60 && hrPc60 -> "Watch ölçümü kaydedildi; SpO₂ ve nabız alarm kararı PC-60FW'ye bırakıldı"
                spo2Pc60 -> "Watch ölçümü kaydedildi; SpO₂ alarm kararı PC-60FW'de, nabız Galaxy Watch'ta"
                else -> "Watch ölçümü kaydedildi; nabız alarm kararı PC-60FW'de, SpO₂ Galaxy Watch'ta"
            }
            AlarmTimelineStore.add(this, "KAYNAK ÖNCELİĞİ", detail)
        }

        val alarmReading = reading.copy(
            spo2 = if (spo2Pc60) null else reading.spo2,
            heartRate = if (hrPc60) null else reading.heartRate
        )
        if (alarmReading.spo2 == null && alarmReading.heartRate == null) return

        val e = if (engine == null || activeConfig != cfg) {
            activeConfig = cfg
            AlarmEngine(cfg).also { engine = it }
        } else engine!!
        val recent = HistoryStore.formatted(this, 4).lines().filter { it.isNotBlank() }
        e.evaluate(alarmReading).forEach { AlertDispatcher(this).dispatch(it, recent, alarmReading) }
    }

    companion object {
        private const val MIN_LIVE_REPLAY_AGE_MS = 10 * 60_000L
    }
}
