package com.skhealth.guardian.mobile

import android.content.Context

data class Pc60Status(
    val state: String = "Kapalı",
    val deviceName: String = "",
    val address: String = "",
    val lastPacketAt: Long = 0L,
    val packetCount: Long = 0L,
    val lastPacketHex: String = "",
    val spo2: Int? = null,
    val heartRate: Int? = null,
    val perfusionIndex: Double? = null,
    val batteryLevel: Int? = null,
    val probeOff: Boolean = false,
    val pulseSearching: Boolean = false
)

object Pc60StatusStore {
    private const val PREF = "pc60_status"

    @Synchronized
    fun save(context: Context, status: Pc60Status) {
        val current = load(context)
        val incomingIsOlder = status.lastPacketAt > 0L && current.lastPacketAt > 0L && status.lastPacketAt < current.lastPacketAt
        val merged = if (incomingIsOlder) {
            current.copy(
                state = status.state,
                deviceName = status.deviceName.ifBlank { current.deviceName },
                address = status.address.ifBlank { current.address },
                packetCount = maxOf(current.packetCount, status.packetCount)
            )
        } else {
            status.copy(
                lastPacketAt = maxOf(current.lastPacketAt, status.lastPacketAt),
                packetCount = maxOf(current.packetCount, status.packetCount)
            )
        }

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("state", merged.state)
            .putString("name", merged.deviceName)
            .putString("address", merged.address)
            .putLong("last_packet", merged.lastPacketAt)
            .putLong("packet_count", merged.packetCount)
            .putString("last_hex", merged.lastPacketHex)
            .putInt("spo2", merged.spo2 ?: -1)
            .putInt("heart_rate", merged.heartRate ?: -1)
            .putLong("pi_bits", java.lang.Double.doubleToRawLongBits(merged.perfusionIndex ?: Double.NaN))
            .putInt("battery", merged.batteryLevel ?: -1)
            .putBoolean("probe_off", merged.probeOff)
            .putBoolean("pulse_searching", merged.pulseSearching)
            .apply()
    }

    fun load(context: Context): Pc60Status {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val pi = java.lang.Double.longBitsToDouble(p.getLong("pi_bits", java.lang.Double.doubleToRawLongBits(Double.NaN)))
        return Pc60Status(
            state = p.getString("state", "Kapalı") ?: "Kapalı",
            deviceName = p.getString("name", "") ?: "",
            address = p.getString("address", "") ?: "",
            lastPacketAt = p.getLong("last_packet", 0L),
            packetCount = p.getLong("packet_count", 0L),
            lastPacketHex = p.getString("last_hex", "") ?: "",
            spo2 = p.getInt("spo2", -1).takeIf { it >= 0 },
            heartRate = p.getInt("heart_rate", -1).takeIf { it >= 0 },
            perfusionIndex = pi.takeUnless { it.isNaN() },
            batteryLevel = p.getInt("battery", -1).takeIf { it >= 0 },
            probeOff = p.getBoolean("probe_off", false),
            pulseSearching = p.getBoolean("pulse_searching", false)
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
