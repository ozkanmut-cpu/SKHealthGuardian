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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class SystemHealthActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply { text = "Sistem sağlık kontrolü"; textSize = 24f })
        addStatus("SMS izni", has(Manifest.permission.SEND_SMS))
        addStatus("Arama izni", has(Manifest.permission.CALL_PHONE))
        addStatus("Bildirim izni", Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS))
        addStatus("Bluetooth bağlantı izni", Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT))
        addStatus("Tam ekran alarm yetkisi", canUseFullScreenIntent())
        addStatus("Pil optimizasyonu dışında", isIgnoringBatteryOptimization())

        val now = System.currentTimeMillis()
        val stale = AppSettings.load(this).staleDataMs
        val lastReading = MonitoringState.lastReading(this)
        val heartbeat = WatchHeartbeatStore.timestamp(this)
        val watchBattery = WatchHeartbeatStore.battery(this)
        val phoneBattery = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        addStatus("Saatten ölçüm verisi geliyor", lastReading > 0 && now - lastReading <= stale)
        addStatus("Saat servisi heartbeat aktif", heartbeat > 0 && now - heartbeat <= 3 * 60_000L)
        root.addView(TextView(this).apply { text = "Saat pili: ${if (watchBattery >= 0) "%$watchBattery" else "bilinmiyor"}"; textSize = 18f })
        root.addView(TextView(this).apply { text = "Telefon pili: %$phoneBattery"; textSize = 18f })
        root.addView(TextView(this).apply { text = "Saat durumu/ACK: ${WatchStatusStore.status(this@SystemHealthActivity)}"; textSize = 17f })

        root.addView(TextView(this).apply {
            text = "Aktif alarm kaynağı: ${SourcePriorityCoordinator.activeSourceLabel(this@SystemHealthActivity, now)}"
            textSize = 19f
            setPadding(0, 24, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Saat ↔ PC-60FW SpO₂ doğrulaması\n${SpO2ReliabilityStore.formatted(this@SystemHealthActivity)}"
            textSize = 17f
            setPadding(0, 8, 0, 24)
        })

        root.addView(Button(this).apply {
            text = "Uygulama ayarlarını aç"
            setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        })
        root.addView(Button(this).apply {
            text = "Pil optimizasyon ayarını aç"
            setOnClickListener { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        })
        if (Build.VERSION.SDK_INT >= 34) {
            root.addView(Button(this).apply {
                text = "Tam ekran alarm ayarını aç"
                setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) }
            })
        }
    }

    private fun addStatus(label: String, ok: Boolean) {
        root.addView(TextView(this).apply {
            text = (if (ok) "✓ " else "✗ ") + label
            textSize = 18f
            setPadding(0, 12, 0, 12)
        })
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun isIgnoringBatteryOptimization(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
}
