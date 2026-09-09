package com.skhealth.guardian.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WatchSetupActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var stateText: TextView
    private lateinit var valuesText: TextView
    private lateinit var freshnessText: TextView
    private lateinit var primaryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::stateText.isInitialized) refresh()
    }

    private fun render() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val compact = resources.configuration.screenWidthDp < 220 || resources.configuration.fontScale >= 1.25f
        val pad = if (compact) 14 else 20
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, 18, pad, 28)
            setBackgroundColor(Color.BLACK)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.BLACK)
            addView(root)
        })

        root.addView(TextView(this).apply {
            text = "SK Guardian"
            setTextColor(Color.WHITE)
            textSize = if (compact) 18f else 20f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        })

        stateText = TextView(this).apply {
            textSize = if (compact) 20f else 22f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }
        root.addView(stateText)

        valuesText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = if (compact) 25f else 29f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8, 0, 4)
        }
        root.addView(valuesText)

        freshnessText = TextView(this).apply {
            setTextColor(Color.rgb(180, 184, 190))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        root.addView(freshnessText)

        primaryButton = Button(this).apply {
            minHeight = dp(50)
            isAllCaps = false
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(28, 116, 210), 28f)
            setOnClickListener { onPrimaryAction() }
        }
        root.addView(primaryButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "Ayarlar telefondan yönetilir"
            setTextColor(Color.rgb(130, 134, 142))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        })
        refresh()
    }

    private fun onPrimaryAction() {
        if (!hasSensorPermissions() || (Build.VERSION.SDK_INT >= 36 && !has(PERM_READ_HEALTH_DATA_IN_BACKGROUND))) {
            requestSensorPermissions()
            return
        }
        startMonitoringIfReady()
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_MEASURE_NOW)
            )
        }
        primaryButton.text = "Ölçüm başlatıldı"
        primaryButton.isEnabled = false
        primaryButton.postDelayed({ primaryButton.isEnabled = true; refresh() }, 5000)
    }

    private fun refresh() {
        val permissionsOk = hasSensorPermissions() && (Build.VERSION.SDK_INT < 36 || has(PERM_READ_HEALTH_DATA_IN_BACKGROUND))
        val status = WearStatusStore.load(this)
        val now = System.currentTimeMillis()
        val age = if (status.lastReadingAt > 0) (now - status.lastReadingAt).coerceAtLeast(0) else Long.MAX_VALUE

        if (!permissionsOk) {
            stateText.text = "○ Kurulum gerekli"
            stateText.setTextColor(Color.rgb(255, 190, 70))
            valuesText.text = "SpO₂ —   ♥ —"
            freshnessText.text = "Sensör izinlerini tamamla"
            primaryButton.text = "İzinleri tamamla"
            return
        }

        startMonitoringIfReady()
        stateText.text = if (age == Long.MAX_VALUE || age > 15 * 60_000L) "○ Veri bekleniyor" else "● İzleme aktif"
        stateText.setTextColor(if (age == Long.MAX_VALUE || age > 15 * 60_000L) Color.rgb(255, 190, 70) else Color.rgb(58, 214, 126))
        valuesText.text = "SpO₂ ${status.spo2?.let { "$it%" } ?: "—"}   ♥ ${status.heartRate?.toString() ?: "—"}"
        freshnessText.text = when {
            age == Long.MAX_VALUE -> "Henüz ölçüm yok"
            age < 5_000L -> "Son ölçüm: şimdi"
            age < 60_000L -> "Son ölçüm: ${age / 1000} sn önce"
            else -> "Son ölçüm: ${age / 60_000} dk önce"
        }
        primaryButton.text = "Şimdi ölç"
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
            REQ_SENSORS -> if (hasSensorPermissions()) requestBackgroundPermission() else refresh()
            REQ_BACKGROUND -> { startMonitoringIfReady(); refresh() }
        }
    }

    private fun startMonitoringIfReady() {
        if (!hasSensorPermissions()) return
        if (Build.VERSION.SDK_INT >= 36 && !has(PERM_READ_HEALTH_DATA_IN_BACKGROUND)) return
        runCatching { ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java)) }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
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
