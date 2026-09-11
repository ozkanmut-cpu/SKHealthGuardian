package com.skhealth.guardian.shared

/** Minimal Bluetooth SIG Record Access Control Point helpers for glucose meters. */
object BleGlucoseRacp {
    private const val OP_REPORT_STORED_RECORDS = 0x01
    private const val OP_NUMBER_OF_STORED_RECORDS_RESPONSE = 0x05
    private const val OP_RESPONSE_CODE = 0x06

    private const val OPERATOR_NULL = 0x00
    private const val OPERATOR_ALL_RECORDS = 0x01
    private const val OPERATOR_GREATER_OR_EQUAL = 0x03
    private const val FILTER_SEQUENCE_NUMBER = 0x01

    data class Response(
        val opcode: Int,
        val requestOpcode: Int? = null,
        val responseCode: Int? = null,
        val numberOfRecords: Int? = null
    ) {
        val success: Boolean
            get() = opcode == OP_RESPONSE_CODE && responseCode == 0x01

        val noRecordsFound: Boolean
            get() = opcode == OP_RESPONSE_CODE && responseCode == 0x06
    }

    fun reportAllRecordsCommand(): ByteArray = byteArrayOf(
        OP_REPORT_STORED_RECORDS.toByte(),
        OPERATOR_ALL_RECORDS.toByte()
    )

    /** Requests records with sequence number >= the supplied value. */
    fun reportFromSequenceCommand(sequenceNumber: Int): ByteArray {
        require(sequenceNumber in 0..0xFFFF) { "sequenceNumber must fit uint16" }
        return byteArrayOf(
            OP_REPORT_STORED_RECORDS.toByte(),
            OPERATOR_GREATER_OR_EQUAL.toByte(),
            FILTER_SEQUENCE_NUMBER.toByte(),
            (sequenceNumber and 0xFF).toByte(),
            ((sequenceNumber ushr 8) and 0xFF).toByte()
        )
    }

    fun parseResponse(payload: ByteArray): Response {
        require(payload.size >= 2) { "RACP payload is too short" }
        val opcode = u8(payload[0])
        return when (opcode) {
            OP_RESPONSE_CODE -> {
                require(payload.size >= 4) { "RACP response-code payload is too short" }
                require(u8(payload[1]) == OPERATOR_NULL) { "Invalid RACP response operator" }
                Response(
                    opcode = opcode,
                    requestOpcode = u8(payload[2]),
                    responseCode = u8(payload[3])
                )
            }
            OP_NUMBER_OF_STORED_RECORDS_RESPONSE -> {
                require(payload.size >= 4) { "RACP number-response payload is too short" }
                require(u8(payload[1]) == OPERATOR_NULL) { "Invalid RACP number response operator" }
                Response(
                    opcode = opcode,
                    numberOfRecords = u16(payload, 2)
                )
            }
            else -> Response(opcode = opcode)
        }
    }

    private fun u8(value: Byte): Int = value.toInt() and 0xFF
    private fun u16(payload: ByteArray, offset: Int): Int =
        u8(payload[offset]) or (u8(payload[offset + 1]) shl 8)
}
