package com.skhealth.guardian.mobile

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.UUID

class SmsSender(private val context: Context) {
    fun send(number: String, message: String, alertTs: Long = 0L): Boolean =
        send(number, message, UUID.randomUUID().toString(), 0, alertTs)

    fun send(number: String, message: String, messageId: String, attempt: Int, alertTs: Long = 0L): Boolean {
        if (alertTs > 0L && AlertAcknowledgementStore.lastAcknowledgedAt(context) >= alertTs) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val sms = context.getSystemService(SmsManager::class.java)
            val parts = sms.divideMessage(message)
            val masked = mask(number)
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            val deliveredIntents = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { index ->
                val commonRequestCode = messageId.hashCode() * 31 + attempt * 1000 + index
                val sentIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_SENT
                    putExtra(SmsStatusReceiver.EXTRA_TARGET, masked)
                    putExtra(SmsStatusReceiver.EXTRA_NUMBER, number)
                    putExtra(SmsStatusReceiver.EXTRA_MESSAGE, message)
                    putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                    putExtra(SmsStatusReceiver.EXTRA_ATTEMPT, attempt)
                    putExtra(SmsStatusReceiver.EXTRA_PART, index + 1)
                    putExtra(SmsStatusReceiver.EXTRA_TOTAL, parts.size)
                    putExtra(SmsStatusReceiver.EXTRA_ALERT_TS, alertTs)
                }
                sentIntents += PendingIntent.getBroadcast(
                    context,
                    commonRequestCode,
                    sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val deliveredIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                    putExtra(SmsStatusReceiver.EXTRA_TARGET, masked)
                    putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                    putExtra(SmsStatusReceiver.EXTRA_ATTEMPT, attempt)
                    putExtra(SmsStatusReceiver.EXTRA_PART, index + 1)
                    putExtra(SmsStatusReceiver.EXTRA_TOTAL, parts.size)
                    putExtra(SmsStatusReceiver.EXTRA_ALERT_TS, alertTs)
                }
                deliveredIntents += PendingIntent.getBroadcast(
                    context,
                    commonRequestCode xor 0x5A5A5A5A,
                    deliveredIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            sms.sendMultipartTextMessage(number, null, parts, sentIntents, deliveredIntents)
            true
        }.getOrDefault(false)
    }

    private fun mask(number: String): String {
        val clean = number.trim()
        return if (clean.length <= 4) "****" else "***${clean.takeLast(4)}"
    }
}
