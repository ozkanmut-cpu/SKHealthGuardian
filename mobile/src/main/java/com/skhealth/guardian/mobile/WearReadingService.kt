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
        if (event.path != "/health/reading") return
        val p = String(event.data).split('|')
        if (p.size < 5) return
        val reading = HealthReading(
            timestampMs = p[0].toLongOrNull() ?: System.currentTimeMillis(),
            spo2 = p[1].toIntOrNull(),
            heartRate = p[2].toIntOrNull(),
            valid = p[3].toBooleanStrictOrNull() ?: false,
            source = p[4]
        )
        HistoryStore.add(this, reading)
        MonitoringState.markReading(this, reading.timestampMs)
        val cfg = AppSettings.load(this)
        val e = if (engine == null || activeConfig != cfg) {
            activeConfig = cfg
            AlarmEngine(cfg).also { engine = it }
        } else engine!!
        val recent = HistoryStore.formatted(this, 4).lines().filter { it.isNotBlank() }
        e.evaluate(reading).forEach { AlertDispatcher(this).dispatch(it, recent, reading) }
    }
}
