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
        val bluetoothReady = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!bluetoothReady) return
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, WatchdogService::class.java))
        }
    }
}
