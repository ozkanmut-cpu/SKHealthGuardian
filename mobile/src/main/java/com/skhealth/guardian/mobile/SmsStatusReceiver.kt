package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val target = intent.getStringExtra(EXTRA_TARGET) ?: "****"
        val part = intent.getIntExtra(EXTRA_PART, 1)
        val total = intent.getIntExtra(EXTRA_TOTAL, 1)
        val ok = resultCode == Activity.RESULT_OK
        val detail = if (ok) {
            "SMS parçası gönderildi ($part/$total)"
        } else {
            "SMS gönderilemedi ($part/$total), sonuç=$resultCode"
        }
        DeliveryLogStore.add(context, "SMS", target, ok, detail)
    }

    companion object {
        const val ACTION_SMS_SENT = "com.skhealth.guardian.mobile.SMS_SENT"
        const val EXTRA_TARGET = "target"
        const val EXTRA_PART = "part"
        const val EXTRA_TOTAL = "total"
    }
}
