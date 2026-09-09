package com.skhealth.guardian.wear

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearCommandService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/health/selftest" -> runSelfTest()
            "/health/measure_now" -> ContextCompat.startForegroundService(
                this,
                Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_MEASURE_NOW)
            )
            "/health/config" -> WearSettings.decode(event.data)?.let { WearSettings.save(this, it) }
            "/health/silence" -> LocalAlarm.cancel(this)
        }
    }

    private fun runSelfTest() {
        scope.launch {
            val status = runCatching {
                val sensor = com.skhealth.guardian.wear.sensor.SamsungSensorGateway(this@WearCommandService)
                sensor.reconnect()
                val hr = runCatching { sensor.measureHeartRate(20_000) }.getOrNull()
                val spo2 = runCatching { sensor.measureSpO2(35_000) }.getOrNull()
                when {
                    hr != null && spo2 != null -> "OK | HR=$hr bpm | SpO₂=$spo2%"
                    hr != null -> "UYARI | HR OK ($hr bpm) | SpO₂ yanıt vermedi"
                    spo2 != null -> "UYARI | SpO₂ OK ($spo2%) | HR yanıt vermedi"
                    else -> "HATA | HR ve SpO₂ sensörlerinden geçerli yanıt yok"
                }
            }.getOrElse { "HATA | ${it.javaClass.simpleName}" }
            val nodes = Wearable.getNodeClient(this@WearCommandService).connectedNodes.awaitCompat2()
            nodes.forEach {
                Wearable.getMessageClient(this@WearCommandService)
                    .sendMessage(it.id, "/health/status", status.toByteArray())
                    .awaitCompat2()
            }
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitCompat2(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) {} }
        addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.failure(it)) }
    }
