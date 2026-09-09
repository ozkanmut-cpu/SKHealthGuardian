package com.skhealth.guardian.mobile

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.skhealth.guardian.shared.AlarmEngine
import com.skhealth.guardian.shared.HealthReading

class WearReadingService : WearableListenerService() {
    private var engine: AlarmEngine? = null
    private var activeConfig: com.skhealth.guardian.shared.AlarmConfig? = null

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
        SpO2ReliabilityStore.onWatchReading(this, reading.id, reading.timestampMs, reading.spo2, reading.valid)

        val cfg = AppSettings.load(this)
        val maxLiveAgeMs = maxOf(cfg.staleDataMs, MIN_LIVE_REPLAY_AGE_MS)
        val ageMs = (receivedAt - reading.timestampMs).coerceAtLeast(0L)
        if (ageMs > maxLiveAgeMs) return

        val pc60OwnsSpo2 = reading.spo2 != null && SourcePriorityCoordinator.isPc60Authoritative(this, receivedAt)
        val readingForAlarm = if (pc60OwnsSpo2) {
            AlarmTimelineStore.add(
                this,
                "KAYNAK ÖNCELİĞİ",
                "Watch SpO₂ %${reading.spo2} karşılaştırma için kaydedildi; SpO₂ alarm kararı PC-60FW'ye bırakıldı. Watch nabız alarmı aktif kalır."
            )
            HealthReading(
                id = reading.id,
                timestampMs = reading.timestampMs,
                spo2 = null,
                heartRate = reading.heartRate,
                valid = reading.valid,
                source = reading.source
            )
        } else reading

        val e = if (engine == null || activeConfig != cfg) {
            activeConfig = cfg
            AlarmEngine(cfg).also { engine = it }
        } else engine!!
        val recent = HistoryStore.formatted(this, 4).lines().filter { it.isNotBlank() }
        e.evaluate(readingForAlarm).forEach { AlertDispatcher(this).dispatch(it, recent, reading) }
    }

    companion object {
        private const val MIN_LIVE_REPLAY_AGE_MS = 10 * 60_000L
    }
}
