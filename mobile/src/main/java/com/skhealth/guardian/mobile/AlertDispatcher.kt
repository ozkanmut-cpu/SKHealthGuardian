package com.skhealth.guardian.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.AlertIdentity
import com.skhealth.guardian.shared.HealthReading
import com.skhealth.guardian.shared.SequentialFailover
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertDispatcher(private val context: Context) {
    private val sms = SmsSender(context)
    private val caller = CallPlacer(context)

    fun dispatch(alert: AlertEvent, recent: List<String>, reading: HealthReading? = null) {
        if (!AlertDeduplicator.shouldDispatch(context, alert)) return

        val alertId = AlertIdentity.of(alert)
        val current = reading ?: alert.reading
        val contacts = ContactStore.contacts(context)
        val time = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date(alert.timestampMs))
        val history = if (recent.isEmpty()) "" else recent.takeLast(4).joinToString("\n", prefix="\nSon ölçümler:\n")
        val text = "KRİTİK SAĞLIK UYARISI\n${alert.message}\nSaat: $time$history"

        AlarmTimelineStore.add(context, "ALARM", "${alert.message}; SpO₂=${current?.spo2 ?: "—"}; HR=${current?.heartRate ?: "—"}", alert.timestampMs)

        val smsTargets = contacts.filter { it.smsEnabled }
        val smsResults = smsTargets.map { contact ->
            val ok = sms.send(contact.phoneNumber, text, alert.timestampMs, alertId)
            DeliveryLogStore.add(context, "SMS", mask(contact.phoneNumber), ok, if (ok) "modem gönderim kuyruğuna alındı; sonuç bekleniyor" else "kuyruğa alınamadı / izin yok")
            AlarmTimelineStore.add(context, "SMS", "${mask(contact.phoneNumber)} ${if (ok) "kuyruğa alındı" else "başlatılamadı"}")
            ok
        }
        val smsQueued = smsResults.isNotEmpty() && smsResults.all { it }

        val callTarget = SequentialFailover.firstSuccessful(contacts.filter { it.callEnabled }) { candidate ->
            val ok = caller.call(candidate.phoneNumber)
            DeliveryLogStore.add(context, "ARAMA", mask(candidate.phoneNumber), ok, if (ok) "arama başlatıldı" else "arama başlatılamadı; sıradaki kişi denenecek")
            AlarmTimelineStore.add(context, "ARAMA", "${mask(candidate.phoneNumber)} ${if (ok) "başlatıldı" else "başlatılamadı"}")
            ok
        }
        val callOk = callTarget != null

        val remoteStatus = buildString {
            append(if (smsTargets.isEmpty()) "SMS kişisi yok" else if (smsQueued) "SMS kuyruğa alındı; gönderim sonucu loglanacak" else "SMS kuyruğa alınamadı")
            append(" • ")
            val callEnabled = contacts.any { it.callEnabled }
            append(if (!callEnabled) "Arama kişisi yok" else if (callOk) "Arama başlatıldı ${callTarget?.let { mask(it.phoneNumber) }.orEmpty()}" else "Hiçbir arama kişisi başlatılamadı")
        }

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            action = "com.skhealth.guardian.mobile.SHOW_ALARM"
            data = Uri.parse("skhealth://alarm/id/${Uri.encode(alertId)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmActivity.EXTRA_REASON, alert.message)
            putExtra(AlarmActivity.EXTRA_SPO2, current?.spo2 ?: -1)
            putExtra(AlarmActivity.EXTRA_HR, current?.heartRate ?: -1)
            putExtra(AlarmActivity.EXTRA_REMOTE_STATUS, remoteStatus)
            putExtra(AlarmActivity.EXTRA_ALERT_ID, alertId)
            putExtra(AlarmActivity.EXTRA_ALERT_TS, alert.timestampMs)
        }

        localNotification(alarmIntent, alert)
        scheduleEscalation(alertId, alert)
        runCatching { context.startActivity(alarmIntent) }
    }

    private fun scheduleEscalation(alertId: String, alert: AlertEvent) {
        val minutes = AppSettings.escalationMinutes(context)
        if (minutes <= 0) return
        val dueAt = System.currentTimeMillis() + minutes * 60_000L
        EscalationScheduler.schedule(context, alertId, alert.timestampMs, alert.message, dueAt)
        AlarmTimelineStore.add(context, "ESCALATION PLANLANDI", "$minutes dk içinde alarm susturulmazsa tekrar iletişim kurulacak")
    }

    private fun localNotification(alarmIntent: Intent, alert: AlertEvent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("critical", "Critical health alerts", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
