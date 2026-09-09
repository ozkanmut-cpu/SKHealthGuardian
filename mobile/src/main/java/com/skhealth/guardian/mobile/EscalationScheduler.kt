package com.skhealth.guardian.mobile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.skhealth.guardian.shared.AlertIdentity

object EscalationScheduler {
    private const val PREF = "pending_escalations"
    private const val DUE_PREFIX = "due_"
    private const val REASON_PREFIX = "reason_"
    private const val DUE2_PREFIX = "due2_"
    private const val REASON2_PREFIX = "reason2_"
    private const val TS2_PREFIX = "ts2_"

    /** Current exact-ID scheduling API. */
    @Synchronized
    fun schedule(context: Context, alertId: String, alertTs: Long, reason: String, dueAtMs: Long) {
        if (!AlertIdentity.isValid(alertId) || alertTs <= 0L || dueAtMs <= 0L) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(DUE2_PREFIX + alertId, dueAtMs)
            .putString(REASON2_PREFIX + alertId, reason)
            .putLong(TS2_PREFIX + alertId, alertTs)
            .commit()
        arm(context, alertId, alertTs, reason, dueAtMs)
    }

    @Synchronized
    fun cancel(context: Context, alertId: String, alertTs: Long) {
        if (!AlertIdentity.isValid(alertId)) return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, alertId, alertTs, ""))
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(DUE2_PREFIX + alertId)
            .remove(REASON2_PREFIX + alertId)
            .remove(TS2_PREFIX + alertId)
            .commit()
    }

    fun markConsumed(context: Context, alertId: String, alertTs: Long) = cancel(context, alertId, alertTs)

    /** Legacy timestamp API retained for already-scheduled alarms during migration. */
    @Synchronized
    fun schedule(context: Context, alertTs: Long, reason: String, dueAtMs: Long) {
        if (alertTs <= 0L || dueAtMs <= 0L) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(DUE_PREFIX + alertTs, dueAtMs)
            .putString(REASON_PREFIX + alertTs, reason)
            .commit()
        armLegacy(context, alertTs, reason, dueAtMs)
    }

    @Synchronized
    fun cancel(context: Context, alertTs: Long) {
        if (alertTs <= 0L) return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntentLegacy(context, alertTs, ""))
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().remove(DUE_PREFIX + alertTs).remove(REASON_PREFIX + alertTs).commit()
    }

    fun markConsumed(context: Context, alertTs: Long) = cancel(context, alertTs)

    @Synchronized
    fun restorePending(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        var restored = 0

        val exactEntries = prefs.all.filterKeys { it.startsWith(DUE2_PREFIX) }
        exactEntries.forEach { (key, value) ->
            val alertId = key.removePrefix(DUE2_PREFIX)
            val dueAt = value as? Long ?: return@forEach
            val alertTs = prefs.getLong(TS2_PREFIX + alertId, 0L)
            if (!AlertIdentity.isValid(alertId) || alertTs <= 0L) {
                cancel(context, alertId, alertTs)
                return@forEach
            }
            if (AlertAcknowledgementStore.isAcknowledged(context, alertId)) {
                cancel(context, alertId, alertTs)
                return@forEach
            }
            val reason = prefs.getString(REASON2_PREFIX + alertId, null) ?: "Sağlık alarmı"
            arm(context, alertId, alertTs, reason, maxOf(nowMs + 1_000L, dueAt))
            restored++
        }

        val legacyAck = AlertAcknowledgementStore.lastAcknowledgedAt(context)
        val legacyEntries = prefs.all.filterKeys { it.startsWith(DUE_PREFIX) && !it.startsWith(DUE2_PREFIX) }
        legacyEntries.forEach { (key, value) ->
            val alertTs = key.removePrefix(DUE_PREFIX).toLongOrNull() ?: return@forEach
            val dueAt = value as? Long ?: return@forEach
            if (alertTs <= legacyAck) {
                cancel(context, alertTs)
                return@forEach
            }
            val reason = prefs.getString(REASON_PREFIX + alertTs, null) ?: "Sağlık alarmı"
            armLegacy(context, alertTs, reason, maxOf(nowMs + 1_000L, dueAt))
            restored++
        }
        return restored
    }

    private fun arm(context: Context, alertId: String, alertTs: Long, reason: String, dueAtMs: Long) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            dueAtMs,
            pendingIntent(context, alertId, alertTs, reason)
        )
    }

    private fun pendingIntent(context: Context, alertId: String, alertTs: Long, reason: String): PendingIntent {
        val intent = Intent(context, AlarmEscalationReceiver::class.java).apply {
            action = "com.skhealth.guardian.mobile.ESCALATE"
            data = Uri.parse("skhealth://escalation/id/${Uri.encode(alertId)}")
            putExtra(AlarmEscalationReceiver.EXTRA_ALERT_ID, alertId)
            putExtra(AlarmEscalationReceiver.EXTRA_ALERT_TS, alertTs)
            if (reason.isNotBlank()) putExtra(AlarmEscalationReceiver.EXTRA_REASON, reason)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun armLegacy(context: Context, alertTs: Long, reason: String, dueAtMs: Long) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            dueAtMs,
            pendingIntentLegacy(context, alertTs, reason)
        )
    }

    private fun pendingIntentLegacy(context: Context, alertTs: Long, reason: String): PendingIntent {
        val intent = Intent(context, AlarmEscalationReceiver::class.java).apply {
            action = "com.skhealth.guardian.mobile.ESCALATE"
            data = Uri.parse("skhealth://escalation/$alertTs")
            putExtra(AlarmEscalationReceiver.EXTRA_ALERT_TS, alertTs)
            if (reason.isNotBlank()) putExtra(AlarmEscalationReceiver.EXTRA_REASON, reason)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
