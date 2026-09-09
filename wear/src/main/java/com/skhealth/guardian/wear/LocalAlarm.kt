package com.skhealth.guardian.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.AlertEvent

object LocalAlarm {
    private const val NOTIFICATION_ID = 99
    private const val CHANNEL_ID = "critical"

    fun raise(context: Context, alert: AlertEvent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Kritik sağlık alarmları", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Doğrulanmış SpO₂ ve nabız alarmları"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 300, 700, 300, 1200)
        })
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠ SAĞLIK ALARMI")
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${alert.message}\n\nÖlçümü kontrol et. Telefona da iletilmeye çalışılıyor."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .build())
        (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300, 700, 300, 1200), 1))
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
    }
}
