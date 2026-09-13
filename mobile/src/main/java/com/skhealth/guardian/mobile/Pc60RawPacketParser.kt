package com.skhealth.guardian.mobile

import com.skhealth.guardian.shared.Pc60Sample

/** Parser for the PC-60FW Nordic UART packets observed on the physical device. */
object Pc60RawPacketParser {
    fun parseMeasurement(bytes: ByteArray, timestampMs: Long): Pc60Sample? {
        if (bytes.size != 12) return null
        if (u(bytes, 0) != 0xAA || u(bytes, 1) != 0x55 || u(bytes, 2) != 0x0F) return null
        if (u(bytes, 3) != 0x08 || u(bytes, 4) != 0x01) return null
        if (!crc8MaximValid(bytes)) return null

        val spo2 = u(bytes, 5)
        val pulse = u(bytes, 6)
        val pi = u(bytes, 8) / 10.0
        val searching = spo2 == 0 || pulse == 0 || pi <= 0.0
        return Pc60Sample(
            timestampMs = timestampMs,
            spo2 = spo2,
            pulseRate = pulse,
            perfusionIndex = pi,
            probeOff = false,
            pulseSearching = searching
        )
    }

    /** Device reports battery as four levels (0..3); map to approximate quarter percentages for UI. */
    fun parseBatteryLevel(bytes: ByteArray): Int? {
        if (bytes.size != 7) return null
        if (u(bytes, 0) != 0xAA || u(bytes, 1) != 0x55 || u(bytes, 2) != 0xF0) return null
        if (u(bytes, 3) != 0x03 || u(bytes, 4) != 0x03) return null
        if (!crc8MaximValid(bytes)) return null
        return when (u(bytes, 5)) {
            0 -> 25
            1 -> 50
            2 -> 75
            3 -> 100
            else -> null
        }
    }

    internal fun crc8MaximValid(bytes: ByteArray): Boolean {
        var crc = 0
        for (b in bytes) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x01 != 0) (crc ushr 1) xor 0x8C else crc ushr 1
            }
        }
        return crc == 0
    }

    private fun u(bytes: ByteArray, index: Int) = bytes[index].toInt() and 0xFF
}
