package com.skhealth.guardian.wear.sensor

import android.content.Context
import com.skhealth.guardian.wear.SensorGateway

class SamsungSensorGateway(context: Context) : SensorGateway {
    override suspend fun measureHeartRate(timeoutMs: Long): Int? = null
    override suspend fun measureSpO2(timeoutMs: Long): Int? = null
    override suspend fun reconnect() = Unit
}
