package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.AlarmConfig
import com.skhealth.guardian.shared.AlarmEngineState

object AlarmEngineStateStore {
    private const val PREF = "alarm_engine_state"
    private const val KEY_SIGNATURE = "signature"

    fun signature(config: AlarmConfig, spo2Pc60: Boolean, hrPc60: Boolean): String = listOf(
        config.spo2CriticalImmediate,
        config.spo2LowThreshold,
        config.spo2ConfirmCount,
        config.heartRateHighThreshold,
        config.heartRateHighConfirmCount,
        config.heartRateLowEnabled,
        config.heartRateLowThreshold,
        config.heartRateLowConfirmCount,
        spo2Pc60,
        hrPc60
    ).joinToString("|")

    @Synchronized
    fun load(context: Context, expectedSignature: String): AlarmEngineState? {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (p.getString(KEY_SIGNATURE, null) != expectedSignature) return null
        return AlarmEngineState(
            lowSpo2Count = p.getInt("low_spo2_count", 0),
            highHrCount = p.getInt("high_hr_count", 0),
            lowHrCount = p.getInt("low_hr_count", 0),
            spo2Episode = p.getString("spo2_episode", "NONE") ?: "NONE",
            highHrEpisodeActive = p.getBoolean("high_hr_episode", false),
            lowHrEpisodeActive = p.getBoolean("low_hr_episode", false)
        )
    }

    @Synchronized
    fun save(context: Context, signature: String, state: AlarmEngineState) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_SIGNATURE, signature)
            .putInt("low_spo2_count", state.lowSpo2Count)
            .putInt("high_hr_count", state.highHrCount)
            .putInt("low_hr_count", state.lowHrCount)
            .putString("spo2_episode", state.spo2Episode)
            .putBoolean("high_hr_episode", state.highHrEpisodeActive)
            .putBoolean("low_hr_episode", state.lowHrEpisodeActive)
            .commit()
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
