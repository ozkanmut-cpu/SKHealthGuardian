package com.skhealth.guardian.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.AlertEvent

object LocalAlarm {
    fun raise(context: Context, alert: AlertEvent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("critical", "Critical health alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(99, NotificationCompat.Builder(context, "critical")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("KRİTİK SAĞLIK UYARISI")
            .setContentText(alert.message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).build())
        (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            .vibrate(VibrationEffect.createWaveform(longArrayOf(0,700,300,700,300,1200), 1))
    }
}
