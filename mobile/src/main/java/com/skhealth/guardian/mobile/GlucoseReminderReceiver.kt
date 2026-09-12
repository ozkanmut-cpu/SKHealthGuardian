package com.skhealth.guardian.mobile

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.skhealth.guardian.shared.GlucoseScheduleEngine

class GlucoseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = GlucoseSettings.load(context)
        if (!settings.remindersEnabled) return

        val checkpointName = intent.getStringExtra(EXTRA_CHECKPOINT) ?: return
        val checkpoint = runCatching { GlucoseScheduleEngine.Checkpoint.valueOf(checkpointName) }.getOrNull() ?: return
        val requestedOverdue = intent.getBooleanExtra(EXTRA_OVERDUE, false)
        if (requestedOverdue && !settings.overdueEnabled) return

        val status = GlucoseDailyPlan.statuses(context).firstOrNull { it.checkpoint == checkpoint } ?: return
        if (status.state == GlucoseDailyPlan.State.COMPLETED || status.state == GlucoseDailyPlan.State.UPCOMING) return
        if (requestedOverdue && status.state != GlucoseDailyPlan.State.OVERDUE) return
        if (!requestedOverdue && status.state != GlucoseDailyPlan.State.DUE) return

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Orko Takip • Şeker ölçümü hatırlatmaları", NotificationManager.IMPORTANCE_HIGH)
        )
        val openIntent = PendingIntent.getActivity(
            context,
            5000 + checkpoint.ordinal,
            Intent(context, GlucoseHistoryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            NOTIFICATION_BASE + checkpoint.ordinal,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Orko Takip")
                .setContentText("${if (requestedOverdue) "Şeker ölçümü gecikti" else "Şeker ölçüm zamanı"} • ${checkpointLabel(checkpoint)} ölçümü ${if (requestedOverdue) "henüz tamamlanmadı" else "şimdi yapılabilir"}.")
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    companion object {
        private const val CHANNEL_ID = "glucose_reminders"
        private const val NOTIFICATION_BASE = 2600
        private const val EXTRA_CHECKPOINT = "checkpoint"
        private const val EXTRA_OVERDUE = "overdue"

        fun reschedule(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val settings = GlucoseSettings.load(context)
            val statuses = GlucoseDailyPlan.statuses(context)

            GlucoseScheduleEngine.Checkpoint.entries.forEach { checkpoint ->
                cancel(context, alarmManager, checkpoint, overdue = false)
                cancel(context, alarmManager, checkpoint, overdue = true)
            }
            if (!settings.remindersEnabled) return

            statuses.filter { it.state != GlucoseDailyPlan.State.COMPLETED }.forEach { status ->
                schedule(
                    context,
                    alarmManager,
                    status.checkpoint,
                    triggerAtMs = maxOf(System.currentTimeMillis() + 1_000L, status.windowStartMs),
                    overdue = false
                )
                if (settings.overdueEnabled) {
                    schedule(
                        context,
                        alarmManager,
                        status.checkpoint,
                        triggerAtMs = maxOf(
                            System.currentTimeMillis() + 2_000L,
                            status.windowEndMs + settings.overdueGraceMinutes * 60_000L
                        ),
                        overdue = true
                    )
                }
            }
        }

        private fun schedule(
            context: Context,
            alarmManager: AlarmManager,
            checkpoint: GlucoseScheduleEngine.Checkpoint,
            triggerAtMs: Long,
            overdue: Boolean
        ) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent(context, checkpoint, overdue)
            )
        }

        private fun cancel(
            context: Context,
            alarmManager: AlarmManager,
            checkpoint: GlucoseScheduleEngine.Checkpoint,
            overdue: Boolean
        ) {
            alarmManager.cancel(pendingIntent(context, checkpoint, overdue))
        }

        private fun pendingIntent(
            context: Context,
            checkpoint: GlucoseScheduleEngine.Checkpoint,
            overdue: Boolean
        ): PendingIntent {
            val requestCode = 6000 + checkpoint.ordinal * 2 + if (overdue) 1 else 0
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, GlucoseReminderReceiver::class.java)
                    .putExtra(EXTRA_CHECKPOINT, checkpoint.name)
                    .putExtra(EXTRA_OVERDUE, overdue),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun checkpointLabel(checkpoint: GlucoseScheduleEngine.Checkpoint): String = when (checkpoint) {
            GlucoseScheduleEngine.Checkpoint.MORNING_FASTING -> "Sabah açlık"
            GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL -> "Kahvaltı +2 saat"
            GlucoseScheduleEngine.Checkpoint.MIDDAY -> "Öğlen"
            GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL -> "Akşam +2 saat"
            GlucoseScheduleEngine.Checkpoint.BEDTIME -> "Yatmadan önce"
        }
    }
}
