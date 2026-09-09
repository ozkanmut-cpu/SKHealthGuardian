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
        WatchCommandSender(context).sendConfig(config)
    }

    fun escalationMinutes(context: Context): Int = prefs(context).getInt("escalation_minutes", 0)
    fun setEscalationMinutes(context: Context, minutes: Int) = edit(context).putInt("escalation_minutes", minutes.coerceIn(0, 60)).apply()

    fun escalationMaxAgeMinutes(context: Context): Int = prefs(context).getInt("escalation_max_age_minutes", 60)
    fun setEscalationMaxAgeMinutes(context: Context, minutes: Int) = edit(context).putInt("escalation_max_age_minutes", minutes.coerceIn(5, 1440)).apply()

    fun watchMeasurementMinutes(context: Context): Int = prefs(context).getInt("watch_measurement_minutes", 5)
    fun setWatchMeasurementMinutes(context: Context, value: Int) = edit(context).putInt("watch_measurement_minutes", value.coerceIn(1, 60)).apply()

    fun watchConfirmMinutes(context: Context): Int = prefs(context).getInt("watch_confirm_minutes", 2)
    fun setWatchConfirmMinutes(context: Context, value: Int) = edit(context).putInt("watch_confirm_minutes", value.coerceIn(1, 10)).apply()

    fun watchRetry1Seconds(context: Context): Int = prefs(context).getInt("watch_retry1_seconds", 30)
    fun setWatchRetry1Seconds(context: Context, value: Int) = edit(context).putInt("watch_retry1_seconds", value.coerceIn(5, 300)).apply()

    fun watchRetry2Seconds(context: Context): Int = prefs(context).getInt("watch_retry2_seconds", 60)
    fun setWatchRetry2Seconds(context: Context, value: Int) = edit(context).putInt("watch_retry2_seconds", value.coerceIn(5, 600)).apply()

    fun pc60AlarmThreshold(context: Context): Int = prefs(context).getInt("pc60_alarm_threshold", 85)
    fun setPc60AlarmThreshold(context: Context, value: Int) = edit(context).putInt("pc60_alarm_threshold", value.coerceIn(50, 99)).apply()

    fun pc60ConfirmMinutes(context: Context): Int = prefs(context).getInt("pc60_confirm_minutes", 2)
    fun setPc60ConfirmMinutes(context: Context, value: Int) = edit(context).putInt("pc60_confirm_minutes", value.coerceIn(1, 10)).apply()

    fun pc60RecoveryThreshold(context: Context): Int = prefs(context).getInt("pc60_recovery_threshold", 85)
    fun setPc60RecoveryThreshold(context: Context, value: Int) = edit(context).putInt("pc60_recovery_threshold", value.coerceIn(50, 99)).apply()

    fun pc60StableSeconds(context: Context): Int = prefs(context).getInt("pc60_stable_seconds", 10)
    fun setPc60StableSeconds(context: Context, value: Int) = edit(context).putInt("pc60_stable_seconds", value.coerceIn(3, 120)).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun edit(context: Context) = prefs(context).edit()
}
