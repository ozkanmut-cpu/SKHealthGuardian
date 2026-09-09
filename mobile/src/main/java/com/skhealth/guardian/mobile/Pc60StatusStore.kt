package com.skhealth.guardian.mobile

import android.content.Context

data class Pc60Status(
    val state: String = "Kapalı",
    val deviceName: String = "",
    val address: String = "",
    val lastPacketAt: Long = 0L,
    val packetCount: Long = 0L,
    val lastPacketHex: String = ""
)

object Pc60StatusStore {
    private const val PREF = "pc60_status"

    fun save(context: Context, status: Pc60Status) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("state", status.state)
            .putString("name", status.deviceName)
            .putString("address", status.address)
            .putLong("last_packet", status.lastPacketAt)
            .putLong("packet_count", status.packetCount)
            .putString("last_hex", status.lastPacketHex)
            .apply()
    }

    fun load(context: Context): Pc60Status {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return Pc60Status(
            state = p.getString("state", "Kapalı") ?: "Kapalı",
            deviceName = p.getString("name", "") ?: "",
            address = p.getString("address", "") ?: "",
            lastPacketAt = p.getLong("last_packet", 0L),
            packetCount = p.getLong("packet_count", 0L),
            lastPacketHex = p.getString("last_hex", "") ?: ""
        )
    }

    fun savedAddress(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("preferred_address", "") ?: ""

    fun rememberAddress(context: Context, address: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("preferred_address", address).apply()
    }

    fun clearRemembered(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("preferred_address").apply()
    }
}
