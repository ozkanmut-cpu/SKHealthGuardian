package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Pc60Activity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)
        requestBlePermissions()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BLE) render()
    }

    private fun render() {
        root = UiStyle.page(this)
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })

        val s = Pc60StatusStore.load(this)
        val now = System.currentTimeMillis()
        val age = if (s.lastPacketAt > 0) (now - s.lastPacketAt).coerceAtLeast(0) else Long.MAX_VALUE
        val fresh = age <= 10_000L
        val good = fresh && s.spo2 in 1..100 && !s.probeOff && !s.pulseSearching
        val signal = when {
            s.probeOff -> "Parmak algılanmıyor"
            s.pulseSearching -> "Nabız aranıyor"
            !fresh || s.spo2 !in 1..100 -> "Ölçüm bekleniyor"
            else -> "Ölçüm güvenilir"
        }
        val signalColor = if (good) UiStyle.GREEN else UiStyle.AMBER
        val signalIcon = if (good) SkIcon.STATUS_OK else SkIcon.STATUS_WARNING
        val last = if (s.lastPacketAt == 0L) "yok" else SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date(s.lastPacketAt))
        val battery = s.batteryLevel?.let { when (it) { 0 -> "0–25%"; 1 -> "25–50%"; 2 -> "50–75%"; 3 -> "75–100%"; else -> it.toString() } } ?: "—"

        root.addView(UiStyle.detailHeader(this, "PC-60FW", "Parmak oksimetresi • canlı izleme"))

        val statusCard = UiStyle.card(this)
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusRow.addView(UiStyle.icon(this, signalIcon, 28, signalColor, signal), LinearLayout.LayoutParams(UiStyle.dp(this, 36), UiStyle.dp(this, 36)))
        val statusTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusTexts.addView(UiStyle.text(this, signal, 20f, signalColor, true))
        statusTexts.addView(UiStyle.text(this, "${s.state} • son veri ${ageText(age)}", 14f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@Pc60Activity, 5), 0, 0) })
        statusRow.addView(statusTexts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusCard.addView(statusRow)
        root.addView(statusCard, UiStyle.sectionParams(this))

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        metrics.addView(metric(SkIcon.SPO2, "SpO₂", s.spo2?.let { "$it%" } ?: "—", UiStyle.BLUE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = UiStyle.dp(this@Pc60Activity, 6) })
        metrics.addView(metric(SkIcon.HEART, "Nabız", s.heartRate?.toString() ?: "—", UiStyle.RED, "bpm"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiStyle.dp(this@Pc60Activity, 6) })
        root.addView(metrics, UiStyle.sectionParams(this))

        val deviceCard = UiStyle.card(this)
        deviceCard.addView(UiStyle.iconLabel(this, SkIcon.DEVICE_INFO, "Sinyal ve cihaz", UiStyle.BLUE, UiStyle.TEXT, 22, 16f, true))
        val infoRows = listOf(
            Triple(SkIcon.INFO, "PI", "${s.perfusionIndex?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"} • ${piLabel(s.perfusionIndex)}"),
            Triple(SkIcon.BATTERY, "Pil", battery),
            Triple(SkIcon.CLOCK, "Son paket", last),
            Triple(SkIcon.OXIMETER, "Cihaz", s.deviceName.ifBlank { "—" })
        )
        infoRows.forEach { (icon, label, value) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, UiStyle.dp(this@Pc60Activity, 10), 0, 0) }
            row.addView(UiStyle.icon(this, icon, 20, UiStyle.MUTED, label), LinearLayout.LayoutParams(UiStyle.dp(this, 28), UiStyle.dp(this, 28)))
            row.addView(UiStyle.text(this, label, 14f, UiStyle.MUTED), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(UiStyle.text(this, value, 14f, UiStyle.TEXT, true))
            deviceCard.addView(row)
        }
        root.addView(deviceCard, UiStyle.sectionParams(this))

        val authoritative = SourcePriorityCoordinator.isPc60Authoritative(this)
        val sourceCard = UiStyle.card(this).apply { background = UiStyle.rounded(if (authoritative) 0xFF14241C.toInt() else UiStyle.SURFACE, context = this@Pc60Activity) }
        sourceCard.addView(UiStyle.iconLabel(this, if (authoritative) SkIcon.STATUS_OK else SkIcon.WATCH, if (authoritative) "PC-60FW ana alarm kaynağı" else "Galaxy Watch yedek kaynak", if (authoritative) UiStyle.GREEN else UiStyle.AMBER, if (authoritative) UiStyle.GREEN else UiStyle.AMBER, 22, 16f, true))
        sourceCard.addView(UiStyle.text(this, if (authoritative) "Geçerli ve taze PC-60FW ölçümleri alarm kararında kullanılıyor." else "Geçerli PC-60FW verisi gelene kadar Galaxy Watch alarm değerlendirmesine devam eder.", 14f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@Pc60Activity, 7), 0, 0) })
        root.addView(sourceCard, UiStyle.sectionParams(this))

        root.addView(action(SkIcon.LINK, "Bağlan / izlemeyi başlat", "Bluetooth bağlantısını başlat", UiStyle.GREEN) {
            if (!blePermissionsReady()) requestBlePermissions() else {
                Pc60StatusStore.setMonitoringEnabled(this, true)
                ContextCompat.startForegroundService(this, Intent(this, Pc60BleService::class.java))
            }
        }, UiStyle.sectionParams(this))

        root.addView(action(SkIcon.SEARCH, "Cihazı yeniden tara", "PC-60FW için yeni tarama ve BLE tanılama başlat", UiStyle.BLUE) {
            if (!blePermissionsReady()) requestBlePermissions() else {
                Pc60StatusStore.setMonitoringEnabled(this, true)
                ContextCompat.startForegroundService(this, Intent(this, Pc60BleService::class.java).setAction(Pc60BleService.ACTION_RESCAN))
            }
        }, UiStyle.sectionParams(this))

        root.addView(action(SkIcon.REFRESH, "Durumu yenile", "Ekrandaki canlı bilgileri ve tanılamayı güncelle", UiStyle.TEXT) { render() }, UiStyle.sectionParams(this))

        val diagCard = UiStyle.card(this)
        diagCard.addView(UiStyle.iconLabel(this, SkIcon.INFO, "BLE tanılama", UiStyle.BLUE, UiStyle.TEXT, 22, 16f, true))
        diagCard.addView(UiStyle.text(this, "Gerçek cihazın GATT servisleri, karakteristikleri ve bağlantı olayları burada görünür.", 13f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@Pc60Activity, 6), 0, 0) })
        val snapshot = s.gattSnapshot.ifBlank { "GATT servis haritası henüz alınmadı." }
        diagCard.addView(UiStyle.text(this, snapshot, 11.5f, UiStyle.TEXT).apply { setPadding(0, UiStyle.dp(this@Pc60Activity, 10), 0, 0); typeface = android.graphics.Typeface.MONOSPACE })
        val log = s.diagnostics.ifBlank { "Tanılama kaydı henüz yok. 'Cihazı yeniden tara' düğmesine bas." }
        diagCard.addView(UiStyle.text(this, log, 11f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@Pc60Activity, 12), 0, 0); typeface = android.graphics.Typeface.MONOSPACE })
        root.addView(diagCard, UiStyle.sectionParams(this))

        root.addView(action(SkIcon.INFO, "Tanılamayı kopyala", "GATT haritası ve BLE olaylarını panoya kopyala", UiStyle.BLUE) {
            val latest = Pc60StatusStore.load(this)
            val report = buildString {
                appendLine("Orko Takip • PC-60FW BLE tanılama")
                appendLine("Durum: ${latest.state}")
                appendLine("Cihaz: ${latest.deviceName.ifBlank { "—" }}")
                appendLine("Paket: ${latest.packetCount}; son=${latest.lastPacketHex.ifBlank { "—" }}")
                appendLine()
                appendLine("GATT:")
                appendLine(latest.gattSnapshot.ifBlank { "—" })
                appendLine()
                appendLine("Olaylar:")
                append(latest.diagnostics.ifBlank { "—" })
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("PC-60FW BLE tanılama", report))
            Toast.makeText(this, "BLE tanılama panoya kopyalandı", Toast.LENGTH_SHORT).show()
        }, UiStyle.sectionParams(this))

        root.addView(action(SkIcon.LINK_OFF, "İzlemeyi durdur", "PC-60FW servisini kapat", UiStyle.RED) {
            Pc60StatusStore.setMonitoringEnabled(this, false)
            stopService(Intent(this, Pc60BleService::class.java))
            render()
        }, UiStyle.sectionParams(this))
    }

    private fun metric(icon: SkIcon, title: String, value: String, color: Int, unit: String = "") = UiStyle.card(this).apply {
        gravity = Gravity.CENTER
        addView(UiStyle.icon(this@Pc60Activity, icon, 24, color, title))
        addView(UiStyle.text(this@Pc60Activity, title, 14f, UiStyle.MUTED, false, Gravity.CENTER).apply { setPadding(0, UiStyle.dp(this@Pc60Activity, 6), 0, 0) })
        addView(UiStyle.text(this@Pc60Activity, value, 38f, color, true, Gravity.CENTER).apply { setPadding(0, UiStyle.dp(this@Pc60Activity, 4), 0, 0) })
        if (unit.isNotBlank()) addView(UiStyle.text(this@Pc60Activity, unit, 13f, UiStyle.MUTED, false, Gravity.CENTER))
    }

    private fun action(icon: SkIcon, title: String, subtitle: String, color: Int, onClick: () -> Unit) = UiStyle.card(this, 16).apply {
        background = UiStyle.rounded(UiStyle.SURFACE_2, 18, context = this@Pc60Activity)
        isClickable = true
        isFocusable = true
        val row = LinearLayout(this@Pc60Activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(UiStyle.icon(this@Pc60Activity, icon, 24, color, title), LinearLayout.LayoutParams(UiStyle.dp(this@Pc60Activity, 36), UiStyle.dp(this@Pc60Activity, 36)))
        val labels = LinearLayout(this@Pc60Activity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(UiStyle.text(this@Pc60Activity, title, 16f, color, true))
        labels.addView(UiStyle.text(this@Pc60Activity, subtitle, 13f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@Pc60Activity, 4), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiStyle.icon(this@Pc60Activity, SkIcon.CHEVRON_RIGHT, 20, UiStyle.MUTED, null))
        addView(row)
        setOnClickListener { onClick() }
    }

    private fun piLabel(pi: Double?) = when {
        pi == null || pi <= 0.0 -> "sinyal yok"
        pi < 0.5 -> "zayıf perfüzyon"
        pi < 1.0 -> "düşük perfüzyon"
        else -> "uygun sinyal"
    }

    private fun ageText(age: Long) = when {
        age == Long.MAX_VALUE -> "yok"
        age < 5_000 -> "şimdi"
        age < 60_000 -> "${age / 1000} sn önce"
        else -> "${age / 60_000} dk önce"
    }

    private fun requestBlePermissions() {
        val wanted = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = wanted.filterNot { has(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_BLE)
    }

    private fun blePermissionsReady() = if (Build.VERSION.SDK_INT >= 31) has(Manifest.permission.BLUETOOTH_SCAN) && has(Manifest.permission.BLUETOOTH_CONNECT) else has(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object { private const val REQ_BLE = 30 }
}
