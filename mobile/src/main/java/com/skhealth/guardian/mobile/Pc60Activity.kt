package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Pc60Activity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBlePermissions()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BLE) render()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        val s = Pc60StatusStore.load(this)
        val last = if (s.lastPacketAt == 0L) "yok" else SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date(s.lastPacketAt))
        val battery = s.batteryLevel?.let { level ->
            when (level) {
                0 -> "0–25%"
                1 -> "25–50%"
                2 -> "50–75%"
                3 -> "75–100%"
                else -> level.toString()
            }
        } ?: "—"

        root.addView(TextView(this).apply { text = "PC-60FW Bluetooth Oksimetre"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = buildString {
                append("Durum: ${s.state}\n")
                append("Cihaz: ${s.deviceName.ifBlank { "—" }}\n")
                append("Adres: ${s.address.ifBlank { "—" }}\n")
                append("SpO₂: ${s.spo2?.let { "%$it" } ?: "—"}\n")
                append("Nabız: ${s.heartRate?.let { "$it bpm" } ?: "—"}\n")
                append("PI: ${s.perfusionIndex?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"}\n")
                append("Pil: $battery\n")
                append("Probe off: ${if (s.probeOff) "EVET" else "hayır"}\n")
                append("Pulse searching: ${if (s.pulseSearching) "EVET" else "hayır"}\n")
                append("Son veri: $last\n")
                append("Paket/örnek sayısı: ${s.packetCount}\n")
                if (s.lastPacketHex.isNotBlank()) append("Ham paket: ${s.lastPacketHex}")
            }
            textSize = 17f
            setPadding(0, 24, 0, 24)
        })
        root.addView(TextView(this).apply {
            text = "Lepu SDK AAR yüklüyse üreticinin RtParam verileri doğrudan Guardian alarm zincirine gider. AAR yoksa ekran ham BLE teşhis modunda çalışır."
        })
        root.addView(Button(this).apply {
            text = "Bağlan / izlemeyi başlat"
            setOnClickListener {
                if (!blePermissionsReady()) {
                    requestBlePermissions()
                } else {
                    ContextCompat.startForegroundService(this@Pc60Activity, Intent(this@Pc60Activity, Pc60BleService::class.java))
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Cihazı yeniden tara"
            setOnClickListener {
                if (!blePermissionsReady()) requestBlePermissions()
                else ContextCompat.startForegroundService(
                    this@Pc60Activity,
                    Intent(this@Pc60Activity, Pc60BleService::class.java).setAction(Pc60BleService.ACTION_RESCAN)
                )
            }
        })
        root.addView(Button(this).apply { text = "Durumu yenile"; setOnClickListener { render() } })
        root.addView(Button(this).apply {
            text = "PC-60FW izlemeyi durdur"
            setOnClickListener { stopService(Intent(this@Pc60Activity, Pc60BleService::class.java)); render() }
        })
    }

    private fun requestBlePermissions() {
        val wanted = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = wanted.filterNot { has(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_BLE)
    }

    private fun blePermissionsReady(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        has(Manifest.permission.BLUETOOTH_SCAN) && has(Manifest.permission.BLUETOOTH_CONNECT)
    } else has(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val REQ_BLE = 30
    }
}
