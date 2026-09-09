package com.skhealth.guardian.wear

interface SensorGateway {
    suspend fun measureHeartRate(timeoutMs: Long = 30_000): Int?
    suspend fun measureSpO2(timeoutMs: Long = 30_000): Int?
    suspend fun reconnect()
}
