package com.skhealth.guardian.mobile

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Watchdog is not a Bluetooth feature and must always be restored after reboot.
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, WatchdogService::class.java))
        }

        if (!Pc60StatusStore.monitoringEnabled(context)) return
        val bluetoothReady = Build.VERSION.SDK_INT < 31 ||
            (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
        if (!bluetoothReady) {
            AlarmTimelineStore.add(context, "PC-60FW TEKNİK", "Telefon yeniden başladı; Bluetooth izni olmadığı için PC-60FW otomatik başlatılamadı")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, Pc60BleService::class.java))
        }.onFailure {
            AlarmTimelineStore.add(context, "PC-60FW TEKNİK", "Telefon yeniden başladı; PC-60FW servisi başlatılamadı: ${it.javaClass.simpleName}")
        }
    }
}
