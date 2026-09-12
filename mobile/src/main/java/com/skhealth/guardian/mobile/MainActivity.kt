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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        if (bluetoothReady) runCatching {
            ContextCompat.startForegroundService(this, Intent(this, WatchdogService::class.java))
        }
    }

    private fun render() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiStyle.BG)
        }
        setContentView(page)
        page.addView(header())

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(2), dp(18), dp(20))
        }
        page.addView(
            ScrollView(this).apply {
                isFillViewport = true
                clipToPadding = false
                addView(root)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val now = System.currentTimeMillis()
        val cfg = AppSettings.load(this)
        val pc60 = Pc60StatusStore.load(this)
        val watchConnected = SourcePriorityCoordinator.isWatchConnected(this, now)
        val pcConnected = pc60.lastPacketAt > 0L && now - pc60.lastPacketAt in 0..15_000L
        val spo2Pc = SourcePriorityCoordinator.isPc60Spo2Authoritative(this, now)
        val hrPc = SourcePriorityCoordinator.isPc60HeartRateAuthoritative(this, now)
        val recentRows = HistoryStore.recent(this, 100).asReversed()
        val latestValid = recentRows.firstOrNull { it.valid }
        val latestSpo2 = recentRows.firstOrNull {
            it.valid && it.spo2 != null && now - it.timestampMs in 0..30 * 60_000L
        }
        val latestHr = recentRows.firstOrNull {
            it.valid && it.heartRate != null && now - it.timestampMs in 0..30 * 60_000L
        }

        val spo2 = if (spo2Pc) pc60.spo2 else latestSpo2?.spo2
        val hr = if (hrPc) pc60.heartRate else latestHr?.heartRate
        val spo2Ts = if (spo2Pc) pc60.lastPacketAt else latestSpo2?.timestampMs ?: 0L
        val hrTs = if (hrPc) pc60.lastPacketAt else latestHr?.timestampMs ?: 0L
        val sourceTs = maxOf(spo2Ts, hrTs)
        val age = if (sourceTs > 0L) (now - sourceTs).coerceAtLeast(0L) else Long.MAX_VALUE

        val severity = when {
            spo2 != null && spo2 < cfg.spo2CriticalImmediate -> Severity.ALARM
            spo2 != null && spo2 < cfg.spo2LowThreshold -> Severity.WARNING
            hr != null && hr > cfg.heartRateHighThreshold -> Severity.WARNING
            sourceTs == 0L || age > cfg.staleDataMs -> Severity.WAITING
            else -> Severity.NORMAL
        }

        addQuickMeasureButton()
        addStatusCard(severity, age, sourceTs)
        if (FeatureFlags.ACCU_CHEK_INSTANT_UI_ENABLED) addGlucoseCard(now)
        addDeviceCards(watchConnected, pcConnected, pc60, now)
        addMetricCards(spo2, hr, cfg.spo2LowThreshold, cfg.heartRateHighThreshold)
        addRecentMeasurements(latestValid)
        addEmergencyContactsCard()
        page.addView(UiStyle.appBottomNavigation(this, "home"))
    }

    private fun header(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(10))
        applyStatusBarInset(this)

        val brandBadge = LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER
            background = UiStyle.rounded(UiStyle.SURFACE, 13, 0xFF2B3642.toInt(), 1, this@MainActivity)
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.mipmap.ic_launcher)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Orko Takip"
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                },
                LinearLayout.LayoutParams(dp(36), dp(36))
            )
        }
        addView(brandBadge, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) })

        addView(
            UiStyle.text(this@MainActivity, "Orko Takip", 22.5f, UiStyle.TEXT, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun applyStatusBarInset(view: View) {
        val left = dp(20)
        val top = dp(12)
        val right = dp(20)
        val bottom = dp(10)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(left, top + statusTop, right, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun addStatusCard(severity: Severity, age: Long, sourceTs: Long) {
        val color = when (severity) {
            Severity.NORMAL -> UiStyle.GREEN
            Severity.WARNING, Severity.WAITING -> UiStyle.AMBER
            Severity.ALARM -> UiStyle.RED
        }
        val icon = when (severity) {
            Severity.NORMAL -> SkIcon.STATUS_OK
            Severity.WARNING -> SkIcon.STATUS_WARNING
            Severity.ALARM -> SkIcon.STATUS_ALERT
            Severity.WAITING -> SkIcon.STATUS_NONE
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
        val surface = when (severity) {
            Severity.NORMAL -> 0xFF14231D.toInt()
            Severity.WARNING, Severity.WAITING -> 0xFF211E16.toInt()
            Severity.ALARM -> 0xFF251719.toInt()
        }

        val card = UiStyle.card(this, 18).apply {
            background = UiStyle.rounded(surface, 22, context = this@MainActivity)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(UiStyle.icon(this, icon, 32, color, title), LinearLayout.LayoutParams(dp(48), dp(48)))

        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(UiStyle.text(this, title, 19f, UiStyle.TEXT, true))
        labels.addView(UiStyle.text(this, subtitle, 13.5f, UiStyle.MUTED).apply { setPadding(0, dp(5), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val last = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        last.addView(UiStyle.text(this, "Son veri", 11.5f, UiStyle.MUTED))
        val lastText = if (sourceTs > 0L) SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date(sourceTs)) else "—"
        last.addView(UiStyle.text(this, lastText, 15f, UiStyle.TEXT, true).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(last)
        card.addView(row)

        if (age != Long.MAX_VALUE) {
            card.addView(UiStyle.text(this, ageText(age), 12f, UiStyle.MUTED).apply { setPadding(dp(48), dp(8), 0, 0) })
        }
        root.addView(card, UiStyle.sectionParams(this, 12))
    }

    private fun addGlucoseCard(now: Long) {
        val latest = BloodGlucoseStore.confirmedOrko(this, 1).lastOrNull()
        val pendingCount = BloodGlucoseStore.pendingOwnership(this, 100).size
        val next = GlucoseDailyPlan.nextActionable(this, now)
        val timeFormat = SimpleDateFormat("HH:mm", Locale("tr", "TR"))

        val stateColor = when (next?.state) {
            GlucoseDailyPlan.State.DUE -> UiStyle.BLUE
            GlucoseDailyPlan.State.OVERDUE -> UiStyle.AMBER
            GlucoseDailyPlan.State.UPCOMING -> UiStyle.MUTED
            GlucoseDailyPlan.State.COMPLETED, null -> UiStyle.GREEN
        }
        val stateText = when (next?.state) {
            GlucoseDailyPlan.State.DUE -> "Şimdi ölçülebilir"
            GlucoseDailyPlan.State.OVERDUE -> "Ölçüm gecikti"
            GlucoseDailyPlan.State.UPCOMING -> "Sıradaki ölçüm"
            GlucoseDailyPlan.State.COMPLETED -> "Tamamlandı"
            null -> "Plan bekleniyor"
        }

        val card = UiStyle.card(this, 18).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, GlucoseHistoryActivity::class.java)) }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(
            UiStyle.icon(this, SkIcon.DEVICE_INFO, 28, UiStyle.BLUE, "Kan şekeri"),
            LinearLayout.LayoutParams(dp(42), dp(42))
        )
        headerRow.addView(
            UiStyle.text(this, "Kan şekeri", 17f, UiStyle.TEXT, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        headerRow.addView(UiStyle.icon(this, SkIcon.CHEVRON_RIGHT, 20, UiStyle.MUTED, "Şeker geçmişini aç"))
        card.addView(headerRow)

        val valueText = latest?.let { "${it.valueMgDl} mg/dL" } ?: "— mg/dL"
        card.addView(
            UiStyle.text(this, valueText, 29f, if (latest == null) UiStyle.MUTED else UiStyle.TEXT, true).apply {
                setPadding(dp(42), dp(8), 0, 0)
            }
        )

        val latestDetail = latest?.let {
            "Son Orko ölçümü • ${timeFormat.format(Date(it.measuredAtMs))} • ${ageText((now - it.measuredAtMs).coerceAtLeast(0L))}"
        } ?: "Henüz Orko olarak doğrulanmış şeker ölçümü yok"
        card.addView(
            UiStyle.text(this, latestDetail, 12.5f, UiStyle.MUTED).apply {
                setPadding(dp(42), dp(5), 0, 0)
            }
        )

        card.addView(UiStyle.divider(this))

        val nextLabel = if (next == null) {
            "Dosefolk ilaç zamanları geldiğinde günlük ölçüm planı burada görünecek"
        } else {
            "${glucoseCheckpointLabel(next.checkpoint)} • ${timeFormat.format(Date(next.targetAtMs))}"
        }
        val planRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        planRow.addView(UiStyle.icon(this, if (next?.state == GlucoseDailyPlan.State.OVERDUE) SkIcon.STATUS_WARNING else SkIcon.CLOCK, 19, stateColor))
        val planLabels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        planLabels.addView(UiStyle.text(this, stateText, 13f, stateColor, true))
        planLabels.addView(UiStyle.text(this, nextLabel, 12.5f, UiStyle.MUTED).apply { setPadding(0, dp(3), 0, 0) })
        planRow.addView(planLabels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(planRow)

        if (pendingCount > 0) {
            card.addView(
                UiStyle.text(this, "$pendingCount ölçüm kime ait olduğu doğrulanmayı bekliyor", 12.5f, UiStyle.AMBER, true).apply {
                    setPadding(dp(27), dp(10), 0, 0)
                }
            )
        }

        root.addView(card, UiStyle.sectionParams(this, 12))
    }

    private fun glucoseCheckpointLabel(checkpoint: com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint): String = when (checkpoint) {
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.MORNING_FASTING -> "Sabah açlık"
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.BREAKFAST_POST_MEAL -> "Kahvaltı +2 saat"
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.MIDDAY -> "Öğlen"
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.DINNER_POST_MEAL -> "Akşam yemeği +2 saat"
        com.skhealth.guardian.shared.GlucoseScheduleEngine.Checkpoint.BEDTIME -> "Yatmadan önce"
    }

    private fun addDeviceCards(watchConnected: Boolean, pcConnected: Boolean, pc60: Pc60Status, now: Long) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        val watchBattery = WatchHeartbeatStore.battery(this)

        row.addView(
            deviceCard(
                icon = SkIcon.WATCH,
                title = "Galaxy Watch",
                connected = watchConnected,
                detail = if (watchConnected) {
                    if (watchBattery in 0..100) "Bağlı • %$watchBattery" else "Bağlı"
                } else "Bağlı değil",
                onClick = { startActivity(Intent(this, DevicesActivity::class.java)) }
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) }
        )

        row.addView(
            deviceCard(
                icon = SkIcon.OXIMETER,
                title = "PC-60FW",
                connected = pcConnected,
                detail = if (pcConnected) "Bağlı • ${ageText((now - pc60.lastPacketAt).coerceAtLeast(0L))}" else "Bağlı değil",
                onClick = { startActivity(Intent(this, DevicesActivity::class.java)) }
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) }
        )
        root.addView(row, UiStyle.sectionParams(this, 14))
    }

    private fun deviceCard(
        icon: SkIcon,
        title: String,
        connected: Boolean,
        detail: String,
        onClick: () -> Unit
    ): LinearLayout = UiStyle.card(this, 16).apply {
        minimumHeight = dp(142)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }

        val titleRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(UiStyle.icon(this@MainActivity, icon, 28, UiStyle.BLUE, title), LinearLayout.LayoutParams(dp(38), dp(38)))
        titleRow.addView(UiStyle.text(this@MainActivity, title, 16.5f, UiStyle.TEXT, true))
        addView(titleRow)

        addView(
            UiStyle.text(this@MainActivity, "●  $detail", 12.5f, if (connected) UiStyle.GREEN else UiStyle.MUTED).apply {
                setPadding(dp(38), dp(7), 0, 0)
            }
        )

        val action = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = UiStyle.rounded(0xFF17304A.toInt(), 16, context = this@MainActivity)
            addView(UiStyle.icon(this@MainActivity, if (connected) SkIcon.DEVICE_INFO else SkIcon.LINK, 18, UiStyle.BLUE))
            addView(
                UiStyle.text(this@MainActivity, if (connected) "Ayrıntılar" else "Bağlan", 13.5f, UiStyle.BLUE, true).apply {
                    setPadding(dp(7), 0, 0, 0)
                }
            )
        }
        addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
    }

    private fun addMetricCards(spo2: Int?, hr: Int?, low: Int, high: Int) {
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        val spo2Color = when {
            spo2 == null -> UiStyle.MUTED
            spo2 < 80 -> UiStyle.RED
            spo2 < low -> UiStyle.AMBER
            else -> UiStyle.TEXT
        }
        val hrColor = when {
            hr == null -> UiStyle.MUTED
            hr > high -> UiStyle.AMBER
            else -> UiStyle.TEXT
        }

        holder.addView(
            metricCard(SkIcon.SPO2, "SpO₂", "%", spo2?.toString() ?: "—", if (spo2 == null) "Veri yok" else spo2Label(spo2, low), spo2Color, UiStyle.BLUE),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) }
        )
        holder.addView(
            metricCard(SkIcon.HEART, "Nabız", "bpm", hr?.toString() ?: "—", if (hr == null) "Veri yok" else hrLabel(hr, high), hrColor, UiStyle.RED),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) }
        )
        root.addView(holder, UiStyle.sectionParams(this, 14))
    }

    private fun metricCard(
        icon: SkIcon,
        title: String,
        unit: String,
        value: String,
        state: String,
        valueColor: Int,
        iconColor: Int
    ): LinearLayout = UiStyle.card(this, 17).apply {
        minimumHeight = dp(158)
        val heading = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(UiStyle.icon(this@MainActivity, icon, 26, iconColor, title), LinearLayout.LayoutParams(dp(34), dp(34)))
        heading.addView(UiStyle.text(this@MainActivity, title, 16.5f, UiStyle.TEXT, true))
        addView(heading)
        addView(UiStyle.text(this@MainActivity, "$value $unit", 32f, valueColor, true).apply { setPadding(0, dp(18), 0, 0) })
        addView(
            UiStyle.text(this@MainActivity, "●  $state", 12.5f, if (state == "Veri yok") UiStyle.MUTED else valueColor).apply {
                setPadding(0, dp(14), 0, 0)
            }
        )
    }

    private fun addRecentMeasurements(recent: com.skhealth.guardian.shared.HealthReading?) {
        val card = UiStyle.card(this, 17).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(UiStyle.icon(this, SkIcon.CLOCK, 28, UiStyle.GREEN, "Son ölçüm"), LinearLayout.LayoutParams(dp(42), dp(42)))

        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(UiStyle.text(this, "Son ölçüm", 16.5f, UiStyle.TEXT, true))
        val detail = if (recent == null) {
            "Henüz ölçüm verisi yok"
        } else {
            val parts = mutableListOf<String>()
            recent.spo2?.let { parts += "SpO₂ %$it" }
            recent.heartRate?.let { parts += "Nabız $it bpm" }
            "${parts.joinToString(" • ")} • ${ageText((System.currentTimeMillis() - recent.timestampMs).coerceAtLeast(0L))}"
        }
        labels.addView(UiStyle.text(this, detail, 12.5f, UiStyle.MUTED).apply { setPadding(0, dp(5), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiStyle.icon(this, SkIcon.CHEVRON_RIGHT, 20, UiStyle.MUTED, "Geçmişi aç"), LinearLayout.LayoutParams(dp(28), dp(28)))
        card.addView(row)
        root.addView(card, UiStyle.sectionParams(this, 14))
    }

    private fun addEmergencyContactsCard() {
        val count = ContactStore.contacts(this).size
        val card = UiStyle.card(this, 17).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, ContactsActivity::class.java)) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(UiStyle.icon(this, SkIcon.CONTACTS, 29, UiStyle.PURPLE, "Acil durum kişileri"), LinearLayout.LayoutParams(dp(42), dp(42)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(UiStyle.text(this, "Acil durum kişileri", 16.5f, UiStyle.TEXT, true))
        labels.addView(
            UiStyle.text(this, if (count == 0) "Henüz kişi eklenmedi" else "$count kişi yapılandırıldı", 12.5f, UiStyle.MUTED).apply {
                setPadding(0, dp(5), 0, 0)
            }
        )
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(UiStyle.icon(this, SkIcon.CHEVRON_RIGHT, 20, UiStyle.MUTED, "Kişileri aç"), LinearLayout.LayoutParams(dp(28), dp(28)))
        card.addView(row)
        root.addView(card, UiStyle.sectionParams(this, 14))
    }

    private fun addQuickMeasureButton() {
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dp(82)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = UiStyle.rounded(0xFF20B967.toInt(), 22, context = this@MainActivity)
            isClickable = true
            isFocusable = true
            contentDescription = "Şimdi ölçüm al"
            setOnClickListener { startActivity(Intent(this@MainActivity, DevicesActivity::class.java)) }
        }
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(UiStyle.icon(this@MainActivity, SkIcon.PLAY, 23, UiStyle.TEXT))
            addView(UiStyle.text(this@MainActivity, "Şimdi ölçüm al", 18f, UiStyle.TEXT, true).apply { setPadding(dp(10), 0, 0, 0) })
        }
        button.addView(title)
        button.addView(
            UiStyle.text(this, "Cihazları kontrol et ve veri al", 12.5f, 0xFFD9F7E7.toInt(), false, Gravity.CENTER).apply {
                setPadding(0, dp(5), 0, 0)
            }
        )
        root.addView(button, UiStyle.sectionParams(this, 8))
    }

    private fun spo2Label(value: Int, low: Int) = when {
        value < 80 -> "Kritik"
        value < low -> "Düşük"
        else -> "Normal"
    }

    private fun hrLabel(value: Int, high: Int) = if (value > high) "Yüksek" else "Normal"

    private fun ageText(age: Long): String = when {
        age == Long.MAX_VALUE -> "veri yok"
        age < 5_000 -> "şimdi"
        age < 60_000 -> "${age / 1000} sn önce"
        age < 3_600_000 -> "${age / 60_000} dk önce"
        else -> "${age / 3_600_000} sa önce"
    }

    private fun dp(value: Int) = UiStyle.dp(this, value)

    private fun requestPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31) {
            wanted += Manifest.permission.BLUETOOTH_CONNECT
            wanted += Manifest.permission.BLUETOOTH_SCAN
        }
        val missing = wanted.filterNot { has(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
    }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private enum class Severity { NORMAL, WARNING, ALARM, WAITING }

    companion object {
        private const val REQ_PERMISSIONS = 10
    }
}
