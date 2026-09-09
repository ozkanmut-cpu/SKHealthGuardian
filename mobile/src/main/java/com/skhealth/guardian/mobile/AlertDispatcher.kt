package com.skhealth.guardian.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.AlertEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertDispatcher(private val context: Context) {
    private val sms = SmsSender(context)
    private val caller = CallPlacer(context)

    fun dispatch(alert: AlertEvent, recent: List<String>) {
        localNotification(alert)
        val time = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date(alert.timestampMs))
        val history = if (recent.isEmpty()) "" else recent.takeLast(4).joinToString("\n", prefix="\nSon ölçümler:\n")
        val text = "KRİTİK SAĞLIK UYARISI\n${alert.message}\nSaat: $time$history"
        val contacts = ContactStore.contacts(context)
        contacts.filter { it.smsEnabled }.forEach { sms.send(it.phoneNumber, text) }
        contacts.firstOrNull { it.callEnabled }?.let { caller.call(it.phoneNumber) }
    }

    private fun localNotification(alert: AlertEvent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("critical", "Critical health alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(100, NotificationCompat.Builder(context, "critical")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("KRİTİK SAĞLIK UYARISI")
            .setContentText(alert.message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).build())
    }
}
