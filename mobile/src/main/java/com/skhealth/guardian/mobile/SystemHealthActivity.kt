package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class SystemHealthActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); if (::root.isInitialized) render() }

    private fun render() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        setContentView(ScrollView(this).apply { addView(root) })
        val now = System.currentTimeMillis()
        val stale = AppSettings.load(this).staleDataMs
        val lastReading = MonitoringState.lastReading(this)
        val heartbeat = WatchHeartbeatStore.timestamp(this)
        val watchBattery = WatchHeartbeatStore.battery(this)
        val phoneBattery = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val checks = listOf(
            "SMS izni" to has(Manifest.permission.SEND_SMS),
            "Arama izni" to has(Manifest.permission.CALL_PHONE),
            "Bildirim izni" to (Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)),
            "Bluetooth bağlantısı" to (Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT)),
            "Tam ekran alarm" to canUseFullScreenIntent(),
            "Pil optimizasyonu" to isIgnoringBatteryOptimization(),
            "Watch veri akışı" to (lastReading > 0 && now - lastReading <= stale),
            "Watch heartbeat" to (heartbeat > 0 && now - heartbeat <= 3 * 60_000L)
        )
        val okCount = checks.count { it.second }

        root.addView(TextView(this).apply { text = "Sistem Sağlığı"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply {
            text = if (okCount == checks.size) "SİSTEM HAZIR" else "$okCount/${checks.size} kontrol başarılı"
            textSize = 21f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 18, 0, 6)
        })
        root.addView(TextView(this).apply { text = "Aktif alarm kaynağı: ${SourcePriorityCoordinator.activeSourceLabel(this@SystemHealthActivity, now)}"; textSize = 16f; setPadding(0, 0, 0, 20) })

        checks.forEach { (label, ok) -> root.addView(TextView(this).apply { text = (if (ok) "✓ " else "✗ ") + label; textSize = 17f; setPadding(0, 8, 0, 8) }) }

        root.addView(TextView(this).apply {
            text = "Cihaz durumu\nSaat pili: ${if (watchBattery >= 0) "%$watchBattery" else "bilinmiyor"}\nTelefon pili: %$phoneBattery\nSaat durumu: ${WatchStatusStore.status(this@SystemHealthActivity)}"
            textSize = 17f; setPadding(0, 22, 0, 20)
        })
        root.addView(TextView(this).apply {
            text = "Watch doğrulama\n${SpO2ReliabilityStore.formatted(this@SystemHealthActivity)}"
            textSize = 16f; setPadding(0, 0, 0, 22)
            setOnClickListener { startActivity(Intent(this@SystemHealthActivity, SpO2ReliabilityActivity::class.java)) }
        })

        root.addView(Button(this).apply { text = "Uygulama izinlarını aç"; setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } })
        root.addView(Button(this).apply { text = "Pil optimizasyonunu aç"; setOnClickListener { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } })
        if (Build.VERSION.SDK_INT >= 34) root.addView(Button(this).apply { text = "Tam ekran alarm ayarını aç"; setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) } })
    }

    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun isIgnoringBatteryOptimization() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    private fun canUseFullScreenIntent() = Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
