package com.skhealth.guardian.wear

import android.content.Context
import com.skhealth.guardian.shared.AlarmConfig

object WearSettings {
    private const val PREF = "wear_guardian_settings"

    fun load(context: Context): AlarmConfig {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return AlarmConfig(
            spo2CriticalImmediate = p.getInt("spo2_critical", 80),
            spo2LowThreshold = p.getInt("spo2_low", 90),
            spo2ConfirmCount = p.getInt("spo2_confirm", 2),
            heartRateHighThreshold = p.getInt("hr_high", 130),
            heartRateHighConfirmCount = p.getInt("hr_high_confirm", 2),
            heartRateLowEnabled = p.getBoolean("hr_low_enabled", false),
            heartRateLowThreshold = p.getInt("hr_low", 45),
            heartRateLowConfirmCount = p.getInt("hr_low_confirm", 2),
            staleDataMs = p.getLong("stale_ms", 10 * 60_000L)
        )
    }

    fun save(context: Context, config: AlarmConfig) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("spo2_critical", config.spo2CriticalImmediate)
            .putInt("spo2_low", config.spo2LowThreshold)
            .putInt("spo2_confirm", config.spo2ConfirmCount)
            .putInt("hr_high", config.heartRateHighThreshold)
            .putInt("hr_high_confirm", config.heartRateHighConfirmCount)
            .putBoolean("hr_low_enabled", config.heartRateLowEnabled)
            .putInt("hr_low", config.heartRateLowThreshold)
            .putInt("hr_low_confirm", config.heartRateLowConfirmCount)
            .putLong("stale_ms", config.staleDataMs)
            .apply()
    }

    fun saveTiming(context: Context, payload: ByteArray) {
        val p = String(payload).split('|')
        if (p.size < 13) return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("measurement_minutes", p[9].toIntOrNull()?.coerceIn(1, 60) ?: 5)
            .putInt("confirm_minutes", p[10].toIntOrNull()?.coerceIn(1, 10) ?: 2)
            .putInt("retry1_seconds", p[11].toIntOrNull()?.coerceIn(5, 300) ?: 30)
            .putInt("retry2_seconds", p[12].toIntOrNull()?.coerceIn(5, 600) ?: 60)
            .apply()
    }

    fun measurementIntervalMs(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("measurement_minutes", 5) * 60_000L

    fun confirmDelayMs(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("confirm_minutes", 2) * 60_000L

    fun retryWaitsMs(context: Context): List<Long> {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val r1 = p.getInt("retry1_seconds", 30).toLong() * 1_000L
        val r2 = p.getInt("retry2_seconds", 60).toLong() * 1_000L
        return listOf(0L, r1, r2)
    }

    fun decode(payload: ByteArray): AlarmConfig? {
        val p = String(payload).split('|')
        if (p.size < 9) return null
        return AlarmConfig(
            spo2CriticalImmediate = p[0].toIntOrNull() ?: return null,
            spo2LowThreshold = p[1].toIntOrNull() ?: return null,
            spo2ConfirmCount = p[2].toIntOrNull() ?: 2,
            heartRateHighThreshold = p[3].toIntOrNull() ?: return null,
            heartRateHighConfirmCount = p[4].toIntOrNull() ?: 2,
            heartRateLowEnabled = p[5].toBooleanStrictOrNull() ?: false,
            heartRateLowThreshold = p[6].toIntOrNull() ?: 45,
            heartRateLowConfirmCount = p[7].toIntOrNull() ?: 2,
            staleDataMs = p[8].toLongOrNull() ?: 10 * 60_000L
        )
    }
}
