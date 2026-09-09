package com.skhealth.guardian.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object BatteryAlertHelper {
    private const val PREF = "battery_alert_state"

    fun update(context: Context, key: String, label: String, batteryPct: Int) {
        if (batteryPct !in 0..100) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val stateKey = "low_$key"
        val alreadyLow = prefs.getBoolean(stateKey, false)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("battery", "Battery warnings", NotificationManager.IMPORTANCE_DEFAULT))
        val id = 700 + key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) % 200 }

        if (batteryPct <= 15 && !alreadyLow) {
            prefs.edit().putBoolean(stateKey, true).apply()
            nm.notify(
                id,
                NotificationCompat.Builder(context, "battery")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("$label pili düşük")
                    .setContentText("Pil seviyesi %$batteryPct")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
            )
            AlarmTimelineStore.add(context, "PİL UYARISI", "$label pili %$batteryPct")
        } else if (batteryPct >= 20 && alreadyLow) {
            prefs.edit().putBoolean(stateKey, false).apply()
            nm.cancel(id)
        }
    }
}
