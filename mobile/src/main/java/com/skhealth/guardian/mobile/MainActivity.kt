package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiStyle.BG
        window.navigationBarColor = UiStyle.BG
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
            setPadding(dp(22), dp(18), dp(22), dp(14))
            setBackgroundColor(UiStyle.BG)
        }
        header.addView(UiStyle.text(this, "SK Health Guardian", 27f, bold = true))
        header.addView(UiStyle.text(this, "Sağlık izleme ve alarm merkezi", 14f, UiStyle.MUTED).apply { setPadding(0, dp(5), 0, 0) })
        page.addView(header)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(26))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val now = System.currentTimeMillis()
        val recent = HistoryStore.recent(this, 100).asReversed().firstOrNull { it.valid && now - it.timestampMs in 0..30 * 60_000L }
        val pc60 = Pc60StatusStore.load(this)
        val pcActive = SourcePriorityCoordinator.isPc60Authoritative(this, now)
        val spo2 = if (pcActive) pc60.spo2 else recent?.spo2
        val hr = if (pcActive) pc60.heartRate ?: recent?.heartRate else recent?.heartRate
        val source = SourcePriorityCoordinator.activeSourceLabel(this, now)
        val sourceTs = if (pcActive) pc60.lastPacketAt else recent?.timestampMs ?: MonitoringState.lastReading(this)
        val age = if (sourceTs > 0) (now - sourceTs).coerceAtLeast(0) else Long.MAX_VALUE
        val cfg = AppSettings.load(this)
        val severity = when {
            spo2 != null && spo2 < cfg.spo2CriticalImmediate -> Severity.ALARM
            spo2 != null && spo2 < cfg.spo2LowThreshold -> Severity.WARNING
            hr != null && hr > cfg.heartRateHighThreshold -> Severity.WARNING
            sourceTs == 0L || age > cfg.staleDataMs -> Severity.WAITING
            else -> Severity.NORMAL
        }

        addStatusCard(severity, source, age)
        addSourceCards(pcActive, pc60)
        addMetricCards(spo2, hr, cfg.spo2LowThreshold, cfg.heartRateHighThreshold)
        addReliabilityCard()
        addQuickActions()
        addSystemSection()

        page.addView(bottomNavigation())
    }

    private fun addStatusCard(severity: Severity, source: String, age: Long) {
        val color = when (severity) {
            Severity.NORMAL -> UiStyle.GREEN
            Severity.WARNING -> UiStyle.AMBER
            Severity.ALARM -> UiStyle.RED
            Severity.WAITING -> UiStyle.BLUE
        }
        val title = when (severity) {
            Severity.NORMAL -> "Sistem normal"
            Severity.WARNING -> "Dikkat gerekiyor"
            Severity.ALARM -> "Sağlık alarmı"
            Severity.WAITING -> "Veri bekleniyor"
        }
        val subtitle = when (severity) {
            Severity.NORMAL -> "Son ölçümler belirlenen aralıkta"
            Severity.WARNING -> "Bir veya daha fazla değer eşik sınırında"
            Severity.ALARM -> "Alarm koşulu algılandı"
            Severity.WAITING -> "Geçerli ölçüm henüz alınmadı"
        }
        val card = UiStyle.card(this).apply {
            background = UiStyle.rounded(0xFF14241C.toInt(), context = this@MainActivity)
        }
        card.addView(UiStyle.text(this, "●  $title", 20f, color, true))
        card.addView(UiStyle.text(this, subtitle, 14f, UiStyle.MUTED).apply { setPadding(0, dp(8), 0, 0) })
        card.addView(UiStyle.divider(this))
        card.addView(UiStyle.text(this, "Aktif kaynak  •  $source", 14f, UiStyle.TEXT, true))
        card.addView(UiStyle.text(this, "Veri yaşı  •  ${ageText(age)}", 13f, UiStyle.MUTED).apply { setPadding(0, dp(5), 0, 0) })
        root.addView(card)
    }

    private fun addSourceCards(pcActive: Boolean, pc60: Pc60Status) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        row.addView(sourceCard("⌚", "Galaxy Watch", if (pcActive) "Yedek kaynak" else "Aktif kaynak", !pcActive), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        val pcConnected = pc60.lastPacketAt > 0 && System.currentTimeMillis() - pc60.lastPacketAt < 15_000
        row.addView(sourceCard("▣", "PC-60FW", if (pcConnected) if (pcActive) "Öncelikli kaynak" else "Bağlı" else "Bekleniyor", pcActive), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        root.addView(row, UiStyle.sectionParams(this))
    }

    private fun sourceCard(icon: String, title: String, subtitle: String, active: Boolean): LinearLayout = UiStyle.card(this, 15).apply {
        background = UiStyle.rounded(if (active) 0xFF172A22.toInt() else UiStyle.SURFACE, context = this@MainActivity)
        addView(UiStyle.text(this@MainActivity, "$icon  $title", 15f, UiStyle.TEXT, true))
        addView(UiStyle.text(this@MainActivity, "● $subtitle", 12.5f, if (active) UiStyle.GREEN else UiStyle.MUTED).apply { setPadding(0, dp(8), 0, 0) })
    }

    private fun addMetricCards(spo2: Int?, hr: Int?, low: Int, high: Int) {
        val stack = resources.configuration.fontScale >= 1.3f || resources.configuration.screenWidthDp < 360
        val holder = LinearLayout(this).apply { orientation = if (stack) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        val spo2Color = when { spo2 == null -> UiStyle.MUTED; spo2 < 80 -> UiStyle.RED; spo2 < low -> UiStyle.AMBER; else -> UiStyle.GREEN }
        val hrColor = when { hr == null -> UiStyle.MUTED; hr > high -> UiStyle.AMBER; else -> UiStyle.GREEN }
        val spo2Card = metricCard("SpO₂", spo2?.let { "$it%" } ?: "—", spo2Label(spo2, low), spo2Color)
        val hrCard = metricCard("Nabız", hr?.let { "$it" } ?: "—", if (hr == null) "Veri yok" else "${hrLabel(hr, high)} • bpm", hrColor)
        if (stack) {
            holder.addView(spo2Card, UiStyle.sectionParams(this, 0))
            holder.addView(hrCard, UiStyle.sectionParams(this, 10))
        } else {
            holder.weightSum = 2f
            holder.addView(spo2Card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            holder.addView(hrCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        }
        root.addView(holder, UiStyle.sectionParams(this))
    }

    private fun metricCard(title: String, value: String, label: String, color: Int): LinearLayout = UiStyle.card(this, 18).apply {
        addView(UiStyle.text(this@MainActivity, title, 15f, UiStyle.MUTED))
        addView(UiStyle.text(this@MainActivity, value, 38f, color, true).apply { setPadding(0, dp(10), 0, 0) })
        addView(UiStyle.text(this@MainActivity, "● $label", 13f, color).apply { setPadding(0, dp(8), 0, 0) })
    }

    private fun addReliabilityCard() {
        val reliability = SpO2ReliabilityStore.summary(this)
        val text = if (reliability.count == 0) {
            "Saat ve PC-60FW arasında henüz eşleşmiş ölçüm yok."
        } else {
            val spo2Mae = String.format(Locale.US, "%.1f", reliability.meanAbsoluteError ?: 0.0)
            val hrText = if (reliability.hrCount > 0) " • Nabız MAE ${String.format(Locale.US, "%.1f", reliability.hrMeanAbsoluteError ?: 0.0)} bpm" else ""
            "${reliability.label} • ${reliability.count} karşılaştırma • SpO₂ MAE $spo2Mae$hrText"
        }
        val card = UiStyle.card(this).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, SpO2ReliabilityActivity::class.java)) }
        }
        card.addView(UiStyle.text(this, "Watch doğrulaması", 16f, UiStyle.TEXT, true))
        card.addView(UiStyle.text(this, text, 13.5f, UiStyle.MUTED).apply { setPadding(0, dp(8), 0, 0) })
        card.addView(UiStyle.text(this, "Karşılaştırmayı aç  ›", 13f, UiStyle.BLUE, true).apply { setPadding(0, dp(12), 0, 0) })
        root.addView(card, UiStyle.sectionParams(this))
    }

    private fun addQuickActions() {
        root.addView(UiStyle.text(this, "Hızlı işlemler", 18f, UiStyle.TEXT, true).apply { setPadding(dp(2), dp(22), 0, dp(4)) })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        grid.addView(actionRow(
            actionCard("▣", "Cihazlar", "Watch / PC-60FW", Pc60Activity::class.java),
            actionCard("⌁", "Grafikler", "Ölçüm geçmişi", HistoryChartActivity::class.java)
        ))
        grid.addView(actionRow(
            actionCard("⚙", "Alarm ayarları", "Eşikler ve süreler", MeasurementSettingsActivity::class.java),
            actionCard("☎", "Acil kişiler", "SMS ve arama", ContactsActivity::class.java)
        ), UiStyle.sectionParams(this, 10))
        root.addView(grid, UiStyle.sectionParams(this, 8))
    }

    private fun addSystemSection() {
        root.addView(UiStyle.text(this, "Sistem", 18f, UiStyle.TEXT, true).apply { setPadding(dp(2), dp(22), 0, dp(4)) })
        val card = UiStyle.card(this, 6)
        addSystemItem(card, "✓", "Kurulum / QA sihirbazı", "İzin ve bağlantı kontrolleri", SystemTestActivity::class.java)
        addSystemItem(card, "♡", "Sistem sağlık kontrolü", "Arka plan servisleri ve izinler", SystemHealthActivity::class.java)
        addSystemItem(card, "☷", "Alarm olay geçmişi", "Alarm ve teknik olaylar", AlarmTimelineActivity::class.java)
        addSystemItem(card, "✉", "SMS / arama kayıtları", "Teslim ve çağrı durumları", DeliveryLogActivity::class.java)
        addSystemItem(card, "⇅", "Yedekle / geri yükle", "JSON / CSV aktarımı", ExportActivity::class.java, divider = false)
        root.addView(card, UiStyle.sectionParams(this, 8))
    }

    private fun actionRow(left: View, right: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        weightSum = 2f
        addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { rightMargin = dp(6) })
        addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = dp(6) })
    }

    private fun actionCard(icon: String, title: String, subtitle: String, target: Class<out Activity>): LinearLayout = UiStyle.card(this, 16).apply {
        minimumHeight = dp(112)
        isClickable = true
        isFocusable = true
        setOnClickListener { startActivity(Intent(this@MainActivity, target)) }
        addView(UiStyle.text(this@MainActivity, icon, 22f, UiStyle.BLUE, true))
        addView(UiStyle.text(this@MainActivity, title, 15f, UiStyle.TEXT, true).apply { setPadding(0, dp(9), 0, 0) })
        addView(UiStyle.text(this@MainActivity, subtitle, 12.5f, UiStyle.MUTED).apply { setPadding(0, dp(5), 0, 0) })
    }

    private fun addSystemItem(parent: LinearLayout, icon: String, title: String, subtitle: String, target: Class<out Activity>, divider: Boolean = true) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, target)) }
        }
        row.addView(UiStyle.text(this, icon, 21f, UiStyle.BLUE, true), LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(UiStyle.text(this, title, 15f, UiStyle.TEXT, true))
        labels.addView(UiStyle.text(this, subtitle, 12.5f, UiStyle.MUTED).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiStyle.text(this, "›", 24f, UiStyle.MUTED))
        parent.addView(row)
        if (divider) parent.addView(UiStyle.divider(this))
    }

    private fun bottomNavigation(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(10))
        setBackgroundColor(0xFF111820.toInt())
        addView(navItem("⌂", "Ana ekran", true) {})
        addView(navItem("⌁", "Grafikler", false) { startActivity(Intent(this@MainActivity, HistoryChartActivity::class.java)) })
        addView(navItem("☷", "Olaylar", false) { startActivity(Intent(this@MainActivity, AlarmTimelineActivity::class.java)) })
        addView(navItem("⚙", "Ayarlar", false) { startActivity(Intent(this@MainActivity, MeasurementSettingsActivity::class.java)) })
    }

    private fun navItem(icon: String, label: String, active: Boolean, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = dp(54)
        isClickable = true
        setOnClickListener { action() }
        val color = if (active) UiStyle.BLUE else UiStyle.MUTED
        addView(UiStyle.text(this@MainActivity, icon, 18f, color, true, Gravity.CENTER))
        addView(UiStyle.text(this@MainActivity, label, 11f, color, active, Gravity.CENTER).apply { setPadding(0, dp(3), 0, 0) })
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun spo2Label(value: Int?, low: Int) = when { value == null -> "Veri yok"; value < 80 -> "Kritik"; value < low -> "Düşük"; else -> "Normal" }
    private fun hrLabel(value: Int?, high: Int) = when { value == null -> "Veri yok"; value > high -> "Yüksek"; else -> "Normal" }
    private fun ageText(age: Long): String = when { age == Long.MAX_VALUE -> "veri yok"; age < 5_000 -> "şimdi"; age < 60_000 -> "${age / 1000} sn önce"; else -> "${age / 60_000} dk önce" }
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
