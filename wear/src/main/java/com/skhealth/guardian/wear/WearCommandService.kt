package com.skhealth.guardian.wear

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
        if (event.path != "/health/selftest") return
        scope.launch {
            val status = runCatching {
                val sensor = com.skhealth.guardian.wear.sensor.SamsungSensorGateway(this@WearCommandService)
                sensor.reconnect()
                val hr = sensor.measureHeartRate(20_000)
                if (hr != null) "OK | sensör hazır | nabız=$hr" else "UYARI | sensör yanıt vermedi"
            }.getOrElse { "HATA | ${it.javaClass.simpleName}" }
            val nodes = Wearable.getNodeClient(this@WearCommandService).connectedNodes.awaitCompat2()
            nodes.forEach { Wearable.getMessageClient(this@WearCommandService).sendMessage(it.id, "/health/status", status.toByteArray()).awaitCompat2() }
        }
    }
}
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitCompat2(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) {} }
        addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.failure(it)) }
    }
