package com.skhealth.guardian.wear.sensor

import android.content.Context
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerType
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey
import com.skhealth.guardian.wear.SensorGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class SamsungSensorGateway(private val context: Context) : SensorGateway {
    private var service: HealthTrackingService? = null
    private var hrTracker: HealthTracker? = null
    private var spo2Tracker: HealthTracker? = null
    private var connected = CompletableDeferred<Unit>()

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() { if (!connected.isCompleted) connected.complete(Unit) }
        override fun onConnectionEnded() { connected = CompletableDeferred() }
        override fun onConnectionFailed(e: HealthTrackingService.HealthTrackingServiceException?) {
            if (!connected.isCompleted) connected.completeExceptionally(e ?: IllegalStateException("Health service connection failed"))
        }
    }

    private suspend fun ensureConnected() {
        if (service == null) {
            service = HealthTrackingService(connectionListener, context)
            service!!.connectService()
        }
        connected.await()
    }

    override suspend fun measureHeartRate(timeoutMs: Long): Int? {
        ensureConnected()
        if (hrTracker == null) hrTracker = service!!.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
        val result = CompletableDeferred<Int?>()
        hrTracker!!.setEventListener(object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(list: MutableList<DataPoint>) {
                list.lastOrNull()?.let { dp ->
                    val hr = dp.getValue(ValueKey.HeartRateSet.HEART_RATE)
                    if (!result.isCompleted && hr > 0) result.complete(hr)
                }
            }
            override fun onFlushCompleted() = Unit
            override fun onError(error: HealthTracker.TrackerError?) { if (!result.isCompleted) result.complete(null) }
        })
        val value = withTimeoutOrNull(timeoutMs) { result.await() }
        hrTracker!!.unsetEventListener()
        return value
    }

    override suspend fun measureSpO2(timeoutMs: Long): Int? {
        ensureConnected()
        if (spo2Tracker == null) spo2Tracker = service!!.getHealthTracker(HealthTrackerType.SPO2_ON_DEMAND)
        val result = CompletableDeferred<Int?>()
        spo2Tracker!!.setEventListener(object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(list: MutableList<DataPoint>) {
                list.lastOrNull()?.let { dp ->
                    val value = dp.getValue(ValueKey.SpO2Set.SPO2)
                    if (!result.isCompleted && value > 0) result.complete(value.toInt())
                }
            }
            override fun onFlushCompleted() = Unit
            override fun onError(error: HealthTracker.TrackerError?) { if (!result.isCompleted) result.complete(null) }
        })
        val value = withTimeoutOrNull(timeoutMs) { result.await() }
        spo2Tracker!!.unsetEventListener()
        return value
    }

    override suspend fun reconnect() {
        runCatching { hrTracker?.unsetEventListener() }
        runCatching { spo2Tracker?.unsetEventListener() }
        runCatching { service?.disconnectService() }
        service = null
        connected = CompletableDeferred()
        ensureConnected()
    }
}
