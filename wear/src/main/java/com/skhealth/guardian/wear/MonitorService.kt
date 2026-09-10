package com.skhealth.guardian.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.AlertType
import com.skhealth.guardian.shared.AlarmConfig
import com.skhealth.guardian.shared.AlarmEngine
import com.skhealth.guardian.shared.HealthReading
import com.skhealth.guardian.wear.sensor.SamsungSensorGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val measurementMutex = Mutex()
    private lateinit var sensor: SensorGateway
    private lateinit var bridge: PhoneBridge
    private var activeConfig = AlarmConfig()
    private var engine = AlarmEngine(activeConfig)
    private var engineSignature = ""
    private var lastValidReadingAt = 0L
    private var serviceStartedAt = 0L
    private var lastSensorFailureAlertAt = 0L
    @Volatile private var inMemoryTechnicalAlertRetry: AlertEvent? = null
    @Volatile private var engineStateDirty = false

    override fun onCreate() {
        super.onCreate()
        serviceStartedAt = System.currentTimeMillis()
        activeConfig = WearSettings.load(this)
        engineSignature = WearAlarmEngineStateStore.signature(activeConfig)
        engine = AlarmEngine(activeConfig).also { restoredEngine ->
            WearAlarmEngineStateStore.load(this, engineSignature)?.let(restoredEngine::restore)
        }
        sensor = SamsungSensorGateway(this)
        bridge = PhoneBridge(this)
        createChannel()
        val intervalMin = WearSettings.measurementIntervalMs(this) / 60_000L
        startForeground(11, NotificationCompat.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sağlık izleme aktif")
            .setContentText("SpO₂ ve nabız $intervalMin dakikada bir ölçülüyor")
            .setOngoing(true).build())
        startHeartbeat()
        startMonitoring()
        startSensorWatchdog()
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                retryEngineStatePersistence()
                retryInMemoryTechnicalAlert()
                runCatching { bridge.sendHeartbeat(batteryPct()) }
                delay(60_000L)
            }
        }
    }

    private fun persistEngineState() {
        engineStateDirty = !WearAlarmEngineStateStore.save(this, engineSignature, engine.snapshot())
    }

    private fun retryEngineStatePersistence() {
        if (!engineStateDirty) return
        persistEngineState()
    }

    private suspend fun retryInMemoryTechnicalAlert() {
        val pending = inMemoryTechnicalAlertRetry ?: return
        val deliveredOrQueued = runCatching { bridge.sendAlert(pending) }.getOrDefault(false)
        if (deliveredOrQueued && inMemoryTechnicalAlertRetry?.eventId == pending.eventId) {
            inMemoryTechnicalAlertRetry = null
        }
    }

    private fun batteryPct(): Int = getSystemService(BatteryManager::class.java)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        .coerceIn(0, 100)

    private fun startMonitoring() {
        scope.launch {
            safeMeasureCycle()
            while (isActive) {
                val intervalMs = WearSettings.measurementIntervalMs(this@MonitorService)
                val now = System.currentTimeMillis()
                val next = ((now / intervalMs) + 1L) * intervalMs
                delay((next - now).coerceAtLeast(0L))
                safeMeasureCycle()
            }
        }
    }

    private fun startSensorWatchdog() {
        scope.launch {
            while (isActive) {
                delay(60_000L)
                val now = System.currentTimeMillis()
                val reference = if (lastValidReadingAt > 0) lastValidReadingAt else serviceStartedAt
                val timeout = maxOf(activeConfig.staleDataMs, MIN_SENSOR_STALE_MS)
                if (now - reference > timeout) {
                    runCatching { sensor.reconnect() }
                    safeMeasureCycle()
                    val recoveredReference = if (lastValidReadingAt > 0) lastValidReadingAt else serviceStartedAt
                    if (System.currentTimeMillis() - recoveredReference > timeout && now - lastSensorFailureAlertAt > timeout) {
                        lastSensorFailureAlertAt = now
                        val alert = AlertEvent(
                            AlertType.SENSOR_FAILURE,
                            now,
                            null,
                            "Saat sensörü uzun süredir geçerli ölçüm üretemiyor"
                        )
                        LocalAlarm.raise(this@MonitorService, alert)
                        val deliveredOrQueued = runCatching { bridge.sendAlert(alert) }.getOrDefault(false)
                        if (!deliveredOrQueued) {
                            inMemoryTechnicalAlertRetry = alert
                        }
                    }
                }
            }
        }
    }

    private suspend fun safeMeasureCycle() = measurementMutex.withLock { measureCycle() }

    private suspend fun measureCycle() {
        val latest = WearSettings.load(this)
        if (latest != activeConfig) {
            activeConfig = latest
            engineSignature = WearAlarmEngineStateStore.signature(activeConfig)
            engine = AlarmEngine(activeConfig)
            persistEngineState()
        }

        val wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "skhealth:measurement")
        wake.acquire(12 * 60_000L)
        try {
            val triggerAt = System.currentTimeMillis()

            val hr = measureHeartRateWithRetry()
            if (hr != null) process(HealthReading(timestampMs = System.currentTimeMillis(), heartRate = hr))
            else process(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))

            val spo2 = measureSpO2WithRetry()
            if (spo2 != null) process(HealthReading(timestampMs = System.currentTimeMillis(), spo2 = spo2))
            else process(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))

            val confirmHr = hr != null && hr > activeConfig.heartRateHighThreshold
            val confirmSpo2 = spo2 != null &&
                spo2 >= activeConfig.spo2CriticalImmediate &&
                spo2 < activeConfig.spo2LowThreshold

            if (confirmHr || confirmSpo2) {
                confirmTriggeredReadings(triggerAt, confirmHr, confirmSpo2)
            }
        } finally {
            if (wake.isHeld) wake.release()
        }
    }

    private suspend fun confirmTriggeredReadings(triggerAt: Long, confirmHr: Boolean, confirmSpo2: Boolean) {
        val confirmDelayMs = WearSettings.confirmDelayMs(this)
        delayUntil(triggerAt + confirmDelayMs)
        var retryHr = false
        var retrySpo2 = false

        if (confirmHr) {
            val secondHr = runCatching { sensor.measureHeartRate() }.getOrNull()
            if (secondHr != null) process(HealthReading(timestampMs = System.currentTimeMillis(), heartRate = secondHr)) else retryHr = true
        }
        if (confirmSpo2) {
            val secondSpo2 = runCatching { sensor.measureSpO2() }.getOrNull()
            if (secondSpo2 != null) process(HealthReading(timestampMs = System.currentTimeMillis(), spo2 = secondSpo2)) else retrySpo2 = true
        }
        if (!retryHr && !retrySpo2) return

        runCatching { sensor.reconnect() }
        delayUntil(triggerAt + confirmDelayMs + FINAL_RETRY_AFTER_CONFIRM_MS)
        if (retryHr) {
            val finalHr = runCatching { sensor.measureHeartRate() }.getOrNull()
            if (finalHr != null) process(HealthReading(timestampMs = System.currentTimeMillis(), heartRate = finalHr)) else process(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))
        }
        if (retrySpo2) {
            val finalSpo2 = runCatching { sensor.measureSpO2() }.getOrNull()
            if (finalSpo2 != null) process(HealthReading(timestampMs = System.currentTimeMillis(), spo2 = finalSpo2)) else process(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))
        }
    }

    private suspend fun delayUntil(targetMs: Long) {
        val remaining = targetMs - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
    }

    private suspend fun measureHeartRateWithRetry(): Int? {
        for (wait in WearSettings.retryWaitsMs(this)) {
            if (wait > 0) delay(wait)
            val value = runCatching { sensor.measureHeartRate() }.getOrNull()
            if (value != null) return value
            runCatching { sensor.reconnect() }
        }
        return null
    }

    private suspend fun measureSpO2WithRetry(): Int? {
        for (wait in WearSettings.retryWaitsMs(this)) {
            if (wait > 0) delay(wait)
            val value = runCatching { sensor.measureSpO2() }.getOrNull()
            if (value != null) return value
            runCatching { sensor.reconnect() }
        }
        return null
    }

    private suspend fun process(reading: HealthReading) {
        if (reading.valid && (reading.spo2 != null || reading.heartRate != null)) {
            lastValidReadingAt = reading.timestampMs
            WearStatusStore.update(this, reading)
        }
        runCatching { bridge.send(reading) }
        val alerts = engine.evaluate(reading)
        persistEngineState()
        if (alerts.isNotEmpty()) LocalAlarm.raise(this, alerts.first())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MEASURE_NOW) scope.launch { safeMeasureCycle() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun createChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("monitor", "Health monitoring", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val ACTION_MEASURE_NOW = "com.skhealth.guardian.wear.MEASURE_NOW"
        private const val MIN_SENSOR_STALE_MS = 10 * 60_000L
        private const val FINAL_RETRY_AFTER_CONFIRM_MS = 60_000L
    }
}
