package com.skhealth.guardian.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.AlarmConfig
import com.skhealth.guardian.shared.AlarmEngine
import com.skhealth.guardian.shared.HealthReading
import com.skhealth.guardian.wear.sensor.SamsungSensorGateway
import kotlinx.coroutines.*

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var sensor: SensorGateway
    private lateinit var bridge: PhoneBridge
    private val config = AlarmConfig()
    private val engine = AlarmEngine(config)

    override fun onCreate() {
        super.onCreate()
        sensor = SamsungSensorGateway(this)
        bridge = PhoneBridge(this)
        createChannel()
        startForeground(11, NotificationCompat.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sağlık izleme aktif")
            .setContentText("SpO₂ ve nabız 5 dakikada bir ölçülüyor")
            .setOngoing(true).build())
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                measureCycle()
                delay(5 * 60_000L)
            }
        }
    }

    private suspend fun measureCycle() {
        val wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "skhealth:measurement")
        wake.acquire(12 * 60_000L)
        try {
            val hr = measureHeartRateWithRetry()
            if (hr != null) {
                process(HealthReading(System.currentTimeMillis(), heartRate = hr))
                if (hr > config.heartRateHighThreshold) {
                    confirmHighHeartRate()
                }
            } else {
                process(HealthReading(System.currentTimeMillis(), valid = false))
            }

            val spo2 = measureSpO2WithRetry()
            if (spo2 != null) {
                process(HealthReading(System.currentTimeMillis(), spo2 = spo2))
                if (spo2 >= config.spo2CriticalImmediate && spo2 < config.spo2LowThreshold) {
                    confirmLowSpO2()
                }
            } else {
                process(HealthReading(System.currentTimeMillis(), valid = false))
            }
        } finally {
            if (wake.isHeld) wake.release()
        }
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

    private suspend fun confirmHighHeartRate() {
        delay(2 * 60_000L)
        val second = runCatching { sensor.measureHeartRate() }.getOrNull()
        if (second != null) {
            process(HealthReading(System.currentTimeMillis(), heartRate = second))
            return
        }
        runCatching { sensor.reconnect() }
        delay(60_000L)
        val third = runCatching { sensor.measureHeartRate() }.getOrNull()
        if (third != null) process(HealthReading(System.currentTimeMillis(), heartRate = third))
        else {
            runCatching { sensor.reconnect() }
            process(HealthReading(System.currentTimeMillis(), valid = false))
        }
    }

    private suspend fun confirmLowSpO2() {
        delay(2 * 60_000L)
        val second = runCatching { sensor.measureSpO2() }.getOrNull()
        if (second != null) {
            process(HealthReading(System.currentTimeMillis(), spo2 = second))
            return
        }
        runCatching { sensor.reconnect() }
        delay(60_000L)
        val third = runCatching { sensor.measureSpO2() }.getOrNull()
        if (third != null) process(HealthReading(System.currentTimeMillis(), spo2 = third))
        else {
            runCatching { sensor.reconnect() }
            process(HealthReading(System.currentTimeMillis(), valid = false))
        }
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
        runCatching { bridge.send(reading) }
        val alerts = engine.evaluate(reading)
        if (alerts.isNotEmpty()) LocalAlarm.raise(this, alerts.first())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MEASURE_NOW) scope.launch { measureCycle() }
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
    }
}
