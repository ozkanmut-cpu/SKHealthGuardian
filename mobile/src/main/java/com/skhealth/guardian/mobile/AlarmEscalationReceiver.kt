package com.skhealth.guardian.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.skhealth.guardian.shared.EscalationGate

class AlarmEscalationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alertTs = intent.getLongExtra(EXTRA_ALERT_TS, 0L)
        if (!EscalationGate.shouldEscalate(AlertAcknowledgementStore.lastAcknowledgedAt(context), alertTs)) {
            if (alertTs > 0L) {
                AlarmTimelineStore.add(context, "ESCALATION İPTAL", "Alarm kullanıcı tarafından susturulmuş/onaylanmış")
            }
            return
        }

        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Sağlık alarmı"
        val contacts = ContactStore.contacts(context)
        val text = "SK HEALTH GUARDIAN TEKRAR UYARI\n$reason\nAlarm henüz susturulmadı/onaylanmadı."

        contacts.filter { it.smsEnabled }.forEach { c ->
            val ok = SmsSender(context).send(c.phoneNumber, text)
            DeliveryLogStore.add(context, "SMS ESCALATION", mask(c.phoneNumber), ok, if (ok) "tekrar SMS kuyruğa alındı" else "tekrar SMS başlatılamadı")
        }

        val callTargets = contacts.filter { it.callEnabled }
        val preferred = if (callTargets.size > 1) callTargets.drop(1) + callTargets.take(1) else callTargets
        var callOk = false
        for (c in preferred) {
            callOk = CallPlacer(context).call(c.phoneNumber)
            DeliveryLogStore.add(context, "ARAMA ESCALATION", mask(c.phoneNumber), callOk, if (callOk) "tekrar arama başlatıldı" else "başlatılamadı")
            if (callOk) break
        }
        AlarmTimelineStore.add(context, "ESCALATION", "Alarm yanıtlanmadı; tekrar SMS/arama çalıştırıldı, arama=${if (callOk) "başlatıldı" else "başarısız/yok"}")
    }

    private fun mask(number: String) = if (number.length <= 4) "****" else "***${number.takeLast(4)}"

    companion object {
        const val EXTRA_ALERT_TS = "alert_ts"
        const val EXTRA_REASON = "reason"
    }
}
