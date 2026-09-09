package com.skhealth.guardian.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
    private var lastValidReadingAt = 0L
    private var serviceStartedAt = 0L
    private var lastSensorFailureAlertAt = 0L

    override fun onCreate() {
        super.onCreate()
        serviceStartedAt = System.currentTimeMillis()
        activeConfig = WearSettings.load(this)
        engine = AlarmEngine(activeConfig)
        sensor = SamsungSensorGateway(this)
        bridge = PhoneBridge(this)
        createChannel()
        startForeground(11, NotificationCompat.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sağlık izleme aktif")
            .setContentText("SpO₂ ve nabız 5 dakikada bir ölçülüyor")
            .setOngoing(true).build())
        startMonitoring()
        startSensorWatchdog()
    }

    private fun startMonitoring() {
        scope.launch {
            safeMeasureCycle()
            while (isActive) {
                val now = System.currentTimeMillis()
                val next = ((now / INTERVAL_MS) + 1L) * INTERVAL_MS
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
                        LocalAlarm.raise(
                            this@MonitorService,
                            AlertEvent(
                                AlertType.SENSOR_FAILURE,
                                now,
                                null,
                                "Saat sensörü uzun süredir geçerli ölçüm üretemiyor"
                            )
                        )
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
            engine = AlarmEngine(activeConfig)
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
        delayUntil(triggerAt + CONFIRM_DELAY_MS)

        var retryHr = false
        var retrySpo2 = false

        if (confirmHr) {
            val secondHr = runCatching { sensor.measureHeartRate() }.getOrNull()
            if (secondHr != null) process(HealthReading(timestampMs = System.currentTimeMillis(), heartRate = secondHr))
            else retryHr = true
        }

        if (confirmSpo2) {
            val secondSpo2 = runCatching { sensor.measureSpO2() }.getOrNull()
            if (secondSpo2 != null) process(HealthReading(timestampMs = System.currentTimeMillis(), spo2 = secondSpo2))
            else retrySpo2 = true
        }

        if (!retryHr && !retrySpo2) return

        runCatching { sensor.reconnect() }
        delayUntil(triggerAt + FINAL_RETRY_AT_MS)

        if (retryHr) {
            val finalHr = runCatching { sensor.measureHeartRate() }.getOrNull()
            if (finalHr != null) process(HealthReading(timestampMs = System.currentTimeMillis(), heartRate = finalHr))
            else process(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))
        }

        if (retrySpo2) {
            val finalSpo2 = runCatching { sensor.measureSpO2() }.getOrNull()
            if (finalSpo2 != null) process(HealthReading(timestampMs = System.currentTimeMillis(), spo2 = finalSpo2))
            else process(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))
        }
    }

    private suspend fun delayUntil(targetMs: Long) {
        val remaining = targetMs - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
    }

    private suspend fun measureHeartRateWithRetry(): Int? {
        val waits = listOf(0L, 30_000L, 60_000L)
        for (wait in waits) {
            if (wait > 0) delay(wait)
            val value = runCatching { sensor.measureHeartRate() }.getOrNull()
            if (value != null) return value
            runCatching { sensor.reconnect() }
        }
        return null
    }

    private suspend fun measureSpO2WithRetry(): Int? {
        val waits = listOf(0L, 30_000L, 60_000L)
        for (wait in waits) {
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
        }
        runCatching { bridge.send(reading) }
        val alerts = engine.evaluate(reading)
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
        private const val INTERVAL_MS = 5 * 60_000L
        private const val MIN_SENSOR_STALE_MS = 10 * 60_000L
        private const val CONFIRM_DELAY_MS = 2 * 60_000L
        private const val FINAL_RETRY_AT_MS = 3 * 60_000L
    }
}
