package com.skhealth.guardian.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class DosefolkBridgeSelfTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SELF_TEST) return
        if (Build.VERSION.SDK_INT >= 34) {
            val sender = sentFromPackage
            if (sender != null && sender != DOSEFOLK_PACKAGE) return
        }

        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val sentAtMs = intent.getLongExtra(EXTRA_SENT_AT_MS, 0L)
        if (token.isBlank() || sentAtMs <= 0L) return

        DosefolkBridgeStatusStore.markSelfTestReceived(context, token, sentAtMs)
        context.sendBroadcast(
            Intent(ACTION_SELF_TEST_ACK)
                .setPackage(DOSEFOLK_PACKAGE)
                .putExtra(EXTRA_TOKEN, token)
                .putExtra(EXTRA_SENT_AT_MS, sentAtMs)
        )
    }

    companion object {
        const val ACTION_SELF_TEST = "com.dosefolk.action.ORKO_BRIDGE_SELF_TEST"
        const val ACTION_SELF_TEST_ACK = "com.dosefolk.action.ORKO_BRIDGE_SELF_TEST_ACK"
        const val DOSEFOLK_PACKAGE = "com.ozkanmut.ilactakip"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_SENT_AT_MS = "sentAtMs"
    }
}
