package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)
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
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiStyle.BG)
        }
        setContentView(page)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(UiStyle.text(this, "SK Health Guardian", 27f, UiStyle.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(UiStyle.text(this, "⚙", 23f, UiStyle.MUTED, true, Gravity.CENTER).apply {
            setPadding(dp(12), dp(8), dp(4), dp(8)); isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsHubActivity::class.java)) }
        })
        header.addView(top)
        header.addView(UiStyle.text(this, "Sağlık izleme ve alarm merkezi", 14f, UiStyle.MUTED).apply { setPadding(0, dp(5), 0, 0) })
        page.addView(header)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(28))
        }
        page.addView(ScrollView(this).apply { isFillViewport = true; addView(root) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val now = System.currentTimeMillis()
        val cfg = AppSettings.load(this)
        val pc60 = Pc60StatusStore.load(this)
        val watchConnected = SourcePriorityCoordinator.isWatchConnected(this, now)
        val pcConnected = pc60.lastPacketAt > 0L && now - pc60.lastPacketAt in 0..15_000L
        val spo2Pc = SourcePriorityCoordinator.isPc60Spo2Authoritative(this, now)
        val hrPc = SourcePriorityCoordinator.isPc60HeartRateAuthoritative(this, now)
        val recent = HistoryStore.recent(this, 100).asReversed().firstOrNull { it.valid }
        val recentFresh = recent?.takeIf { now - it.timestampMs in 0..30 * 60_000L }
        val spo2 = if (spo2Pc) pc60.spo2 else recentFresh?.spo2
        val hr = if (hrPc) pc60.heartRate else recentFresh?.heartRate
        val sourceTs = when {
            spo2Pc || hrPc -> pc60.lastPacketAt
            recentFresh != null -> recentFresh.timestampMs
            else -> 0L
        }
        val age = if (sourceTs > 0L) (now - sourceTs).coerceAtLeast(0L) else Long.MAX_VALUE
        val severity = when {
            spo2 != null && spo2 < cfg.spo2CriticalImmediate -> Severity.ALARM
            spo2 != null && spo2 < cfg.spo2LowThreshold -> Severity.WARNING
            hr != null && hr > cfg.heartRateHighThreshold -> Severity.WARNING
            sourceTs == 0L || age > cfg.staleDataMs -> Severity.WAITING
            else -> Severity.NORMAL
        }

        addStatusCard(severity, age, now)
        addDeviceCards(watchConnected, pcConnected, pc60, now)
        addMetricCards(spo2, hr, cfg.spo2LowThreshold, cfg.heartRateHighThreshold)
        addRecentMeasurements(recent)
        addPrimaryActions()
        addEmergencyContactsCard()

        page.addView(bottomNavigation())
    }

    private fun addStatusCard(severity: Severity, age: Long, now: Long) {
        val color = when (severity) {
            Severity.NORMAL -> UiStyle.GREEN
            Severity.WARNING -> UiStyle.AMBER
            Severity.ALARM -> UiStyle.RED
            Severity.WAITING -> UiStyle.RED
        }
        val title = when (severity) {
            Severity.NORMAL -> "İzleme aktif"
            Severity.WARNING -> "Dikkat gerekiyor"
            Severity.ALARM -> "Sağlık alarmı"
            Severity.WAITING -> "Veri bekleniyor"
        }
        val subtitle = when (severity) {
            Severity.NORMAL -> "Son ölçüm güncel"
            Severity.WARNING -> "Bir değer alarm eşiğine yaklaştı"
            Severity.ALARM -> "Alarm koşulu algılandı"
            Severity.WAITING -> "Geçerli ölçüm henüz alınmadı"
        }
        val card = UiStyle.card(this, 16).apply {
            background = UiStyle.rounded(if (severity == Severity.NORMAL) 0xFF14231D.toInt() else 0xFF221B20.toInt(), 20, context = this@MainActivity)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(UiStyle.text(this, if (severity == Severity.WAITING) "!" else "●", 22f, color, true, Gravity.CENTER), LinearLayout.LayoutParams(dp(42), dp(42)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(UiStyle.text(this, title, 18f, UiStyle.TEXT, true))
        labels.addView(UiStyle.text(this, subtitle, 13.5f, UiStyle.MUTED).apply { setPadding(0, dp(5), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val time = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date(now))
        val last = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        last.addView(UiStyle.text(this, "Son kontrol", 11.5f, UiStyle.MUTED))
        last.addView(UiStyle.text(this, time, 14f, UiStyle.TEXT, true).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(last)
        card.addView(row)
        if (age != Long.MAX_VALUE) card.addView(UiStyle.text(this, "Son veri: ${ageText(age)}", 12f, UiStyle.MUTED).apply { setPadding(dp(42), dp(10), 0, 0) })
        root.addView(card)
    }

    private fun addDeviceCards(watchConnected: Boolean, pcConnected: Boolean, pc60: Pc60Status, now: Long) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        val watchBattery = WatchHeartbeatStore.battery(this)
        row.addView(deviceCard(
            title = "Galaxy Watch",
            connected = watchConnected,
            detail = if (watchConnected) if (watchBattery in 0..100) "Bağlı • %$watchBattery" else "Bağlı" else "Bağlı değil",
            onClick = { startActivity(Intent(this, DevicesActivity::class.java)) }
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        row.addView(deviceCard(
            title = "PC-60FW",
            connected = pcConnected,
            detail = if (pcConnected) "Bağlı • ${ageText((now - pc60.lastPacketAt).coerceAtLeast(0L))}" else "Bağlı değil",
            onClick = { startActivity(Intent(this, DevicesActivity::class.java)) }
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        root.addView(row, UiStyle.sectionParams(this))
    }

    private fun deviceCard(title: String, connected: Boolean, detail: String, onClick: () -> Unit): LinearLayout = UiStyle.card(this, 15).apply {
        minimumHeight = dp(104)
        isClickable = true; isFocusable = true; setOnClickListener { onClick() }
        addView(UiStyle.text(this@MainActivity, title, 16f, UiStyle.TEXT, true))
        addView(UiStyle.text(this@MainActivity, "●  $detail", 12.5f, if (connected) UiStyle.GREEN else UiStyle.MUTED).apply { setPadding(0, dp(9), 0, 0) })
        addView(UiStyle.text(this@MainActivity, "›", 24f, UiStyle.MUTED, false, Gravity.END))
    }

    private fun addMetricCards(spo2: Int?, hr: Int?, low: Int, high: Int) {
        val holder = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        val spo2Color = when { spo2 == null -> UiStyle.MUTED; spo2 < 80 -> UiStyle.RED; spo2 < low -> UiStyle.AMBER; else -> UiStyle.TEXT }
        val hrColor = when { hr == null -> UiStyle.MUTED; hr > high -> UiStyle.AMBER; else -> UiStyle.TEXT }
        holder.addView(metricCard("SpO₂", "%", spo2?.toString() ?: "— —", if (spo2 == null) "Veri yok" else spo2Label(spo2, low), spo2Color), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        holder.addView(metricCard("Nabız", "bpm", hr?.toString() ?: "— —", if (hr == null) "Veri yok" else hrLabel(hr, high), hrColor), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        root.addView(holder, UiStyle.sectionParams(this))
    }

    private fun metricCard(title: String, unit: String, value: String, state: String, valueColor: Int): LinearLayout = UiStyle.card(this, 18).apply {
        minimumHeight = dp(166)
        addView(UiStyle.text(this@MainActivity, title, 17f, UiStyle.TEXT, true))
        addView(UiStyle.text(this@MainActivity, unit, 12f, UiStyle.MUTED).apply { setPadding(0, dp(3), 0, 0) })
        addView(UiStyle.text(this@MainActivity, value, 34f, valueColor, true).apply { setPadding(0, dp(22), 0, 0) })
        addView(UiStyle.text(this@MainActivity, "●  $state", 12.5f, if (state == "Veri yok") UiStyle.MUTED else valueColor).apply { setPadding(0, dp(18), 0, 0) })
    }

    private fun addRecentMeasurements(recent: com.skhealth.guardian.shared.HealthReading?) {
        val card = UiStyle.card(this, 17).apply {
            isClickable = true; isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, HistoryChartActivity::class.java)) }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(UiStyle.text(this, "Son ölçümler", 16f, UiStyle.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiStyle.text(this, "Tümü  ›", 12.5f, UiStyle.MUTED))
        card.addView(row)
        val detail = if (recent == null) "Henüz ölçüm verisi yok\nÖlçümler alındığında grafik burada görünecek." else {
            val parts = mutableListOf<String>()
            recent.spo2?.let { parts += "SpO₂ %$it" }
            recent.heartRate?.let { parts += "Nabız $it bpm" }
            "${parts.joinToString("  •  ")}\n${ageText((System.currentTimeMillis() - recent.timestampMs).coerceAtLeast(0L))}"
        }
        card.addView(UiStyle.text(this, detail, 13f, UiStyle.MUTED, false, Gravity.CENTER).apply { setPadding(0, dp(20), 0, dp(8)) })
        root.addView(card, UiStyle.sectionParams(this))
    }

    private fun addPrimaryActions() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        row.addView(actionCard("Cihazlar", "Bağlantı ve durum", true) { startActivity(Intent(this, DevicesActivity::class.java)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        row.addView(actionCard("Olaylar", "Son kayıtlar", false) { startActivity(Intent(this, AlarmTimelineActivity::class.java)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        root.addView(row, UiStyle.sectionParams(this))
    }

    private fun actionCard(title: String, subtitle: String, primary: Boolean, action: () -> Unit): LinearLayout = UiStyle.card(this, 16).apply {
        minimumHeight = dp(92); isClickable = true; isFocusable = true; setOnClickListener { action() }
        if (primary) background = UiStyle.rounded(0xFF1677FF.toInt(), 20, context = this@MainActivity)
        addView(UiStyle.text(this@MainActivity, title, 16f, UiStyle.TEXT, true))
        addView(UiStyle.text(this@MainActivity, subtitle, 12.5f, if (primary) 0xFFD7E8FF.toInt() else UiStyle.MUTED).apply { setPadding(0, dp(7), 0, 0) })
        addView(UiStyle.text(this@MainActivity, "›", 23f, if (primary) UiStyle.TEXT else UiStyle.MUTED, false, Gravity.END))
    }

    private fun addEmergencyContactsCard() {
        val card = UiStyle.card(this, 17).apply {
            isClickable = true; isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, ContactsActivity::class.java)) }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(UiStyle.text(this, "Acil durum kişileri", 16f, UiStyle.TEXT, true))
        labels.addView(UiStyle.text(this, "Arama ve SMS ayarları", 12.5f, UiStyle.MUTED).apply { setPadding(0, dp(5), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiStyle.text(this, "›", 24f, UiStyle.MUTED))
        card.addView(row)
        root.addView(card, UiStyle.sectionParams(this))
    }

    private fun bottomNavigation(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(10)); setBackgroundColor(0xFF111820.toInt())
        addView(navItem("⌂", "Ana ekran", true) {})
        addView(navItem("⌁", "Grafikler", false) { startActivity(Intent(this@MainActivity, HistoryChartActivity::class.java)) })
        addView(navItem("☷", "Olaylar", false) { startActivity(Intent(this@MainActivity, AlarmTimelineActivity::class.java)) })
        addView(navItem("⚙", "Ayarlar", false) { startActivity(Intent(this@MainActivity, SettingsHubActivity::class.java)) })
    }

    private fun navItem(icon: String, label: String, active: Boolean, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(54); isClickable = true
        setOnClickListener { action() }
        val color = if (active) UiStyle.BLUE else UiStyle.MUTED
        addView(UiStyle.text(this@MainActivity, icon, 18f, color, true, Gravity.CENTER))
        addView(UiStyle.text(this@MainActivity, label, 11f, color, active, Gravity.CENTER).apply { setPadding(0, dp(3), 0, 0) })
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun spo2Label(value: Int, low: Int) = when { value < 80 -> "Kritik"; value < low -> "Düşük"; else -> "Normal" }
    private fun hrLabel(value: Int, high: Int) = if (value > high) "Yüksek" else "Normal"
    private fun ageText(age: Long): String = when { age == Long.MAX_VALUE -> "veri yok"; age < 5_000 -> "şimdi"; age < 60_000 -> "${age / 1000} sn önce"; age < 3_600_000 -> "${age / 60_000} dk önce"; else -> "${age / 3_600_000} sa önce" }
    private fun dp(value: Int) = UiStyle.dp(this, value)

    private fun requestPermissions() {
        val wanted = mutableListOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31) { wanted += Manifest.permission.BLUETOOTH_CONNECT; wanted += Manifest.permission.BLUETOOTH_SCAN }
        val missing = wanted.filterNot { has(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private enum class Severity { NORMAL, WARNING, ALARM, WAITING }
    companion object { private const val REQ_PERMISSIONS = 10 }
}
