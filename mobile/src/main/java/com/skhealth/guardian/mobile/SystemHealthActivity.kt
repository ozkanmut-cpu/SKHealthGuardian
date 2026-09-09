package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

        val last = MonitoringState.lastReading(this)
        addStatus("Saatten veri geliyor", last > 0 && System.currentTimeMillis() - last <= AppSettings.load(this).staleDataMs)
        root.addView(TextView(this).apply { text = "Saat self-test: ${WatchStatusStore.status(this@SystemHealthActivity)}" })

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
