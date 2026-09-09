package com.skhealth.guardian.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.health.connect.HealthPermissions
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WatchSetupActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "SK Health Guardian"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "İlk kurulumda nabız, SpO₂ ve arka plan sağlık erişimine izin ver. Bu izinler olmadan ekran kapalıyken izleme güvenilir çalışmaz."
            textSize = 15f
            setPadding(0, 16, 0, 16)
        })
        status = TextView(this).apply { textSize = 15f }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "İzinleri ver ve izlemeyi başlat"
            setOnClickListener { requestSensorPermissions() }
        })
        root.addView(Button(this).apply {
            text = "İzlemeyi şimdi başlat"
            setOnClickListener { startMonitoringIfReady() }
        })
        setContentView(root)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun requestSensorPermissions() {
        val permissions = mutableListOf(
            HealthPermissions.READ_HEART_RATE,
            HealthPermissions.READ_OXYGEN_SATURATION
        )
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filterNot(::has)
        if (missing.isEmpty()) requestBackgroundPermission()
        else ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_SENSORS)
    }

    private fun requestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= 36 && !has(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND),
                REQ_BACKGROUND
            )
        } else {
            startMonitoringIfReady()
        }
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
        if (Build.VERSION.SDK_INT >= 36 && !has(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)) return
        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
        status.append("\n✓ İzleme servisi başlatıldı")
    }

    private fun refreshStatus() {
        val hr = has(HealthPermissions.READ_HEART_RATE)
        val spo2 = has(HealthPermissions.READ_OXYGEN_SATURATION)
        val background = Build.VERSION.SDK_INT < 36 || has(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)
        val notifications = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
        status.text = buildString {
            append(if (hr) "✓" else "✗").append(" Nabız izni\n")
            append(if (spo2) "✓" else "✗").append(" SpO₂ izni\n")
            append(if (background) "✓" else "✗").append(" Arka plan sağlık izni\n")
            append(if (notifications) "✓" else "✗").append(" Bildirim izni")
        }
    }

    private fun hasSensorPermissions(): Boolean =
        has(HealthPermissions.READ_HEART_RATE) && has(HealthPermissions.READ_OXYGEN_SATURATION)

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val REQ_SENSORS = 100
        private const val REQ_BACKGROUND = 101
    }
}
