package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.AlarmConfig

object AppSettings {
    private const val PREF = "guardian_settings"

    fun load(context: Context): AlarmConfig {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return AlarmConfig(
            spo2CriticalImmediate = p.getInt("spo2_critical", 80),
            spo2LowThreshold = p.getInt("spo2_low", 90),
            spo2ConfirmCount = 2,
            heartRateHighThreshold = p.getInt("hr_high", 130),
            heartRateHighConfirmCount = 2,
            heartRateLowEnabled = p.getBoolean("hr_low_enabled", false),
            heartRateLowThreshold = p.getInt("hr_low", 45),
            heartRateLowConfirmCount = 2,
            staleDataMs = p.getLong("stale_ms", 10 * 60_000L)
        )
    }

    fun save(context: Context, config: AlarmConfig) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("spo2_critical", config.spo2CriticalImmediate)
            .putInt("spo2_low", config.spo2LowThreshold)
            .putInt("hr_high", config.heartRateHighThreshold)
            .putBoolean("hr_low_enabled", config.heartRateLowEnabled)
            .putInt("hr_low", config.heartRateLowThreshold)
            .putLong("stale_ms", config.staleDataMs)
            .apply()
        WatchCommandSender(context).sendConfig(config)
    }

    fun escalationMinutes(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("escalation_minutes", 0)

    fun setEscalationMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("escalation_minutes", minutes.coerceIn(0, 60))
            .apply()
    }
}
