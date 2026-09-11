package com.skhealth.guardian.mobile

import android.content.Context

object GlucoseSettings {
    private const val PREF = "glucose_settings"
    private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    private const val KEY_OVERDUE_ENABLED = "overdue_enabled"
    private const val KEY_OVERDUE_GRACE_MINUTES = "overdue_grace_minutes"
    private const val KEY_OWNERSHIP_PROMPT_ENABLED = "ownership_prompt_enabled"

    data class Config(
        val remindersEnabled: Boolean,
        val overdueEnabled: Boolean,
        val overdueGraceMinutes: Int,
        val ownershipPromptEnabled: Boolean
    )

    fun load(context: Context): Config {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return Config(
            remindersEnabled = prefs.getBoolean(KEY_REMINDERS_ENABLED, true),
            overdueEnabled = prefs.getBoolean(KEY_OVERDUE_ENABLED, true),
            overdueGraceMinutes = prefs.getInt(KEY_OVERDUE_GRACE_MINUTES, 1).coerceIn(1, 120),
            ownershipPromptEnabled = prefs.getBoolean(KEY_OWNERSHIP_PROMPT_ENABLED, true)
        )
    }

    fun save(context: Context, config: Config) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REMINDERS_ENABLED, config.remindersEnabled)
            .putBoolean(KEY_OVERDUE_ENABLED, config.overdueEnabled)
            .putInt(KEY_OVERDUE_GRACE_MINUTES, config.overdueGraceMinutes.coerceIn(1, 120))
            .putBoolean(KEY_OWNERSHIP_PROMPT_ENABLED, config.ownershipPromptEnabled)
            .apply()
        GlucoseReminderReceiver.reschedule(context)
    }
}
