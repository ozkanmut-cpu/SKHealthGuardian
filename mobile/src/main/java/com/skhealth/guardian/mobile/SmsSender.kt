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
    fun send(number: String, message: String): Boolean = send(number, message, UUID.randomUUID().toString(), 0)

    fun send(number: String, message: String, messageId: String, attempt: Int): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val sms = context.getSystemService(SmsManager::class.java)
            val parts = sms.divideMessage(message)
            val masked = mask(number)
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { index ->
                val intent = Intent(context, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_SENT
                    putExtra(SmsStatusReceiver.EXTRA_TARGET, masked)
                    putExtra(SmsStatusReceiver.EXTRA_NUMBER, number)
                    putExtra(SmsStatusReceiver.EXTRA_MESSAGE, message)
                    putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                    putExtra(SmsStatusReceiver.EXTRA_ATTEMPT, attempt)
                    putExtra(SmsStatusReceiver.EXTRA_PART, index + 1)
                    putExtra(SmsStatusReceiver.EXTRA_TOTAL, parts.size)
                }
                sentIntents += PendingIntent.getBroadcast(
                    context,
                    (messageId.hashCode() * 31 + attempt * 1000 + index),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            sms.sendMultipartTextMessage(number, null, parts, sentIntents, null)
            true
        }.getOrDefault(false)
    }

    private fun mask(number: String): String {
        val clean = number.trim()
        return if (clean.length <= 4) "****" else "***${clean.takeLast(4)}"
    }
}
