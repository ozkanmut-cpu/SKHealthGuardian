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
        val history = if (recent.isEmpty()) "" else recent.takeLast(4).joinToString("\n", prefix = "\nSon ölçümler:\n")
        val text = "KRİTİK SAĞLIK UYARISI\n${alert.message}\nSaat: $time$history"

        AlarmTimelineStore.add(
            context,
            "ALARM",
            "${alert.message}; SpO₂=${current?.spo2 ?: "—"}; HR=${current?.heartRate ?: "—"}",
            alert.timestampMs
        )

        val smsTargets = contacts.filter { it.smsEnabled }
        var smsAttempted = 0
        var smsQueuedCount = 0
        for (contact in smsTargets) {
            if (AlertAcknowledgementStore.isAcknowledged(context, alertId)) {
                AlarmTimelineStore.add(context, "SMS İPTAL", "Alarm susturuldu; kalan ilk SMS gönderimleri durduruldu", alert.timestampMs)
                break
            }
            smsAttempted++
            val ok = sms.send(contact.phoneNumber, text, alert.timestampMs, alertId)
            if (ok) smsQueuedCount++
            DeliveryLogStore.add(
                context,
                "SMS",
                mask(contact.phoneNumber),
                ok,
                if (ok) "modem gönderim kuyruğuna alındı; sonuç bekleniyor" else "kuyruğa alınamadı / izin yok"
            )
            AlarmTimelineStore.add(context, "SMS", "${mask(contact.phoneNumber)} ${if (ok) "kuyruğa alındı" else "başlatılamadı"}")
        }
        val smsQueued = smsAttempted > 0 && smsQueuedCount == smsAttempted

        var callTargetNumber: String? = null
        for (candidate in contacts.filter { it.callEnabled }) {
            if (AlertAcknowledgementStore.isAcknowledged(context, alertId)) {
                AlarmTimelineStore.add(context, "ARAMA İPTAL", "Alarm susturuldu; kalan ilk aramalar durduruldu", alert.timestampMs)
                break
            }
            val ok = caller.call(candidate.phoneNumber, alert.timestampMs, alertId)
            DeliveryLogStore.add(
                context,
                "ARAMA",
                mask(candidate.phoneNumber),
                ok,
                if (ok) "arama başlatıldı" else "arama başlatılamadı; sıradaki kişi denenecek"
            )
            AlarmTimelineStore.add(context, "ARAMA", "${mask(candidate.phoneNumber)} ${if (ok) "başlatıldı" else "başlatılamadı"}")
            if (ok) {
                callTargetNumber = candidate.phoneNumber
                break
            }
        }
        val callOk = callTargetNumber != null
        val acknowledgedDuringDispatch = AlertAcknowledgementStore.isAcknowledged(context, alertId)

        val remoteStatus = buildString {
            if (acknowledgedDuringDispatch) {
                append("Alarm susturuldu; kalan uzak bildirimler durduruldu")
            } else {
                append(
                    when {
                        smsTargets.isEmpty() -> "SMS kişisi yok"
                        smsAttempted == 0 -> "SMS gönderimi başlatılmadı"
                        smsQueued -> "SMS kuyruğa alındı; gönderim sonucu loglanacak"
                        else -> "Bazı SMS'ler kuyruğa alınamadı"
                    }
                )
                append(" • ")
                val callEnabled = contacts.any { it.callEnabled }
                append(
                    when {
                        !callEnabled -> "Arama kişisi yok"
                        callOk -> "Arama başlatıldı ${callTargetNumber?.let { mask(it) }.orEmpty()}"
                        else -> "Hiçbir arama kişisi başlatılamadı"
                    }
                )
            }
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

        if (!acknowledgedDuringDispatch) {
            localNotification(alarmIntent, alert)
            if (!AlertAcknowledgementStore.isAcknowledged(context, alertId)) {
                scheduleEscalation(alertId, alert)
            }
            if (!AlertAcknowledgementStore.isAcknowledged(context, alertId)) {
                runCatching { context.startActivity(alarmIntent) }
            }
        }
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
