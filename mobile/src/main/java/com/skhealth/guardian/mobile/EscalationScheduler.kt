package com.skhealth.guardian.mobile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

object EscalationScheduler {
    private const val PREF = "pending_escalations"
    private const val DUE_PREFIX = "due_"
    private const val REASON_PREFIX = "reason_"

    fun schedule(context: Context, alertTs: Long, reason: String, dueAtMs: Long) {
        if (alertTs <= 0L || dueAtMs <= 0L) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(DUE_PREFIX + alertTs, dueAtMs)
            .putString(REASON_PREFIX + alertTs, reason)
            .commit()
        arm(context, alertTs, reason, dueAtMs)
    }

    fun cancel(context: Context, alertTs: Long) {
        if (alertTs <= 0L) return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, alertTs, ""))
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().remove(DUE_PREFIX + alertTs).remove(REASON_PREFIX + alertTs).commit()
    }

    fun markConsumed(context: Context, alertTs: Long) = cancel(context, alertTs)

    fun restorePending(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val ack = AlertAcknowledgementStore.lastAcknowledgedAt(context)
        var restored = 0
        val dueEntries = prefs.all.filterKeys { it.startsWith(DUE_PREFIX) }
        dueEntries.forEach { (key, value) ->
            val alertTs = key.removePrefix(DUE_PREFIX).toLongOrNull() ?: return@forEach
            val dueAt = value as? Long ?: return@forEach
            if (alertTs <= ack) {
                cancel(context, alertTs)
                return@forEach
            }
            val reason = prefs.getString(REASON_PREFIX + alertTs, null) ?: "Sağlık alarmı"
            arm(context, alertTs, reason, maxOf(nowMs + 1_000L, dueAt))
            restored++
        }
        return restored
    }

    private fun arm(context: Context, alertTs: Long, reason: String, dueAtMs: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            dueAtMs,
            pendingIntent(context, alertTs, reason)
        )
    }

    private fun pendingIntent(context: Context, alertTs: Long, reason: String): PendingIntent {
        val intent = Intent(context, AlarmEscalationReceiver::class.java).apply {
            action = "com.skhealth.guardian.mobile.ESCALATE"
            data = Uri.parse("skhealth://escalation/$alertTs")
            putExtra(AlarmEscalationReceiver.EXTRA_ALERT_TS, alertTs)
            if (reason.isNotBlank()) putExtra(AlarmEscalationReceiver.EXTRA_REASON, reason)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
