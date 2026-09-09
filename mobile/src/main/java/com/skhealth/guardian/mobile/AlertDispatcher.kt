package com.skhealth.guardian.mobile

import android.app.AlarmManager
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

        AlarmTimelineStore.add(context, "ALARM", "${alert.message}; SpO₂=${current?.spo2 ?: "—"}; HR=${current?.heartRate ?: "—"}", alert.timestampMs)

        val smsTargets = contacts.filter { it.smsEnabled }
        val smsResults = smsTargets.map { contact ->
            val ok = sms.send(contact.phoneNumber, text)
            DeliveryLogStore.add(context, "SMS", mask(contact.phoneNumber), ok, if (ok) "modem gönderim kuyruğuna alındı; sonuç bekleniyor" else "kuyruğa alınamadı / izin yok")
            AlarmTimelineStore.add(context, "SMS", "${mask(contact.phoneNumber)} ${if (ok) "kuyruğa alındı" else "başlatılamadı"}")
            ok
        }
        val smsQueued = smsResults.isNotEmpty() && smsResults.all { it }

        var callOk = false
        var callTarget: EmergencyContact? = null
        for (candidate in contacts.filter { it.callEnabled }) {
            val ok = caller.call(candidate.phoneNumber)
            DeliveryLogStore.add(context, "ARAMA", mask(candidate.phoneNumber), ok, if (ok) "arama başlatıldı" else "arama başlatılamadı; sıradaki kişi denenecek")
            AlarmTimelineStore.add(context, "ARAMA", "${mask(candidate.phoneNumber)} ${if (ok) "başlatıldı" else "başlatılamadı"}")
            if (ok) { callOk = true; callTarget = candidate; break }
        }

        val remoteStatus = buildString {
            append(if (smsTargets.isEmpty()) "SMS kişisi yok" else if (smsQueued) "SMS kuyruğa alındı; gönderim sonucu loglanacak" else "SMS kuyruğa alınamadı")
            append(" • ")
            val callEnabled = contacts.any { it.callEnabled }
            append(if (!callEnabled) "Arama kişisi yok" else if (callOk) "Arama başlatıldı ${callTarget?.let { mask(it.phoneNumber) }.orEmpty()}" else "Hiçbir arama kişisi başlatılamadı")
        }

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmActivity.EXTRA_REASON, alert.message)
            putExtra(AlarmActivity.EXTRA_SPO2, current?.spo2 ?: -1)
            putExtra(AlarmActivity.EXTRA_HR, current?.heartRate ?: -1)
            putExtra(AlarmActivity.EXTRA_REMOTE_STATUS, remoteStatus)
            putExtra(AlarmActivity.EXTRA_ALERT_TS, alert.timestampMs)
        }

        localNotification(alert, alarmIntent)
        scheduleEscalation(alert)
        runCatching { context.startActivity(alarmIntent) }
    }

    private fun scheduleEscalation(alert: AlertEvent) {
        val minutes = AppSettings.escalationMinutes(context)
        if (minutes <= 0) return
        val intent = Intent(context, AlarmEscalationReceiver::class.java).apply {
            putExtra(AlarmEscalationReceiver.EXTRA_ALERT_TS, alert.timestampMs)
            putExtra(AlarmEscalationReceiver.EXTRA_REASON, alert.message)
        }
        val pi = PendingIntent.getBroadcast(context, alert.timestampMs.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + minutes * 60_000L,
            pi
        )
        AlarmTimelineStore.add(context, "ESCALATION PLANLANDI", "$minutes dk içinde alarm susturulmazsa tekrar iletişim kurulacak")
    }

    private fun localNotification(alert: AlertEvent, alarmIntent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("critical", "Critical health alerts", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(context, alert.timestampMs.toInt(), alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            AlarmActivity.CRITICAL_NOTIFICATION_ID,
            NotificationCompat.Builder(context, "critical")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("KRİTİK SAĞLIK UYARISI")
                .setContentText(alert.message)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .build()
        )
    }

    private fun mask(number: String): String {
        val clean = number.trim()
        return if (clean.length <= 4) "****" else "***${clean.takeLast(4)}"
    }
}
