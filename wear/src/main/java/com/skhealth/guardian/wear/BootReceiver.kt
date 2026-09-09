package com.skhealth.guardian.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hr = granted(context, PERM_READ_HEART_RATE)
        val spo2 = granted(context, PERM_READ_OXYGEN_SATURATION)
        val background = Build.VERSION.SDK_INT < 36 || granted(context, PERM_READ_HEALTH_DATA_IN_BACKGROUND)
        if (!hr || !spo2 || !background) return
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val PERM_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        private const val PERM_READ_OXYGEN_SATURATION = "android.permission.health.READ_OXYGEN_SATURATION"
        private const val PERM_READ_HEALTH_DATA_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }
}
