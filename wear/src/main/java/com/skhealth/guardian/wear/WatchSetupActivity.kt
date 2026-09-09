package com.skhealth.guardian.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WatchSetupActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun render() {
        val compact = resources.configuration.screenWidthDp < 220 || resources.configuration.fontScale >= 1.25f
        val sidePadding = if (compact) 18 else 24
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(sidePadding, 20, sidePadding, 32)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            addView(root)
        })

        root.addView(TextView(this).apply {
            text = "Health Guardian"
            textSize = if (compact) 19f else 22f
            gravity = Gravity.CENTER
            maxLines = 2
            setTypeface(typeface, Typeface.BOLD)
        })
        summary = TextView(this).apply {
            textSize = if (compact) 17f else 19f
            gravity = Gravity.CENTER
            maxLines = 2
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 12, 0, 8)
        }
        root.addView(summary)
        root.addView(TextView(this).apply {
            text = "SpO₂ ve nabız izleme • kritik durumda saat alarmı + telefon bildirimi"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(6, 0, 6, 14)
        })
        status = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 2, 0, 12)
        }
        root.addView(status)
        root.addView(actionButton("İzinleri tamamla") { requestSensorPermissions() })
        root.addView(actionButton("İzlemeyi başlat") { startMonitoringIfReady() })
        refreshStatus()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        minHeight = dp(48)
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun requestSensorPermissions() {
        val permissions = mutableListOf(PERM_READ_HEART_RATE, PERM_READ_OXYGEN_SATURATION)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filterNot(::has)
        if (missing.isEmpty()) requestBackgroundPermission()
        else ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_SENSORS)
    }

    private fun requestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= 36 && !has(PERM_READ_HEALTH_DATA_IN_BACKGROUND)) {
            ActivityCompat.requestPermissions(this, arrayOf(PERM_READ_HEALTH_DATA_IN_BACKGROUND), REQ_BACKGROUND)
        } else startMonitoringIfReady()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_SENSORS -> if (hasSensorPermissions()) requestBackgroundPermission() else refreshStatus()
            REQ_BACKGROUND -> startMonitoringIfReady()
        }
    }

    private fun startMonitoringIfReady() {
        refreshStatus()
        if (!hasSensorPermissions()) return
        if (Build.VERSION.SDK_INT >= 36 && !has(PERM_READ_HEALTH_DATA_IN_BACKGROUND)) return
        runCatching { ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java)) }
            .onSuccess { summary.text = "✓ İZLEME AKTİF"; status.append("\n✓ İzleme servisi çalışıyor") }
            .onFailure { summary.text = "✗ BAŞLATILAMADI"; status.append("\n✗ İzleme servisi başlatılamadı") }
    }

    private fun refreshStatus() {
        val hr = has(PERM_READ_HEART_RATE)
        val spo2 = has(PERM_READ_OXYGEN_SATURATION)
        val background = Build.VERSION.SDK_INT < 36 || has(PERM_READ_HEALTH_DATA_IN_BACKGROUND)
        val notifications = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
        val okCount = listOf(hr, spo2, background, notifications).count { it }
        summary.text = if (okCount == 4) "✓ SAAT HAZIR" else "○ $okCount/4 HAZIR"
        status.text = buildString {
            append(if (hr) "✓" else "✗").append(" Nabız\n")
            append(if (spo2) "✓" else "✗").append(" SpO₂\n")
            append(if (background) "✓" else "✗").append(" Arka plan\n")
            append(if (notifications) "✓" else "✗").append(" Bildirim")
        }
    }

    private fun hasSensorPermissions() = has(PERM_READ_HEART_RATE) && has(PERM_READ_OXYGEN_SATURATION)
    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_SENSORS = 100
        private const val REQ_BACKGROUND = 101
        private const val PERM_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        private const val PERM_READ_OXYGEN_SATURATION = "android.permission.health.READ_OXYGEN_SATURATION"
        private const val PERM_READ_HEALTH_DATA_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }
}
