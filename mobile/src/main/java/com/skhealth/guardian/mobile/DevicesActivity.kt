package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat

class DevicesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val root = UiStyle.page(this)
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
        root.addView(UiStyle.detailHeader(this, "Cihazlar", "Bağlantı ve veri durumu"))

        val watchConnected = SourcePriorityCoordinator.isWatchConnected(this, now)
        val heartbeatAt = WatchHeartbeatStore.timestamp(this)
        val watchBattery = WatchHeartbeatStore.battery(this)
        root.addView(deviceCard(SkIcon.WATCH, "Galaxy Watch", watchConnected, listOf(
            SkIcon.LINK to ("Durum" to (if (watchConnected) "Bağlı" else "Bağlı değil")),
            SkIcon.BATTERY to ("Pil seviyesi" to (if (watchBattery in 0..100) "%$watchBattery" else "—")),
            SkIcon.CLOCK to ("Son bağlantı" to (if (heartbeatAt > 0) ageText((now - heartbeatAt).coerceAtLeast(0L)) else "—"))
        )))

        val pc = Pc60StatusStore.load(this)
        val pcConnected = pc.lastPacketAt > 0 && now - pc.lastPacketAt in 0..15_000L
        root.addView(deviceCard(
            SkIcon.OXIMETER, "PC-60FW", pcConnected, listOf(
                SkIcon.LINK to ("Durum" to (if (pcConnected) "Bağlı" else "Bağlı değil")),
                SkIcon.BATTERY to ("Pil seviyesi" to (pc.batteryLevel?.let { "%$it" } ?: "—")),
                SkIcon.CLOCK to ("Son veri" to (if (pc.lastPacketAt > 0) ageText((now - pc.lastPacketAt).coerceAtLeast(0L)) else "—"))
            ),
            actionLabel = "Cihaz ayrıntılarını aç",
            action = { startActivity(Intent(this, Pc60Activity::class.java)) }
        ), UiStyle.sectionParams(this))

        if (FeatureFlags.ACCU_CHEK_INSTANT_UI_ENABLED) {
            val glucose = AccuChekStatusStore.load(this)
            val glucoseConnected = glucose.state.startsWith("Bağlı", ignoreCase = true)
            root.addView(deviceCard(
                SkIcon.DEVICE_INFO,
                glucose.deviceName.ifBlank { "Accu-Chek Instant" },
                glucoseConnected,
                listOf(
                    SkIcon.LINK to ("Durum" to glucose.state),
                    SkIcon.CLOCK to ("Son şeker" to (glucose.lastValueMgDl?.let { "$it mg/dL" } ?: "—")),
                    SkIcon.STATUS_NONE to ("Doğrulama bekleyen" to glucose.pendingOwnershipCount.toString()),
                    SkIcon.CLOCK to ("Son veri" to (glucose.lastReadingAtMs?.let { ageText((now - it).coerceAtLeast(0L)) } ?: "—"))
                ),
                actionLabel = if (AccuChekStatusStore.savedAddress(this).isBlank()) "Accu-Chek bağla" else "Yeniden tara / bağlan",
                action = {
                    ContextCompat.startForegroundService(this, Intent(this, AccuChekBleService::class.java).setAction(AccuChekBleService.ACTION_RESCAN))
                }
            ), UiStyle.sectionParams(this))

            root.addView(UiStyle.iconButton(this, SkIcon.CLOCK, "Şeker geçmişi ve doğrulama").apply {
                setOnClickListener { startActivity(Intent(this@DevicesActivity, GlucoseHistoryActivity::class.java)) }
            }, UiStyle.sectionParams(this, 10))
        }
    }

    private fun deviceCard(
        deviceIcon: SkIcon,
        title: String,
        connected: Boolean,
        rows: List<Pair<SkIcon, Pair<String, String>>>,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ): LinearLayout = UiStyle.card(this, 17).apply {
        val header = LinearLayout(this@DevicesActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(UiStyle.icon(this@DevicesActivity, deviceIcon, 28, UiStyle.BLUE, title), LinearLayout.LayoutParams(UiStyle.dp(this@DevicesActivity, 38), UiStyle.dp(this@DevicesActivity, 38)))
        header.addView(UiStyle.text(this@DevicesActivity, title, 18f, UiStyle.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(UiStyle.icon(this@DevicesActivity, if (connected) SkIcon.STATUS_OK else SkIcon.STATUS_NONE, 20, if (connected) UiStyle.GREEN else UiStyle.MUTED))
        header.addView(UiStyle.text(this@DevicesActivity, if (connected) "Bağlı" else "Bağlı değil", 12.5f, if (connected) UiStyle.GREEN else UiStyle.MUTED, true).apply { setPadding(UiStyle.dp(this@DevicesActivity, 5), 0, 0, 0) })
        addView(header)

        rows.forEachIndexed { index, item ->
            if (index == 0) addView(UiStyle.divider(this@DevicesActivity))
            val row = LinearLayout(this@DevicesActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, UiStyle.dp(this@DevicesActivity, 7), 0, UiStyle.dp(this@DevicesActivity, 7))
            }
            row.addView(UiStyle.icon(this@DevicesActivity, item.first, 18, UiStyle.MUTED), LinearLayout.LayoutParams(UiStyle.dp(this@DevicesActivity, 28), UiStyle.dp(this@DevicesActivity, 28)))
            row.addView(UiStyle.text(this@DevicesActivity, item.second.first, 13f, UiStyle.MUTED), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(UiStyle.text(this@DevicesActivity, item.second.second, 13f, UiStyle.TEXT, true))
            addView(row)
        }

        if (action != null) {
            val button = LinearLayout(this@DevicesActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                minimumHeight = UiStyle.dp(this@DevicesActivity, 54)
                background = UiStyle.rounded(UiStyle.SURFACE_2, 18, 0xFF405064.toInt(), 1, this@DevicesActivity)
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
                addView(UiStyle.icon(this@DevicesActivity, SkIcon.DEVICE_INFO, 21, UiStyle.BLUE))
                addView(UiStyle.text(this@DevicesActivity, actionLabel ?: "Cihaz ayrıntılarını aç", 15f, UiStyle.TEXT, true).apply { setPadding(UiStyle.dp(this@DevicesActivity, 8), 0, 0, 0) })
            }
            addView(button, UiStyle.sectionParams(this@DevicesActivity, 10))
        }
    }

    private fun ageText(age: Long) = when {
        age < 5_000 -> "şimdi"
        age < 60_000 -> "${age / 1000} sn önce"
        age < 3_600_000 -> "${age / 60_000} dk önce"
        else -> "${age / 3_600_000} sa önce"
    }
}
