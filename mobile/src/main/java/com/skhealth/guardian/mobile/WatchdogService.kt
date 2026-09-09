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
import kotlinx.coroutines.*

class WatchdogService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var alertedForTs = -1L

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("watchdog", "Health watchdog", NotificationManager.IMPORTANCE_LOW))
        startForeground(21, NotificationCompat.Builder(this, "watchdog").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SK Health Guardian aktif").setContentText("Saat verisi izleniyor").setOngoing(true).build())
        scope.launch {
            while (isActive) {
                val last = MonitoringState.lastReading(this@WatchdogService)
                val stale = AppSettings.load(this@WatchdogService).staleDataMs
                if (last > 0 && System.currentTimeMillis() - last > stale && alertedForTs != last) {
                    alertedForTs = last
                    AlertDispatcher(this@WatchdogService).dispatch(
                        AlertEvent(AlertType.DATA_STALE, System.currentTimeMillis(), null, "Saatten ${stale / 60_000} dakikadır veri gelmiyor"),
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
