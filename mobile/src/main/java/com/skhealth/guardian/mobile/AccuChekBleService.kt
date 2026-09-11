package com.skhealth.guardian.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import com.skhealth.guardian.shared.BleGlucoseMeasurementParser
import com.skhealth.guardian.shared.BloodGlucoseReading
import java.util.UUID

/**
 * Direct BLE client for standards-based glucose meters such as Accu-Chek Instant.
 *
 * The Android system owns secure pairing/passkey UI. No meter PIN is embedded in
 * the app. Every imported result is stored UNCONFIRMED until the user attributes
 * it to Orko or another person.
 */
class AccuChekBleService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private var currentName = "Accu-Chek Instant"

    private val adapter: BluetoothAdapter?
        get() = getSystemService(BluetoothManager::class.java).adapter

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Accu-Chek bağlantısı", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Accu-Chek bağlantısı aktif")
                .setContentText("Şeker ölçüm cihazı izleniyor")
                .setOngoing(true)
                .build()
        )
        connectOrScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESCAN -> {
                AccuChekStatusStore.clearRemembered(this)
                disconnectGatt()
                startScan()
            }
            ACTION_STOP -> stopSelf()
            else -> connectOrScan()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopScan()
        disconnectGatt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        val remembered = AccuChekStatusStore.savedAddress(this)
        if (remembered.isNotBlank()) {
            runCatching { bt.getRemoteDevice(remembered) }.getOrNull()?.let {
                connect(it, runCatching { it.name }.getOrNull().orEmpty().ifBlank { currentName })
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
        updateState("Accu-Chek Instant aranıyor")
        runCatching { scanner.startScan(scanCallback) }
            .onFailure {
                scanning = false
                updateState("BLE tarama başlatılamadı: ${it.javaClass.simpleName}")
            }
        handler.postDelayed({
            if (scanning) {
                stopScan()
                updateState("Accu-Chek bulunamadı; tekrar aranacak")
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
            val advertisesGlucose = result.scanRecord?.serviceUuids?.any {
                it.uuid.toString().equals(BleGlucoseMeasurementParser.GLUCOSE_SERVICE_UUID, ignoreCase = true)
            } == true
            if (!advertisesGlucose && !looksLikeAccuChek(name)) return

            stopScan()
            currentName = name.ifBlank { "Accu-Chek Instant" }
            AccuChekStatusStore.rememberDevice(this@AccuChekBleService, result.device.address, currentName)
            connect(result.device, currentName)
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
        updateState("$name bağlanıyor")
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                updateState("Bağlandı; Glucose Service aranıyor")
                runCatching { gatt.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updateState("Bağlantı koptu; yeniden bağlanacak")
                runCatching { gatt.close() }
                if (this@AccuChekBleService.gatt === gatt) this@AccuChekBleService.gatt = null
                handler.postDelayed({ connectOrScan() }, RECONNECT_DELAY_MS)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateState("Servis keşfi başarısız: $status")
                return
            }
            val service = gatt.getService(UUID.fromString(BleGlucoseMeasurementParser.GLUCOSE_SERVICE_UUID))
            if (service == null) {
                updateState("Standart Glucose Service bulunamadı")
                return
            }
            val measurement = service.getCharacteristic(UUID.fromString(BleGlucoseMeasurementParser.GLUCOSE_MEASUREMENT_UUID))
            if (measurement == null) {
                updateState("Glucose Measurement karakteristiği bulunamadı")
                return
            }
            updateState("Bağlı; ölçüm bildirimi açılıyor")
            enableCcc(gatt, measurement, indicate = false)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristic(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristic(characteristic.uuid, value)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateState("BLE bildirim ayarı başarısız: $status")
                return
            }
            val characteristicUuid = descriptor.characteristic.uuid.toString()
            if (characteristicUuid.equals(BleGlucoseMeasurementParser.GLUCOSE_MEASUREMENT_UUID, true)) {
                val service = gatt.getService(UUID.fromString(BleGlucoseMeasurementParser.GLUCOSE_SERVICE_UUID)) ?: return
                val racp = service.getCharacteristic(UUID.fromString(BleGlucoseMeasurementParser.RECORD_ACCESS_CONTROL_POINT_UUID))
                if (racp != null) {
                    enableCcc(gatt, racp, indicate = true)
                } else {
                    updateState("Bağlı; canlı şeker ölçümleri bekleniyor")
                }
            } else if (characteristicUuid.equals(BleGlucoseMeasurementParser.RECORD_ACCESS_CONTROL_POINT_UUID, true)) {
                requestAllStoredRecords(gatt)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid.toString().equals(BleGlucoseMeasurementParser.RECORD_ACCESS_CONTROL_POINT_UUID, true)) {
                updateState(
                    if (status == BluetoothGatt.GATT_SUCCESS) "Bağlı; geçmiş ve canlı ölçümler senkronize ediliyor"
                    else "Bağlı; geçmiş senkronizasyonu başlatılamadı ($status)"
                )
            }
        }
    }

    private fun handleCharacteristic(uuid: UUID, value: ByteArray) {
        if (!uuid.toString().equals(BleGlucoseMeasurementParser.GLUCOSE_MEASUREMENT_UUID, true)) return
        val parsed = runCatching { BleGlucoseMeasurementParser.parse(value) }
            .onFailure { updateState("Geçersiz şeker paketi: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return
        val mgDl = parsed.valueMgDl ?: return
        if (mgDl !in BloodGlucoseReading.MIN_PLAUSIBLE_MG_DL..BloodGlucoseReading.MAX_PLAUSIBLE_MG_DL) return

        val hint = GlucoseScheduleEngineBridge.matchCheckpoint(this, parsed.measuredAtMs)
        val reading = BloodGlucoseReading(
            measuredAtMs = parsed.measuredAtMs,
            valueMgDl = mgDl,
            ownership = BloodGlucoseReading.Ownership.UNCONFIRMED,
            source = BloodGlucoseReading.Source.ACCU_CHEK_INSTANT,
            deviceId = AccuChekStatusStore.savedAddress(this),
            sequenceNumber = parsed.sequenceNumber,
            checkpointHint = hint,
            context = hint.toMeasurementContext(),
            importedAtMs = System.currentTimeMillis()
        )
        val inserted = BloodGlucoseStore.addIfAbsent(this, reading)
        if (!inserted) return

        val old = AccuChekStatusStore.load(this)
        AccuChekStatusStore.save(
            this,
            old.copy(
                state = "Bağlı; yeni ölçüm doğrulama bekliyor",
                deviceName = currentName,
                lastReadingAtMs = parsed.measuredAtMs,
                lastValueMgDl = mgDl,
                pendingOwnershipCount = BloodGlucoseStore.pendingOwnership(this).size
            )
        )
    }

    private fun enableCcc(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, indicate: Boolean) {
        if (!permissionsReady()) return
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            updateState("BLE bildirimi açılamadı")
            return
        }
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: run {
            updateState("CCC descriptor bulunamadı")
            return
        }
        val value = if (indicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            run {
                descriptor.value = value
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun requestAllStoredRecords(gatt: BluetoothGatt) {
        if (!permissionsReady()) return
        val service = gatt.getService(UUID.fromString(BleGlucoseMeasurementParser.GLUCOSE_SERVICE_UUID)) ?: return
        val racp = service.getCharacteristic(UUID.fromString(BleGlucoseMeasurementParser.RECORD_ACCESS_CONTROL_POINT_UUID)) ?: return
        val command = byteArrayOf(0x01, 0x01) // Report Stored Records / All records
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(racp, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            run {
                racp.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                racp.value = command
                gatt.writeCharacteristic(racp)
            }
        }
    }

    private fun disconnectGatt() {
        val old = gatt ?: return
        gatt = null
        if (permissionsReady()) runCatching { old.disconnect() }
        runCatching { old.close() }
    }

    private fun permissionsReady(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun looksLikeAccuChek(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized.contains("accu-chek") || normalized.contains("accuchek") || normalized.contains("instant")
    }

    private fun updateState(state: String) {
        val old = AccuChekStatusStore.load(this)
        AccuChekStatusStore.save(this, old.copy(state = state, deviceName = currentName))
    }

    private fun com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint?.toMeasurementContext(): BloodGlucoseReading.MeasurementContext = when (this) {
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.MORNING_FASTING -> BloodGlucoseReading.MeasurementContext.FASTING
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL,
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL -> BloodGlucoseReading.MeasurementContext.POST_MEAL
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.BEDTIME -> BloodGlucoseReading.MeasurementContext.BEDTIME
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.MIDDAY -> BloodGlucoseReading.MeasurementContext.OTHER
        null -> BloodGlucoseReading.MeasurementContext.UNKNOWN
    }

    companion object {
        const val ACTION_RESCAN = "com.skhealth.guardian.mobile.ACCU_CHEK_RESCAN"
        const val ACTION_STOP = "com.skhealth.guardian.mobile.ACCU_CHEK_STOP"
        private const val CHANNEL = "accu_chek_ble"
        private const val NOTIFICATION_ID = 2402
        private const val SCAN_WINDOW_MS = 20_000L
        private const val RESCAN_DELAY_MS = 10_000L
        private const val RECONNECT_DELAY_MS = 5_000L
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

/** Keeps schedule matching out of the BLE transport layer. */
private object GlucoseScheduleEngineBridge {
    fun matchCheckpoint(context: android.content.Context, measuredAtMs: Long): com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint? {
        val planned = GlucoseScheduleCoordinator.plannedCheckpoints(context)
        return com.skhealth.guardian.shared.GlucoseScheduleEngine.matchReading(measuredAtMs, planned)?.checkpoint
    }
}
