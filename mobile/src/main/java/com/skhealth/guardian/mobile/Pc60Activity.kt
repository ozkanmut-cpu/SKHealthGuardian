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

        root.addView(TextView(this).apply { text = "PC-60FW Bluetooth Oksimetre"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Durum: ${s.state}\nCihaz: ${s.deviceName.ifBlank { "—" }}\nAdres: ${s.address.ifBlank { "—" }}\nSon BLE paketi: $last\nPaket sayısı: ${s.packetCount}\nSon ham paket: ${s.lastPacketHex.ifBlank { "—" }}"
            textSize = 17f
            setPadding(0, 24, 0, 24)
        })
        root.addView(TextView(this).apply {
            text = "Bu ekranda şu an ham BLE bağlantısını doğruluyoruz. SpO₂ / nabız / PI değerlerini alarm sistemine vermek için üretici PC-60FW veri parserı bir sonraki katmanda etkinleştirilecek."
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
        root.addView(Button(this).apply {
            text = "Durumu yenile"
            setOnClickListener { render() }
        })
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
