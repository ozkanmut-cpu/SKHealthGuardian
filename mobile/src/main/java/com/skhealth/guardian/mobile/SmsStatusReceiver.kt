package com.skhealth.guardian.mobile

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.skhealth.guardian.shared.SmsRetryPolicy

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val target = intent.getStringExtra(EXTRA_TARGET) ?: "****"
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)
        val part = intent.getIntExtra(EXTRA_PART, 1)
        val total = intent.getIntExtra(EXTRA_TOTAL, 1)
        val ok = resultCode == Activity.RESULT_OK

        if (intent.action == ACTION_SMS_DELIVERED) {
            val detail = if (ok) {
                "Operatör teslim raporu alındı ($part/$total), deneme=${attempt + 1}"
            } else {
                "Operatör teslim raporu başarısız ($part/$total), deneme=${attempt + 1}, sonuç=$resultCode"
            }
            DeliveryLogStore.add(context, "SMS TESLİM", target, ok, detail)
            AlarmTimelineStore.add(context, "SMS TESLİM", "$target • $detail")
            return
        }

        val number = intent.getStringExtra(EXTRA_NUMBER)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
        val detail = if (ok) {
            "SMS parçası gönderildi ($part/$total), deneme=${attempt + 1}; operatör teslim raporu bekleniyor"
        } else {
            "SMS gönderilemedi ($part/$total), deneme=${attempt + 1}, sonuç=$resultCode"
        }
        DeliveryLogStore.add(context, "SMS", target, ok, detail)
        AlarmTimelineStore.add(context, "SMS SONUCU", "$target • $detail")

        val delay = SmsRetryPolicy.nextDelayMs(attempt)
        if (!ok && number != null && message != null && messageId != null && delay != null &&
            SmsRetryState.claimSchedule(context, messageId, attempt)
        ) {
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
            val alarm = context.getSystemService(AlarmManager::class.java)
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pi)
            DeliveryLogStore.add(context, "SMS RETRY", target, true, "${delay / 1000} sn sonra yeniden deneme planlandı")
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.skhealth.guardian.mobile.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.skhealth.guardian.mobile.SMS_DELIVERED"
        const val EXTRA_TARGET = "target"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_PART = "part"
        const val EXTRA_TOTAL = "total"
    }
}
