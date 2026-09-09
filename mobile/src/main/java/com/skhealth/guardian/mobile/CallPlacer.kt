package com.skhealth.guardian.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

class CallPlacer(private val context: Context) {
    fun call(number: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom.placeCall(Uri.fromParts("tel", number, null), Bundle().apply {
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
            })
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong(KEY_STARTED_AT, System.currentTimeMillis())
                .putString(KEY_TARGET, mask(number))
                .apply()
            true
        }.getOrDefault(false)
    }

    private fun mask(number: String): String {
        val clean = number.trim()
        return if (clean.length <= 4) "****" else "***${clean.takeLast(4)}"
    }

    companion object {
        const val PREF = "call_progress"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_TARGET = "target"
    }
}
