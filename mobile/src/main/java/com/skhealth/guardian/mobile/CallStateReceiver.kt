package com.skhealth.guardian.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val prefs = context.getSharedPreferences(CallPlacer.PREF, Context.MODE_PRIVATE)
        val startedAt = prefs.getLong(CallPlacer.KEY_STARTED_AT, 0L)
        val target = prefs.getString(CallPlacer.KEY_TARGET, "****") ?: "****"
        if (startedAt <= 0L || System.currentTimeMillis() - startedAt !in 0..5 * 60_000L) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val detail = when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> "Arama hattı aktif/çevriliyor. Android bu izin düzeyinde karşı tarafın kesin cevapladığını doğrulamaz."
            TelephonyManager.EXTRA_STATE_IDLE -> "Arama sonlandı veya hat beklemeye döndü."
            TelephonyManager.EXTRA_STATE_RINGING -> "Telefon çalıyor durumu görüldü."
            else -> "Telefon durumu: $state"
        }
        DeliveryLogStore.add(context, "ARAMA DURUMU", target, state != TelephonyManager.EXTRA_STATE_IDLE, detail)
        AlarmTimelineStore.add(context, "ARAMA DURUMU", "$target • $detail")

        if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            prefs.edit().remove(CallPlacer.KEY_STARTED_AT).remove(CallPlacer.KEY_TARGET).apply()
        }
    }
}
