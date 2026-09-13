package com.skhealth.guardian.mobile

import com.skhealth.guardian.shared.Pc60Sample

/**
 * Streaming parser for the PC-60FW Nordic UART transport observed on the physical device.
 *
 * Android BLE notifications are transport chunks, not guaranteed to align with PC-60FW frames.
 * A frame starts with AA 55 and its total size is 4 + the length byte at index 3.
 */
object Pc60RawPacketParser {
    private const val MAX_BUFFER = 512
    private const val MAX_FRAME = 80
    private val buffer = ArrayList<Byte>()

    @Synchronized
    fun parseMeasurement(bytes: ByteArray, timestampMs: Long): Pc60Sample? {
        append(bytes)
        var latest: Pc60Sample? = null
        while (true) {
            val frame = takeFrame() ?: break
            decodeMeasurement(frame, timestampMs)?.let { latest = it }
        }
        return latest
    }

    /** Battery packets are still accepted when a complete frame arrives in one BLE notification. */
    fun parseBatteryLevel(bytes: ByteArray): Int? = decodeBattery(bytes)

    @Synchronized
    fun reset() {
        buffer.clear()
    }

    private fun append(bytes: ByteArray) {
        bytes.forEach { buffer.add(it) }
        if (buffer.size > MAX_BUFFER) {
            // Keep enough tail to find the next AA 55 header while preventing unbounded growth.
            val keep = buffer.takeLast(MAX_FRAME)
            buffer.clear()
            buffer.addAll(keep)
        }
    }

    /** Returns one complete AA 55 frame, or null when more BLE bytes are required. */
    private fun takeFrame(): ByteArray? {
        while (true) {
            if (buffer.size < 2) return null

            var header = -1
            for (i in 0 until buffer.size - 1) {
                if (u(buffer[i]) == 0xAA && u(buffer[i + 1]) == 0x55) {
                    header = i
                    break
                }
            }

            if (header < 0) {
                // Preserve a trailing AA because the following notification may start with 55.
                val keepAa = u(buffer.last()) == 0xAA
                buffer.clear()
                if (keepAa) buffer.add(0xAA.toByte())
                return null
            }

            repeat(header) { buffer.removeAt(0) }
            if (buffer.size < 4) return null

            val payloadLength = u(buffer[3])
            val totalLength = 4 + payloadLength
            if (payloadLength <= 0 || totalLength > MAX_FRAME) {
                // Implausible length: move one byte forward and resynchronise.
                buffer.removeAt(0)
                continue
            }
            if (buffer.size < totalLength) return null

            return ByteArray(totalLength) { buffer.removeAt(0) }
        }
    }

    private fun decodeMeasurement(bytes: ByteArray, timestampMs: Long): Pc60Sample? {
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

    private fun decodeBattery(bytes: ByteArray): Int? {
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
    private fun u(byte: Byte) = byte.toInt() and 0xFF
}
