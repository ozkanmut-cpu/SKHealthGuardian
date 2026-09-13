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
import com.skhealth.guardian.shared.WatchSpO2AlarmPolicy
import com.skhealth.guardian.shared.WatchSpO2AlarmSnapshot
import com.skhealth.guardian.shared.WatchSpO2Decision
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
    private val watchSpO2Policy = WatchSpO2AlarmPolicy()
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
        restoreWatchSpO2Policy()
        sensor = SamsungSensorGateway(this)
        bridge = PhoneBridge(this)
        createChannel()
        val intervalMin = WearSettings.measurementIntervalMs(this) / 60_000L
        startForeground(11, NotificationCompat.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Orko Takip")
            .setContentText("Sağlık izleme aktif • SpO₂ ve nabız $intervalMin dakikada bir ölçülüyor")
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
                        if (!deliveredOrQueued) inMemoryTechnicalAlertRetry = alert
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
            val hrTriggerAt = System.currentTimeMillis()

            val hr = measureHeartRateWithRetry()
            if (hr != null) processGeneric(HealthReading(timestampMs = System.currentTimeMillis(), heartRate = hr))
            else recordAndSend(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))

            val spo2 = measureSpO2WithRetry()
            var lowSpo2StartedAt: Long? = null
            val followLowSpo2 = if (spo2 != null) {
                val reading = HealthReading(timestampMs = System.currentTimeMillis(), spo2 = spo2)
                val follow = processWatchSpO2(reading)
                if (follow) lowSpo2StartedAt = watchSpO2Policy.snapshot().observationSince ?: reading.timestampMs
                follow
            } else {
                recordAndSend(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))
                false
            }

            // SpO2 follow-up is time-sensitive. Do it before the slower HR confirmation path.
            if (followLowSpo2 && lowSpo2StartedAt != null) followLowSpO2(lowSpo2StartedAt)

            val confirmHr = hr != null && hr > activeConfig.heartRateHighThreshold
            if (confirmHr) confirmHeartRate(hrTriggerAt)
        } finally {
            if (wake.isHeld) wake.release()
        }
    }

    private suspend fun confirmHeartRate(triggerAt: Long) {
        val confirmDelayMs = WearSettings.confirmDelayMs(this)
        delayUntil(triggerAt + confirmDelayMs)
        val secondHr = runCatching { sensor.measureHeartRate() }.getOrNull()
        if (secondHr != null) {
            processGeneric(HealthReading(timestampMs = System.currentTimeMillis(), heartRate = secondHr))
            return
        }
        runCatching { sensor.reconnect() }
        delayUntil(triggerAt + confirmDelayMs + FINAL_RETRY_AFTER_CONFIRM_MS)
        val finalHr = runCatching { sensor.measureHeartRate() }.getOrNull()
        if (finalHr != null) processGeneric(HealthReading(timestampMs = System.currentTimeMillis(), heartRate = finalHr))
        else recordAndSend(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))
    }

    private suspend fun followLowSpO2(observationStartedAt: Long) {
        var nextAt = maxOf(System.currentTimeMillis(), observationStartedAt + WATCH_SPO2_FOLLOWUP_INTERVAL_MS)
        val stopAt = observationStartedAt + WATCH_SPO2_CONFIRMATION_WINDOW_MS
        while (scope.isActive && nextAt <= stopAt) {
            delayUntil(nextAt)
            val value = runCatching { sensor.measureSpO2(WATCH_SPO2_MEASUREMENT_TIMEOUT_MS) }.getOrNull()
            if (value == null) {
                recordAndSend(HealthReading(timestampMs = System.currentTimeMillis(), valid = false))
            } else {
                val keepFollowing = processWatchSpO2(
                    HealthReading(timestampMs = System.currentTimeMillis(), spo2 = value)
                )
                if (!keepFollowing) return
            }
            nextAt += WATCH_SPO2_FOLLOWUP_INTERVAL_MS
        }
    }

    private suspend fun processWatchSpO2(reading: HealthReading): Boolean {
        recordAndSend(reading)
        val spo2 = reading.spo2
        val decision = watchSpO2Policy.evaluate(reading.timestampMs, spo2, reading.valid)
        persistWatchSpO2Policy()
        when (decision) {
            WatchSpO2Decision.ALARM_IMMEDIATE -> {
                LocalAlarm.raise(this, AlertEvent(
                    AlertType.SPO2_CRITICAL,
                    reading.timestampMs,
                    reading,
                    "Saat SpO₂ kritik: %$spo2"
                ))
                return false
            }
            WatchSpO2Decision.ALARM_EARLY -> {
                LocalAlarm.raise(this, AlertEvent(
                    AlertType.SPO2_LOW_CONFIRMED,
                    reading.timestampMs,
                    reading,
                    "Saat SpO₂ 3 dakika boyunca %85 altından toparlanmadı: %$spo2"
                ))
                return false
            }
            WatchSpO2Decision.RECOVERED -> return false
            WatchSpO2Decision.NONE -> Unit
        }
        return spo2 != null && spo2 in 75..84
    }

    private fun restoreWatchSpO2Policy() {
        val prefs = getSharedPreferences(WATCH_SPO2_POLICY_PREFS, MODE_PRIVATE)
        val since = if (prefs.contains(KEY_OBSERVATION_SINCE)) prefs.getLong(KEY_OBSERVATION_SINCE, 0L) else null
        val latched = prefs.getBoolean(KEY_ALARM_LATCHED, false)
        watchSpO2Policy.restore(WatchSpO2AlarmSnapshot(since, latched))
    }

    private fun persistWatchSpO2Policy() {
        val snapshot = watchSpO2Policy.snapshot()
        val editor = getSharedPreferences(WATCH_SPO2_POLICY_PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ALARM_LATCHED, snapshot.alarmLatched)
        if (snapshot.observationSince == null) editor.remove(KEY_OBSERVATION_SINCE)
        else editor.putLong(KEY_OBSERVATION_SINCE, snapshot.observationSince)
        editor.apply()
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

    private suspend fun processGeneric(reading: HealthReading) {
        recordAndSend(reading)
        val engineReading = reading.copy(spo2 = null)
        val alerts = engine.evaluate(engineReading)
        persistEngineState()
        if (alerts.isNotEmpty()) LocalAlarm.raise(this, alerts.first())
    }

    private suspend fun recordAndSend(reading: HealthReading) {
        if (reading.valid && (reading.spo2 != null || reading.heartRate != null)) {
            lastValidReadingAt = reading.timestampMs
            WearStatusStore.update(this, reading)
        }
        runCatching { bridge.send(reading) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MEASURE_NOW) scope.launch { safeMeasureCycle() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun createChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("monitor", "Orko Takip • Sağlık izleme", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val ACTION_MEASURE_NOW = "com.skhealth.guardian.wear.MEASURE_NOW"
        private const val MIN_SENSOR_STALE_MS = 10 * 60_000L
        private const val FINAL_RETRY_AFTER_CONFIRM_MS = 60_000L
        private const val WATCH_SPO2_FOLLOWUP_INTERVAL_MS = 30_000L
        private const val WATCH_SPO2_CONFIRMATION_WINDOW_MS = 3 * 60_000L
        private const val WATCH_SPO2_MEASUREMENT_TIMEOUT_MS = 20_000L
        private const val WATCH_SPO2_POLICY_PREFS = "watch_spo2_policy"
        private const val KEY_OBSERVATION_SINCE = "observation_since"
        private const val KEY_ALARM_LATCHED = "alarm_latched"
    }
}
