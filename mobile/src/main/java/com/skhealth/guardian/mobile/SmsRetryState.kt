package com.skhealth.guardian.mobile

import android.content.Context

/**
 * Persistent, process-atomic retry claim store.
 * Multipart callbacks for the same message/attempt race here; only one callback may
 * schedule the retry alarm. Claims survive receiver/service recreation.
 */
object SmsRetryState {
    private const val PREF = "sms_retry_state"

    fun claimSchedule(context: Context, messageId: String, attempt: Int): Boolean = synchronized(this) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val key = key(messageId, attempt)
        if (prefs.getBoolean(key, false)) return@synchronized false
        prefs.edit().putBoolean(key, true).commit()
    }

    fun isClaimed(context: Context, messageId: String, attempt: Int): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(key(messageId, attempt), false)

    internal fun clearClaim(context: Context, messageId: String, attempt: Int) = synchronized(this) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(key(messageId, attempt)).commit()
    }

    private fun key(messageId: String, attempt: Int) = "scheduled_${messageId}_$attempt"
}
