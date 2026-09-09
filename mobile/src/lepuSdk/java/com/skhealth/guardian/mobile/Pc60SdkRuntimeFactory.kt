package com.skhealth.guardian.mobile

import android.content.Context
import androidx.lifecycle.Observer
import com.jeremyliao.liveeventbus.LiveEventBus
import com.lepu.blepro.event.EventMsgConst
import com.lepu.blepro.event.InterfaceEvent
import com.lepu.blepro.ext.BleServiceHelper
import com.lepu.blepro.ext.pc60fw.RtParam
import com.lepu.blepro.objs.Bluetooth
import com.skhealth.guardian.shared.Pc60Sample

object Pc60SdkRuntimeFactory {
    fun create(): Pc60SdkRuntime = LepuPc60SdkRuntime()
}

private class LepuPc60SdkRuntime : Pc60SdkRuntime {
    override val available: Boolean = true
    private val model = Bluetooth.MODEL_PC60FW
    private var appContext: Context? = null
    private var stateCallback: ((String) -> Unit)? = null
    private var sampleCallback: ((Pc60Sample) -> Unit)? = null
    private var lastBattery: Int? = null
    private var started = false

    private val serviceReadyObserver = Observer<Boolean> { ready ->
        if (ready == true) {
            stateCallback?.invoke("Lepu SDK hazır; PC-60FW aranıyor")
            runCatching { BleServiceHelper.BleServiceHelper.startScan(intArrayOf(model)) }
                .onFailure { stateCallback?.invoke("Lepu SDK tarama hatası: ${it.javaClass.simpleName}") }
        }
    }

    private val deviceFoundObserver = Observer<Bluetooth> { device ->
        if (device?.model != model) return@Observer
        val context = appContext ?: return@Observer
        runCatching {
            stateCallback?.invoke("PC-60FW bulundu; SDK bağlanıyor")
            BleServiceHelper.BleServiceHelper.setInterfaces(model)
            BleServiceHelper.BleServiceHelper.stopScan()
            BleServiceHelper.BleServiceHelper.connect(
                context.applicationContext,
                model,
                device.device,
                bluetooth = device
            )
        }.onFailure {
            stateCallback?.invoke("Lepu SDK bağlantı hatası: ${it.javaClass.simpleName}")
        }
    }

    private val rtParamObserver = Observer<InterfaceEvent> { event ->
        if (event == null || event.model != model) return@Observer
        val data = event.data as? RtParam ?: return@Observer
        val sample = Pc60Sample(
            timestampMs = System.currentTimeMillis(),
            spo2 = data.spo2,
            pulseRate = data.pr,
            perfusionIndex = data.pi.toDouble(),
            probeOff = data.isProbeOff,
            pulseSearching = data.isPulseSearching,
            batteryLevel = lastBattery
        )
        stateCallback?.invoke(if (sample.valid) "Bağlı; Lepu RtParam geliyor" else "Bağlı; ölçüm stabilizasyonu bekleniyor")
        sampleCallback?.invoke(sample)
    }

    private val batteryObserver = Observer<InterfaceEvent> { event ->
        if (event == null || event.model != model) return@Observer
        lastBattery = (event.data as? Int)?.coerceIn(0, 3)
    }

    override fun start(
        context: Context,
        onState: (String) -> Unit,
        onSample: (Pc60Sample) -> Unit
    ) {
        if (started) return
        started = true
        appContext = context.applicationContext
        stateCallback = onState
        sampleCallback = onSample

        LiveEventBus.get<Boolean>(EventMsgConst.Ble.EventServiceConnectedAndInterfaceInit)
            .observeForever(serviceReadyObserver)
        LiveEventBus.get<Bluetooth>(EventMsgConst.Discovery.EventDeviceFound)
            .observeForever(deviceFoundObserver)
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.PC60Fw.EventPC60FwRtParam)
            .observeForever(rtParamObserver)
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.PC60Fw.EventPC60FwBatLevel)
            .observeForever(batteryObserver)

        runCatching {
            val helper = BleServiceHelper.BleServiceHelper
            if (helper.checkService()) {
                onState("Lepu SDK hazır; PC-60FW aranıyor")
                helper.startScan(intArrayOf(model))
            } else {
                onState("Lepu SDK başlatılıyor")
                helper.initService(context.applicationContext)
            }
        }.onFailure { onState("Lepu SDK başlatma hatası: ${it.javaClass.simpleName}") }
    }

    override fun restart() {
        if (!started) return
        runCatching {
            val helper = BleServiceHelper.BleServiceHelper
            helper.disconnect(model, false)
            helper.setInterfaces(model)
            helper.startScan(intArrayOf(model))
            stateCallback?.invoke("Lepu SDK yeniden bağlantı arıyor")
        }.onFailure { stateCallback?.invoke("Lepu SDK yeniden başlatma hatası: ${it.javaClass.simpleName}") }
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { BleServiceHelper.BleServiceHelper.disconnect(model, false) }
        LiveEventBus.get<Boolean>(EventMsgConst.Ble.EventServiceConnectedAndInterfaceInit)
            .removeObserver(serviceReadyObserver)
        LiveEventBus.get<Bluetooth>(EventMsgConst.Discovery.EventDeviceFound)
            .removeObserver(deviceFoundObserver)
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.PC60Fw.EventPC60FwRtParam)
            .removeObserver(rtParamObserver)
        LiveEventBus.get<InterfaceEvent>(InterfaceEvent.PC60Fw.EventPC60FwBatLevel)
            .removeObserver(batteryObserver)
        appContext = null
        stateCallback = null
        sampleCallback = null
    }
}
