package com.skhealth.guardian.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.skhealth.guardian.shared.AlertIdentity
import com.skhealth.guardian.shared.RemoteDeliveryGate

class SmsRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(SmsStatusReceiver.EXTRA_NUMBER) ?: return
        val message = intent.getStringExtra(SmsStatusReceiver.EXTRA_MESSAGE) ?: return
        val messageId = intent.getStringExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID) ?: return
        val attempt = intent.getIntExtra(SmsStatusReceiver.EXTRA_ATTEMPT, 1)
        val target = intent.getStringExtra(SmsStatusReceiver.EXTRA_TARGET) ?: "****"
        val alertTs = intent.getLongExtra(SmsStatusReceiver.EXTRA_ALERT_TS, 0L)
        val alertId = intent.getStringExtra(SmsStatusReceiver.EXTRA_ALERT_ID)
        val exact = AlertIdentity.isValid(alertId)

        val shouldDeliver = if (exact) {
            !AlertAcknowledgementStore.isAcknowledged(context, alertId!!)
        } else {
            RemoteDeliveryGate.shouldDeliver(AlertAcknowledgementStore.lastAcknowledgedAt(context), alertTs)
        }
        if (!shouldDeliver) {
            DeliveryLogStore.add(context, "SMS RETRY", target, false, "alarm susturuldu; yeniden gönderim iptal edildi")
            AlarmTimelineStore.add(context, "SMS RETRY İPTAL", "$target • alarm susturulduğu için yeniden gönderim yapılmadı", alertTs)
            return
        }

        val ok = SmsSender(context).send(number, message, messageId, attempt, alertTs, alertId)
        DeliveryLogStore.add(
            context,
            "SMS RETRY",
            target,
            ok,
            if (ok) "yeniden gönderim modem kuyruğuna alındı; deneme=${attempt + 1}" else "yeniden gönderim başlatılamadı; deneme=${attempt + 1}"
        )
        AlarmTimelineStore.add(context, "SMS RETRY", "$target • deneme=${attempt + 1} • ${if (ok) "başlatıldı" else "başarısız"}")
    }
}
