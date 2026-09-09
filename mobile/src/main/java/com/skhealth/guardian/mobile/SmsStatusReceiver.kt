package com.skhealth.guardian.mobile

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val target = intent.getStringExtra(EXTRA_TARGET) ?: "****"
        val number = intent.getStringExtra(EXTRA_NUMBER)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)
        val part = intent.getIntExtra(EXTRA_PART, 1)
        val total = intent.getIntExtra(EXTRA_TOTAL, 1)
        val ok = resultCode == Activity.RESULT_OK
        val detail = if (ok) {
            "SMS parçası gönderildi ($part/$total), deneme=${attempt + 1}"
        } else {
            "SMS gönderilemedi ($part/$total), deneme=${attempt + 1}, sonuç=$resultCode"
        }
        DeliveryLogStore.add(context, "SMS", target, ok, detail)
        AlarmTimelineStore.add(context, "SMS SONUCU", "$target • $detail")

        if (!ok && number != null && message != null && messageId != null && attempt < MAX_RETRIES) {
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val key = "scheduled_${messageId}_$attempt"
            if (!prefs.getBoolean(key, false)) {
                prefs.edit().putBoolean(key, true).apply()
                val retryIntent = Intent(context, SmsRetryReceiver::class.java).apply {
                    putExtra(EXTRA_NUMBER, number)
                    putExtra(EXTRA_MESSAGE, message)
                    putExtra(EXTRA_MESSAGE_ID, messageId)
                    putExtra(EXTRA_ATTEMPT, attempt + 1)
                    putExtra(EXTRA_TARGET, target)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    (messageId.hashCode() * 37 + attempt),
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val delay = if (attempt == 0) 30_000L else 90_000L
                val alarm = context.getSystemService(AlarmManager::class.java)
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pi)
                DeliveryLogStore.add(context, "SMS RETRY", target, true, "${delay / 1000} sn sonra yeniden deneme planlandı")
            }
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.skhealth.guardian.mobile.SMS_SENT"
        const val EXTRA_TARGET = "target"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_PART = "part"
        const val EXTRA_TOTAL = "total"
        private const val PREF = "sms_retry_state"
        private const val MAX_RETRIES = 2
    }
}
