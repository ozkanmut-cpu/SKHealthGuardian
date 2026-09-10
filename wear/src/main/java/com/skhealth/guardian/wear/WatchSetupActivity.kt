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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class WatchSetupActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var stateText: TextView
    private lateinit var stateIcon: WearIconView
    private lateinit var spo2Value: TextView
    private lateinit var heartRateValue: TextView
    private lateinit var freshnessText: TextView
    private lateinit var primaryButton: LinearLayout
    private lateinit var primaryButtonText: TextView
    private lateinit var primaryButtonIcon: WearIconView

    private val bg = Color.rgb(13, 17, 23)
    private val surface = Color.rgb(23, 29, 36)
    private val surface2 = Color.rgb(32, 40, 50)
    private val text = Color.rgb(243, 246, 249)
    private val muted = Color.rgb(151, 162, 174)
    private val green = Color.rgb(53, 208, 127)
    private val amber = Color.rgb(255, 183, 77)
    private val blue = Color.rgb(66, 165, 245)
    private val red = Color.rgb(255, 92, 92)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); if (::stateText.isInitialized) refresh() }

    private fun render() {
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val compact = resources.configuration.screenWidthDp < 220 || resources.configuration.fontScale >= 1.25f
        val horizontal = if (compact) 12 else 18
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(horizontal), dp(12), dp(horizontal), dp(28))
            setBackgroundColor(bg)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(bg)
            addView(root)
        })

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(wearIcon(WearIcon.WATCH, blue, if (compact) 24 else 28), LinearLayout.LayoutParams(dp(if (compact) 28 else 32), dp(if (compact) 28 else 32)))
            addView(label("SK Guardian", if (compact) 17f else 19f, text, true).apply { setPadding(dp(7), 0, 0, 0) })
        }
        root.addView(titleRow)
        root.addView(label("Sağlık izleme", 11f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(8)) })

        val statusCard = card().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        stateIcon = wearIcon(WearIcon.REFRESH, amber, 22)
        statusRow.addView(stateIcon, LinearLayout.LayoutParams(dp(26), dp(26)))
        stateText = label("", if (compact) 17f else 19f, amber, true).apply { setPadding(dp(7), 0, 0, 0) }
        statusRow.addView(stateText)
        statusCard.addView(statusRow)
        freshnessText = label("", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
        statusCard.addView(freshnessText)
        root.addView(statusCard, sectionParams(5))

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        val spo2Card = metricCard("SpO₂", blue, WearIcon.SPO2).also { spo2Value = it.second }
        val hrCard = metricCard("Nabız", red, WearIcon.HEART, "bpm").also { heartRateValue = it.second }
        metrics.addView(spo2Card.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        metrics.addView(hrCard.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        root.addView(metrics, sectionParams(5))

        primaryButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            background = rounded(blue, 24f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            primaryButtonIcon = wearIcon(WearIcon.REFRESH, Color.WHITE, 21)
            addView(primaryButtonIcon, LinearLayout.LayoutParams(dp(24), dp(24)))
            primaryButtonText = label("Şimdi ölç", if (compact) 14f else 15f, Color.WHITE, true).apply { setPadding(dp(8), 0, 0, 0) }
            addView(primaryButtonText)
            setOnClickListener { onPrimaryAction() }
        }
        root.addView(primaryButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5) })

        val settingsHint = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
            addView(wearIcon(WearIcon.CHEVRON_RIGHT, muted, 14), LinearLayout.LayoutParams(dp(16), dp(16)))
            addView(label("Ayarlar telefondan yönetilir", 10.5f, muted).apply { setPadding(dp(4), 0, 0, 0) })
        }
        root.addView(settingsHint)
        refresh()
    }

    private fun metricCard(title: String, accent: Int, icon: WearIcon, unit: String = "%"): Pair<LinearLayout, TextView> {
        val value = label("—", if (resources.configuration.screenWidthDp < 220) 27f else 31f, accent, true).apply { gravity = Gravity.CENTER }
        val box = card(surface2).apply {
            gravity = Gravity.CENTER
            val heading = LinearLayout(this@WatchSetupActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(wearIcon(icon, accent, 17), LinearLayout.LayoutParams(dp(19), dp(19)))
                addView(label(title, 12f, muted).apply { setPadding(dp(4), 0, 0, 0) })
            }
            addView(heading)
            addView(value)
            addView(label(unit, 10f, muted).apply { gravity = Gravity.CENTER })
        }
        return box to value
    }

    private fun onPrimaryAction() {
        if (!hasSensorPermissions() || (Build.VERSION.SDK_INT >= 36 && !has(PERM_READ_HEALTH_DATA_IN_BACKGROUND))) {
            requestSensorPermissions(); return
        }
        startMonitoringIfReady()
        runCatching { ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_MEASURE_NOW)) }
        primaryButtonText.text = "Ölçüm başlatıldı"
        primaryButton.isEnabled = false
        primaryButton.alpha = 0.72f
        primaryButton.postDelayed({ primaryButton.isEnabled = true; primaryButton.alpha = 1f; refresh() }, 5000)
    }

    private fun refresh() {
        val permissionsOk = hasSensorPermissions() && (Build.VERSION.SDK_INT < 36 || has(PERM_READ_HEALTH_DATA_IN_BACKGROUND))
        val status = WearStatusStore.load(this)
        val now = System.currentTimeMillis()
        val age = if (status.lastReadingAt > 0) (now - status.lastReadingAt).coerceAtLeast(0) else Long.MAX_VALUE
        if (!permissionsOk) {
            stateText.text = "Kurulum gerekli"
            stateText.setTextColor(amber)
            stateIcon.icon = WearIcon.ALERT
            stateIcon.tint = amber
            stateIcon.invalidate()
            spo2Value.text = "—"; heartRateValue.text = "—"
            freshnessText.text = "Sensör izinlerini tamamla"
            primaryButtonText.text = "İzinleri tamamla"
            primaryButtonIcon.icon = WearIcon.ALERT
            primaryButtonIcon.invalidate()
            return
        }
        startMonitoringIfReady()
        val fresh = age != Long.MAX_VALUE && age <= 15 * 60_000L
        stateText.text = if (fresh) "İzleme aktif" else "Veri bekleniyor"
        stateText.setTextColor(if (fresh) green else amber)
        stateIcon.icon = if (fresh) WearIcon.STATUS_OK else WearIcon.REFRESH
        stateIcon.tint = if (fresh) green else amber
        stateIcon.invalidate()
        spo2Value.text = status.spo2?.toString() ?: "—"
        heartRateValue.text = status.heartRate?.toString() ?: "—"
        freshnessText.text = when {
            age == Long.MAX_VALUE -> "Henüz ölçüm yok"
            age < 5_000L -> "Son ölçüm: şimdi"
            age < 60_000L -> "Son ölçüm: ${age / 1000} sn önce"
            else -> "Son ölçüm: ${age / 60_000} dk önce"
        }
        primaryButtonText.text = "Şimdi ölç"
        primaryButtonIcon.icon = WearIcon.REFRESH
        primaryButtonIcon.invalidate()
    }

    private fun wearIcon(icon: WearIcon, tint: Int, size: Int) = WearIconView(this, icon, tint).apply {
        contentDescription = icon.name.lowercase().replace('_', ' ')
        minimumWidth = dp(size)
        minimumHeight = dp(size)
    }

    private fun card(color: Int = surface) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(color, 18f)
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun sectionParams(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun requestSensorPermissions() {
        val permissions = mutableListOf(PERM_READ_HEART_RATE, PERM_READ_OXYGEN_SATURATION)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filterNot(::has)
        if (missing.isEmpty()) requestBackgroundPermission() else ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_SENSORS)
    }
    private fun requestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= 36 && !has(PERM_READ_HEALTH_DATA_IN_BACKGROUND)) ActivityCompat.requestPermissions(this, arrayOf(PERM_READ_HEALTH_DATA_IN_BACKGROUND), REQ_BACKGROUND)
        else startMonitoringIfReady()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) { REQ_SENSORS -> if (hasSensorPermissions()) requestBackgroundPermission() else refresh(); REQ_BACKGROUND -> { startMonitoringIfReady(); refresh() } }
    }
    private fun startMonitoringIfReady() {
        if (!hasSensorPermissions()) return
        if (Build.VERSION.SDK_INT >= 36 && !has(PERM_READ_HEALTH_DATA_IN_BACKGROUND)) return
        runCatching { ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java)) }
    }
    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(color); cornerRadius = dp(radiusDp.toInt()).toFloat() }
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
