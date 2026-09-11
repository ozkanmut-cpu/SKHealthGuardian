package com.skhealth.guardian.mobile

import android.content.Context

object AccuChekStatusStore {
    private const val PREF = "accu_chek_status"
    private const val KEY_ADDRESS = "address"
    private const val KEY_NAME = "name"
    private const val KEY_STATE = "state"
    private const val KEY_LAST_READING_AT = "last_reading_at"
    private const val KEY_LAST_VALUE = "last_value"
    private const val KEY_PENDING_COUNT = "pending_count"

    data class Status(
        val state: String = "Bağlı değil",
        val deviceName: String = "Accu-Chek Instant",
        val lastReadingAtMs: Long? = null,
        val lastValueMgDl: Int? = null,
        val pendingOwnershipCount: Int = 0
    )

    fun load(context: Context): Status {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return Status(
            state = p.getString(KEY_STATE, "Bağlı değil") ?: "Bağlı değil",
            deviceName = p.getString(KEY_NAME, "Accu-Chek Instant") ?: "Accu-Chek Instant",
            lastReadingAtMs = p.getLong(KEY_LAST_READING_AT, 0L).takeIf { it > 0L },
            lastValueMgDl = p.getInt(KEY_LAST_VALUE, -1).takeIf { it >= 0 },
            pendingOwnershipCount = p.getInt(KEY_PENDING_COUNT, 0).coerceAtLeast(0)
        )
    }

    fun save(context: Context, status: Status) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, status.state)
            .putString(KEY_NAME, status.deviceName)
            .putLong(KEY_LAST_READING_AT, status.lastReadingAtMs ?: 0L)
            .putInt(KEY_LAST_VALUE, status.lastValueMgDl ?: -1)
            .putInt(KEY_PENDING_COUNT, status.pendingOwnershipCount)
            .apply()
    }

    fun savedAddress(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_ADDRESS, "") ?: ""

    fun rememberDevice(context: Context, address: String, name: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun clearRemembered(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .remove(KEY_ADDRESS)
            .remove(KEY_NAME)
            .apply()
    }
}
