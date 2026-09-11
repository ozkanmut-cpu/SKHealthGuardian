package com.skhealth.guardian.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.BloodGlucoseReading

/** Prompts for ownership without ever assuming that a meter result belongs to Orko. */
object GlucoseOwnershipNotification {
    private const val CHANNEL_ID = "glucose_ownership"
    private const val CHANNEL_NAME = "Şeker ölçümü doğrulama"
    private const val BASE_NOTIFICATION_ID = 31_000

    fun show(context: Context, reading: BloodGlucoseReading) {
        if (!GlucoseSettings.load(context).ownershipPromptEnabled) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val orkoIntent = PendingIntent.getBroadcast(
            context,
            requestCode(reading.id, 1),
            Intent(context, GlucoseOwnershipReceiver::class.java)
                .setAction(GlucoseOwnershipReceiver.ACTION_CONFIRM_ORKO)
                .putExtra(GlucoseOwnershipReceiver.EXTRA_READING_ID, reading.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val otherIntent = PendingIntent.getBroadcast(
            context,
            requestCode(reading.id, 2),
            Intent(context, GlucoseOwnershipReceiver::class.java)
                .setAction(GlucoseOwnershipReceiver.ACTION_CONFIRM_OTHER)
                .putExtra(GlucoseOwnershipReceiver.EXTRA_READING_ID, reading.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contextText = when (reading.checkpointHint) {
            com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.MORNING_FASTING -> " • Sabah açlık"
            com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL -> " • Kahvaltı +2 saat"
            com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.MIDDAY -> " • Öğlen"
            com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL -> " • Akşam +2 saat"
            com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.BEDTIME -> " • Yatmadan önce"
            null -> ""
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Yeni şeker ölçümü: ${reading.valueMgDl} mg/dL")
            .setContentText("Bu ölçüm Orko'nun mu?$contextText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(0, "Orko", orkoIntent)
            .addAction(0, "Başka kişi", otherIntent)
            .build()

        manager.notify(notificationId(reading.id), notification)
    }

    fun dismiss(context: Context, readingId: String) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(notificationId(readingId))
    }

    private fun notificationId(readingId: String): Int =
        BASE_NOTIFICATION_ID + (readingId.hashCode() and 0x0FFF)

    private fun requestCode(readingId: String, suffix: Int): Int =
        ((readingId.hashCode() and 0x7FFF) * 10 + suffix) and 0x7FFFFFFF
}
