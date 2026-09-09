package com.skhealth.guardian.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.HealthReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertDispatcher(private val context: Context) {
    private val sms = SmsSender(context)
    private val caller = CallPlacer(context)

    fun dispatch(alert: AlertEvent, recent: List<String>, reading: HealthReading? = null) {
        if (!AlertDeduplicator.shouldDispatch(context, alert)) return

        val current = reading ?: alert.reading
        val contacts = ContactStore.contacts(context)
        val time = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date(alert.timestampMs))
        val history = if (recent.isEmpty()) "" else recent.takeLast(4).joinToString("\n", prefix="\nSon ölçümler:\n")
        val text = "KRİTİK SAĞLIK UYARISI\n${alert.message}\nSaat: $time$history"

        val smsTargets = contacts.filter { it.smsEnabled }
        val smsOk = smsTargets.map { sms.send(it.phoneNumber, text) }.all { it }
        val callTarget = contacts.firstOrNull { it.callEnabled }
        val callOk = callTarget?.let { caller.call(it.phoneNumber) } ?: false
        val remoteStatus = buildString {
            append(if (smsTargets.isEmpty()) "SMS kişisi yok" else if (smsOk) "SMS gönderildi" else "SMS gönderilemedi")
            append(" • ")
            append(if (callTarget == null) "Arama kişisi yok" else if (callOk) "Arama başlatıldı" else "Arama başlatılamadı")
        }

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmActivity.EXTRA_REASON, alert.message)
            putExtra(AlarmActivity.EXTRA_SPO2, current?.spo2 ?: -1)
            putExtra(AlarmActivity.EXTRA_HR, current?.heartRate ?: -1)
            putExtra(AlarmActivity.EXTRA_REMOTE_STATUS, remoteStatus)
        }
        context.startActivity(alarmIntent)
        localNotification(alert, alarmIntent)
    }

    private fun localNotification(alert: AlertEvent, alarmIntent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("critical", "Critical health alerts", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(context, alert.timestampMs.toInt(), alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(100, NotificationCompat.Builder(context, "critical")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("KRİTİK SAĞLIK UYARISI")
            .setContentText(alert.message)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).build())
    }
}
