package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat

class SystemHealthActivity : Activity() {
    private lateinit var root: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); UiStyle.applyBars(this); render() }
    override fun onResume() { super.onResume(); if (::root.isInitialized) render() }

    private fun render() {
        root = UiStyle.page(this)
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
        root.addView(UiStyle.title(this, "Sistem sağlığı"))
        root.addView(UiStyle.subtitle(this, "İzinlar, bağlantılar ve arka plan çalışma koşullarını tek ekrandan kontrol et.".replace("İzinlar", "İzinler")))

        val summary = UiStyle.card(this)
        val ready = okCount == checks.size
        summary.background = UiStyle.rounded(if (ready) 0xFF123222.toInt() else 0xFF332719.toInt(), context = this)
        summary.addView(UiStyle.text(this, if (ready) "✓ SİSTEM HAZIR" else "⚠ $okCount/${checks.size} kontrol başarılı", 20f, if (ready) UiStyle.GREEN else UiStyle.AMBER, true))
        summary.addView(UiStyle.text(this, "Aktif alarm kaynağı: ${SourcePriorityCoordinator.activeSourceLabel(this, now)}", 15f, UiStyle.TEXT).apply { setPadding(0, UiStyle.dp(this@SystemHealthActivity, 8), 0, 0) })
        root.addView(summary)

        val checksCard = UiStyle.card(this)
        checksCard.addView(UiStyle.text(this, "Kontroller", 19f, UiStyle.TEXT, true))
        checks.forEach { (label, ok) ->
            checksCard.addView(UiStyle.text(this, "${if (ok) "✓" else "✗"} $label", 16f, if (ok) UiStyle.GREEN else UiStyle.RED).apply { setPadding(0, UiStyle.dp(this@SystemHealthActivity, 9), 0, 0) })
        }
        root.addView(checksCard, UiStyle.sectionParams(this))

        val device = UiStyle.card(this)
        device.addView(UiStyle.text(this, "Cihaz durumu", 19f, UiStyle.TEXT, true))
        device.addView(UiStyle.text(this, "Saat pili: ${if (watchBattery >= 0) "%$watchBattery" else "bilinmiyor"}\nTelefon pili: %$phoneBattery\nSaat durumu: ${WatchStatusStore.status(this)}", 16f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@SystemHealthActivity, 8), 0, 0) })
        root.addView(device, UiStyle.sectionParams(this))

        val reliability = UiStyle.card(this)
        reliability.addView(UiStyle.text(this, "Watch doğrulama", 19f, UiStyle.TEXT, true))
        reliability.addView(UiStyle.text(this, SpO2ReliabilityStore.formatted(this), 15f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@SystemHealthActivity, 8), 0, 0) })
        reliability.setOnClickListener { startActivity(Intent(this, SpO2ReliabilityActivity::class.java)) }
        root.addView(reliability, UiStyle.sectionParams(this))

        root.addView(UiStyle.button(this, "Uygulama izinlerini aç", false).apply { setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } })
        root.addView(UiStyle.button(this, "Pil optimizasyonu ayarını aç", false).apply { setOnClickListener { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } })
        if (Build.VERSION.SDK_INT >= 34) root.addView(UiStyle.button(this, "Tam ekran alarm ayarını aç", false).apply { setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) } })
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
    }
    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun isIgnoringBatteryOptimization() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    private fun canUseFullScreenIntent() = Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
