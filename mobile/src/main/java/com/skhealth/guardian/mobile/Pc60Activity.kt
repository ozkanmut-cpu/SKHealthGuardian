package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Pc60Activity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); requestBlePermissions(); render() }
    override fun onResume() { super.onResume(); if (::root.isInitialized) render() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQ_BLE) render() }

    private fun render() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        setContentView(ScrollView(this).apply { addView(root) })
        val s = Pc60StatusStore.load(this)
        val now = System.currentTimeMillis()
        val age = if (s.lastPacketAt > 0) (now - s.lastPacketAt).coerceAtLeast(0) else Long.MAX_VALUE
        val fresh = age <= 10_000L
        val signal = when {
            s.probeOff -> "PARMAK ALGILANMIYOR"
            s.pulseSearching -> "NABIZ ARANIYOR"
            !fresh || s.spo2 !in 1..100 -> "ÖLÇÜM BEKLENİYOR"
            else -> "ÖLÇÜM GÜVENİLİR"
        }
        val last = if (s.lastPacketAt == 0L) "yok" else SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date(s.lastPacketAt))
        val battery = s.batteryLevel?.let { when (it) { 0 -> "0–25%"; 1 -> "25–50%"; 2 -> "50–75%"; 3 -> "75–100%"; else -> it.toString() } } ?: "—"

        root.addView(TextView(this).apply { text = "PC-60FW"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = signal; textSize = 21f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 18, 0, 4) })
        root.addView(TextView(this).apply { text = "${s.state} • son veri ${ageText(age)}"; textSize = 15f; setPadding(0, 0, 0, 20) })

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        metrics.addView(metric("SpO₂", s.spo2?.let { "$it%" } ?: "—"), LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(metric("Nabız", s.heartRate?.let { "$it bpm" } ?: "—"), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(metrics)
        root.addView(TextView(this).apply {
            text = "PI: ${s.perfusionIndex?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"} • ${piLabel(s.perfusionIndex)}\nPil: $battery • Son paket: $last\nCihaz: ${s.deviceName.ifBlank { "—" }}"
            textSize = 16f
            setPadding(0, 22, 0, 22)
        })
        root.addView(TextView(this).apply {
            text = if (SourcePriorityCoordinator.isPc60Authoritative(this@Pc60Activity)) "PC-60FW şu anda ana alarm ölçüm kaynağıdır." else "Geçerli PC-60FW verisi gelene kadar Galaxy Watch alarm kaynağı olarak devam eder."
            textSize = 15f
            setPadding(0, 0, 0, 20)
        })
        root.addView(Button(this).apply { text = "Bağlan / izlemeyi başlat"; setOnClickListener { if (!blePermissionsReady()) requestBlePermissions() else ContextCompat.startForegroundService(this@Pc60Activity, Intent(this@Pc60Activity, Pc60BleService::class.java)) } })
        root.addView(Button(this).apply { text = "Cihazı yeniden tara"; setOnClickListener { if (!blePermissionsReady()) requestBlePermissions() else ContextCompat.startForegroundService(this@Pc60Activity, Intent(this@Pc60Activity, Pc60BleService::class.java).setAction(Pc60BleService.ACTION_RESCAN)) } })
        root.addView(Button(this).apply { text = "Durumu yenile"; setOnClickListener { render() } })
        root.addView(Button(this).apply { text = "İzlemeyi durdur"; setOnClickListener { stopService(Intent(this@Pc60Activity, Pc60BleService::class.java)); render() } })
    }

    private fun metric(title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(12, 22, 12, 22)
        addView(TextView(this@Pc60Activity).apply { text = title; textSize = 16f; gravity = Gravity.CENTER })
        addView(TextView(this@Pc60Activity).apply { text = value; textSize = 34f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
    }

    private fun piLabel(pi: Double?): String = when { pi == null || pi <= 0.0 -> "sinyal yok"; pi < 0.5 -> "zayıf perfüzyon"; pi < 1.0 -> "düşük perfüzyon"; else -> "uygun sinyal" }
    private fun ageText(age: Long) = when { age == Long.MAX_VALUE -> "yok"; age < 5_000 -> "şimdi"; age < 60_000 -> "${age / 1000} sn önce"; else -> "${age / 60_000} dk önce" }

    private fun requestBlePermissions() {
        val wanted = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = wanted.filterNot { has(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_BLE)
    }
    private fun blePermissionsReady() = if (Build.VERSION.SDK_INT >= 31) has(Manifest.permission.BLUETOOTH_SCAN) && has(Manifest.permission.BLUETOOTH_CONNECT) else has(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    companion object { private const val REQ_BLE = 30 }
}
