package com.skhealth.guardian.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.skhealth.guardian.shared.Pc60Sample
import java.util.UUID

class Pc60BleService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private var packetCount = 0L
    private var currentName = ""
    private var currentAddress = ""
    private var streamStartedAt = 0L
    private var lastDataAt = 0L
    private var noDataRecoveryAttempts = 0
    private lateinit var sdkRuntime: Pc60SdkRuntime
    private lateinit var alarmController: Pc60AlarmController

    private val adapter: BluetoothAdapter?
        get() = getSystemService(BluetoothManager::class.java).adapter

    private val dataWatchdog = object : Runnable {
        override fun run() {
            checkDataFlow()
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        alarmController = Pc60AlarmController(this)
        sdkRuntime = Pc60SdkRuntimeFactory.create()

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Orko Takip • PC-60FW", NotificationManager.IMPORTANCE_LOW))
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Orko Takip")
                .setContentText("PC-60FW bağlantısı aktif • Bluetooth oksimetre izleniyor")
                .setOngoing(true)
                .build()
        )

        handler.postDelayed(dataWatchdog, WATCHDOG_INTERVAL_MS)
        if (sdkRuntime.available) startSdkRuntime() else connectOrScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESCAN -> {
                Pc60StatusStore.clearRemembered(this)
                resetDataWatch()
                if (sdkRuntime.available) {
                    sdkRuntime.restart()
                    markStreamStarted()
                } else {
                    disconnectGatt()
                    startScan()
                }
            }
            ACTION_STOP -> stopSelf()
            else -> if (!sdkRuntime.available) connectOrScan()
        }
        return START_STICKY
    }

    private fun startSdkRuntime() {
        if (!permissionsReady()) {
            updateState("Bluetooth izni bekleniyor")
            return
        }
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            updateState("Bluetooth kapalı")
            return
        }
        resetDataWatch()
        markStreamStarted()
        updateState("Lepu SDK ile PC-60FW hazırlanıyor")
        sdkRuntime.start(
            context = this,
            onState = { state -> handler.post {
                updateState(state)
                if (state.contains("bağ", ignoreCase = true) || state.contains("connect", ignoreCase = true)) {
                    markStreamStarted()
                }
            } },
            onSample = { sample -> handler.post { onSdkSample(sample) } }
        )
    }

    private fun onSdkSample(sample: Pc60Sample) {
        markDataReceived()
        packetCount += 1
        val old = Pc60StatusStore.load(this)
        Pc60StatusStore.save(
            this,
            old.copy(
                state = if (sample.valid) "Bağlı; Lepu RtParam geliyor" else "Bağlı; ölçüm stabilizasyonu bekleniyor",
                deviceName = old.deviceName.ifBlank { "PC-60FW" },
                lastPacketAt = sample.timestampMs,
                packetCount = packetCount,
                spo2 = sample.spo2,
                heartRate = sample.pulseRate,
                perfusionIndex = sample.perfusionIndex,
                batteryLevel = sample.batteryLevel,
                probeOff = sample.probeOff,
                pulseSearching = sample.pulseSearching
            )
        )
        alarmController.onSample(sample)
    }

    private fun connectOrScan() {
        if (!permissionsReady()) {
            updateState("Bluetooth izni bekleniyor")
            return
        }
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            updateState("Bluetooth kapalı")
            return
        }
        val remembered = Pc60StatusStore.savedAddress(this)
        if (remembered.isNotBlank()) {
            runCatching { bt.getRemoteDevice(remembered) }.getOrNull()?.let {
                connect(it, it.name ?: "PC-60FW")
                return
            }
        }
        startScan()
    }

    private fun startScan() {
        if (!permissionsReady()) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (scanning) return
        scanning = true
        resetDataWatch()
        updateState("PC-60FW aranıyor (ham BLE teşhis modu)")
        runCatching { scanner.startScan(scanCallback) }
            .onFailure { scanning = false; updateState("Tarama başlatılamadı: ${it.javaClass.simpleName}") }
        handler.postDelayed({
            if (scanning) {
                stopScan()
                updateState("PC-60FW bulunamadı; tekrar aranacak")
                handler.postDelayed({ startScan() }, RESCAN_DELAY_MS)
            }
        }, SCAN_WINDOW_MS)
    }

    private fun stopScan() {
        if (!scanning || !permissionsReady()) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!permissionsReady()) return
            val name = runCatching { result.device.name }.getOrNull().orEmpty()
            if (!looksLikePc60(name)) return
            stopScan()
            Pc60StatusStore.rememberAddress(this@Pc60BleService, result.device.address)
            connect(result.device, name.ifBlank { "PC-60FW" })
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            updateState("BLE tarama hatası: $errorCode")
            handler.postDelayed({ startScan() }, RESCAN_DELAY_MS)
        }
    }

    private fun connect(device: BluetoothDevice, name: String) {
        if (!permissionsReady()) return
        disconnectGatt()
        currentName = name
        currentAddress = device.address
        resetDataWatch()
        updateState("Bağlanıyor (ham BLE teşhis modu)")
        gatt = if (Build.VERSION.SDK_INT >= 23) {
            device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(this, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                updateState("Bağlandı; servisler okunuyor")
                runCatching { gatt.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                resetDataWatch()
                updateState("Bağlantı koptu; yeniden bağlanacak")
                runCatching { gatt.close() }
                if (this@Pc60BleService.gatt === gatt) this@Pc60BleService.gatt = null
                handler.postDelayed({ connectOrScan() }, RECONNECT_DELAY_MS)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateState("GATT servisleri okunamadı: $status")
                reconnectSoon()
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            val notify = service?.getCharacteristic(NOTIFY_UUID)
            if (notify == null) {
                updateState("PC-60FW notify kanalı bulunamadı")
                reconnectSoon()
                return
            }
            val ok = runCatching { gatt.setCharacteristicNotification(notify, true) }.getOrDefault(false)
            val ccc = notify.getDescriptor(CCC_UUID)
            if (!ok || ccc == null) {
                updateState("Notify açılamadı")
                reconnectSoon()
                return
            }
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(ccc)
            }
            markStreamStarted()
            updateState("Bağlı; ham BLE verisi bekleniyor")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == NOTIFY_UUID) onPacket(value)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (characteristic.uuid == NOTIFY_UUID) onPacket(characteristic.value ?: return)
        }
    }

    private fun onPacket(bytes: ByteArray) {
        markDataReceived()
        packetCount += 1
        val old = Pc60StatusStore.load(this)
        Pc60StatusStore.save(
            this,
            old.copy(
                state = "Bağlı; ham BLE verisi geliyor",
                deviceName = currentName,
                address = currentAddress,
                lastPacketAt = System.currentTimeMillis(),
                packetCount = packetCount,
                lastPacketHex = bytes.take(48).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            )
        )
    }

    private fun markStreamStarted() {
        if (streamStartedAt == 0L) streamStartedAt = System.currentTimeMillis()
    }

    private fun markDataReceived() {
        lastDataAt = System.currentTimeMillis()
        streamStartedAt = lastDataAt
        noDataRecoveryAttempts = 0
    }

    private fun resetDataWatch() {
        streamStartedAt = 0L
        lastDataAt = 0L
        noDataRecoveryAttempts = 0
    }

    private fun checkDataFlow() {
        val started = streamStartedAt
        if (started == 0L) return
        val now = System.currentTimeMillis()
        val reference = if (lastDataAt > 0L) lastDataAt else started
        val timeout = if (lastDataAt > 0L) STREAM_STALL_TIMEOUT_MS else FIRST_DATA_TIMEOUT_MS
        if (now - reference < timeout) return

        if (noDataRecoveryAttempts >= MAX_NO_DATA_RECOVERY_ATTEMPTS) {
            updateState("Bağlı fakat veri akmıyor; oksimetreyi/parmağı kontrol et")
            streamStartedAt = 0L
            AlarmTimelineStore.add(this, "PC-60FW TEKNİK", "Bağlantı var ancak veri akışı yok; sağlık alarmı üretilmedi")
            return
        }

        noDataRecoveryAttempts += 1
        updateState("PC-60FW veri akışı durdu; bağlantı yenileniyor (${noDataRecoveryAttempts}/$MAX_NO_DATA_RECOVERY_ATTEMPTS)")
        AlarmTimelineStore.add(this, "PC-60FW RECONNECT", "Veri akışı yok; yeniden bağlanma denemesi $noDataRecoveryAttempts")
        lastDataAt = 0L
        streamStartedAt = now
        if (sdkRuntime.available) {
            runCatching { sdkRuntime.restart() }
        } else {
            disconnectGatt()
            handler.postDelayed({ connectOrScan() }, RECONNECT_DELAY_MS)
        }
    }

    private fun reconnectSoon() {
        handler.postDelayed({ disconnectGatt(); connectOrScan() }, RECONNECT_DELAY_MS)
    }

    private fun disconnectGatt() {
        stopScan()
        val old = gatt
        gatt = null
        if (old != null && permissionsReady()) {
            runCatching { old.disconnect() }
            runCatching { old.close() }
        }
    }

    private fun updateState(state: String) {
        val old = Pc60StatusStore.load(this)
        Pc60StatusStore.save(this, old.copy(state = state, deviceName = currentName.ifBlank { old.deviceName }, address = currentAddress.ifBlank { old.address }))
    }

    private fun permissionsReady(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            has(Manifest.permission.BLUETOOTH_SCAN) && has(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            has(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::sdkRuntime.isInitialized) sdkRuntime.stop()
        disconnectGatt()
        resetDataWatch()
        updateState("Kapalı")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_RESCAN = "com.skhealth.guardian.mobile.PC60_RESCAN"
        const val ACTION_STOP = "com.skhealth.guardian.mobile.PC60_STOP"
        private const val CHANNEL = "pc60_ble"
        private const val NOTIFICATION_ID = 31
        private const val SCAN_WINDOW_MS = 15_000L
        private const val RESCAN_DELAY_MS = 15_000L
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val FIRST_DATA_TIMEOUT_MS = 20_000L
        private const val STREAM_STALL_TIMEOUT_MS = 8_000L
        private const val MAX_NO_DATA_RECOVERY_ATTEMPTS = 2

        private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val NOTIFY_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        private val CCC_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private fun looksLikePc60(name: String): Boolean {
            val n = name.uppercase().replace(" ", "")
            return n.contains("PC-60") || n.contains("PC60")
        }
    }
}
