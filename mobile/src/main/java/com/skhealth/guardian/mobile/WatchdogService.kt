package com.skhealth.guardian.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.AlertType
import com.skhealth.guardian.shared.WatchConnectionPolicy
import com.skhealth.guardian.shared.WatchdogPolicy
import kotlinx.coroutines.*

class WatchdogService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("watchdog", "Health watchdog", NotificationManager.IMPORTANCE_LOW))
        startForeground(21, NotificationCompat.Builder(this, "watchdog").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SK Health Guardian aktif").setContentText("Sağlık verisi izleniyor").setOngoing(true).build())
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val last = MonitoringState.lastReading(this@WatchdogService)
                val stale = AppSettings.load(this@WatchdogService).staleDataMs
                val lastAlertedFor = MonitoringState.lastStaleAlertedFor(this@WatchdogService)
                if (WatchdogPolicy.shouldAlert(now, last, stale, lastAlertedFor)) {
                    MonitoringState.markStaleAlertedFor(this@WatchdogService, last)
                    AlertDispatcher(this@WatchdogService).dispatch(
                        AlertEvent(AlertType.DATA_STALE, now, null, "${stale / 60_000} dakikadır geçerli sağlık verisi gelmiyor"),
                        HistoryStore.formatted(this@WatchdogService, 4).lines().filter { it.isNotBlank() }
                    )
                }

                val lastHeartbeat = WatchHeartbeatStore.timestamp(this@WatchdogService)
                val disconnectAlertedFor = WatchHeartbeatStore.lastDisconnectAlertedFor(this@WatchdogService)
                val heartbeatTimeoutMin = AppSettings.watchHeartbeatTimeoutMinutes(this@WatchdogService)
                val heartbeatTimeoutMs = heartbeatTimeoutMin * 60_000L
                if (WatchConnectionPolicy.shouldAlert(now, lastHeartbeat, heartbeatTimeoutMs, disconnectAlertedFor)) {
                    WatchHeartbeatStore.markDisconnectAlertedFor(this@WatchdogService, lastHeartbeat)
                    AlertDispatcher(this@WatchdogService).dispatch(
                        AlertEvent(
                            AlertType.WATCH_DISCONNECTED,
                            now,
                            null,
                            "Saat bağlantısı kesildi; $heartbeatTimeoutMin dakikadır heartbeat alınamıyor"
                        ),
                        HistoryStore.formatted(this@WatchdogService, 4).lines().filter { it.isNotBlank() }
                    )
                }

                val phoneBattery = getSystemService(BatteryManager::class.java)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                BatteryAlertHelper.update(this@WatchdogService, "phone", "Telefon", phoneBattery)
                delay(60_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
