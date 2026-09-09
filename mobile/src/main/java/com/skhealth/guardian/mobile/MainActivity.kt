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

class MainActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        startWatchdogIfReady()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) startWatchdogIfReady()
    }

    private fun startWatchdogIfReady() {
        val bluetoothReady = Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT)
        if (bluetoothReady) runCatching { ContextCompat.startForegroundService(this, Intent(this, WatchdogService::class.java)) }
    }

    private fun render() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        setContentView(ScrollView(this).apply { addView(root) })
        val now = System.currentTimeMillis()
        val recent = HistoryStore.recent(this, 100).asReversed().firstOrNull { it.valid && now - it.timestampMs in 0..30 * 60_000L }
        val pc60 = Pc60StatusStore.load(this)
        val pcActive = SourcePriorityCoordinator.isPc60Authoritative(this, now)
        val spo2 = if (pcActive) pc60.spo2 else recent?.spo2
        val hr = if (pcActive) pc60.heartRate ?: recent?.heartRate else recent?.heartRate
        val source = SourcePriorityCoordinator.activeSourceLabel(this, now)
        val sourceTs = if (pcActive) pc60.lastPacketAt else recent?.timestampMs ?: MonitoringState.lastReading(this)
        val age = if (sourceTs > 0) (now - sourceTs).coerceAtLeast(0) else Long.MAX_VALUE
        val cfg = AppSettings.load(this)
        val status = when {
            spo2 != null && spo2 < cfg.spo2CriticalImmediate -> "ALARM"
            spo2 != null && spo2 < cfg.spo2LowThreshold -> "DİKKAT"
            hr != null && hr > cfg.heartRateHighThreshold -> "DİKKAT"
            sourceTs == 0L || age > cfg.staleDataMs -> "VERİ BEKLENİYOR"
            else -> "NORMAL"
        }

        root.addView(TextView(this).apply { text = "SK Health Guardian"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply {
            text = status
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 18, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "Aktif kaynak: $source • ${ageText(age)}"
            textSize = 15f
            setPadding(0, 0, 0, 22)
        })

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        metrics.addView(metric("SpO₂", spo2?.let { "$it%" } ?: "—", spo2Label(spo2, cfg.spo2LowThreshold)), LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(metric("Nabız", hr?.let { "$it bpm" } ?: "—", hrLabel(hr, cfg.heartRateHighThreshold)), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(metrics)

        val reliability = SpO2ReliabilityStore.summary(this)
        root.addView(TextView(this).apply {
            text = if (reliability.count == 0) "Watch doğrulama: henüz eşleşme yok" else "Watch uyumu: ${reliability.label} • ${reliability.count} karşılaştırma • ort. fark ${String.format(Locale.US, "%.1f", reliability.meanAbsoluteError ?: 0.0)} puan"
            textSize = 15f
            setPadding(0, 22, 0, 18)
            setOnClickListener { startActivity(Intent(this@MainActivity, SpO2ReliabilityActivity::class.java)) }
        })

        root.addView(Button(this).apply { text = "Cihazlar / PC-60FW"; setOnClickListener { startActivity(Intent(this@MainActivity, Pc60Activity::class.java)) } })
        root.addView(Button(this).apply { text = "Ölçüm geçmişi ve grafikler"; setOnClickListener { startActivity(Intent(this@MainActivity, HistoryChartActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Ölçüm ve alarm ayarları"; setOnClickListener { startActivity(Intent(this@MainActivity, MeasurementSettingsActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Acil durum kişileri"; setOnClickListener { startActivity(Intent(this@MainActivity, ContactsActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Kurulum / QA sihirbazı"; setOnClickListener { startActivity(Intent(this@MainActivity, SystemTestActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Sistem sağlık kontrolü"; setOnClickListener { startActivity(Intent(this@MainActivity, SystemHealthActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Alarm olay geçmişi"; setOnClickListener { startActivity(Intent(this@MainActivity, AlarmTimelineActivity::class.java)) } })
        root.addView(Button(this).apply { text = "SMS / arama kayıtları"; setOnClickListener { startActivity(Intent(this@MainActivity, DeliveryLogActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Yedekle / dışa aktar / geri yükle"; setOnClickListener { startActivity(Intent(this@MainActivity, ExportActivity::class.java)) } })
    }

    private fun metric(title: String, value: String, label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(12, 22, 12, 22)
        addView(TextView(this@MainActivity).apply { text = title; textSize = 16f; gravity = Gravity.CENTER })
        addView(TextView(this@MainActivity).apply { text = value; textSize = 34f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
        addView(TextView(this@MainActivity).apply { text = label; textSize = 14f; gravity = Gravity.CENTER })
    }

    private fun spo2Label(value: Int?, low: Int) = when { value == null -> "Veri yok"; value < 80 -> "Kritik"; value < low -> "Düşük"; else -> "Normal" }
    private fun hrLabel(value: Int?, high: Int) = when { value == null -> "Veri yok"; value > high -> "Yüksek"; else -> "Normal" }
    private fun ageText(age: Long): String = when { age == Long.MAX_VALUE -> "veri yok"; age < 5_000 -> "şimdi"; age < 60_000 -> "${age / 1000} sn önce"; else -> "${age / 60_000} dk önce" }

    private fun requestPermissions() {
        val wanted = mutableListOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31) { wanted += Manifest.permission.BLUETOOTH_CONNECT; wanted += Manifest.permission.BLUETOOTH_SCAN }
        val missing = wanted.filterNot { has(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    companion object { private const val REQ_PERMISSIONS = 10 }
}
