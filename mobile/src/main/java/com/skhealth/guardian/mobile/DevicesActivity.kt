package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

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

        root.addView(UiStyle.title(this, "Cihazlar"))
        root.addView(UiStyle.subtitle(this, "Bağlantı ve veri durumu"))

        val watchConnected = SourcePriorityCoordinator.isWatchConnected(this, now)
        val heartbeatAt = WatchHeartbeatStore.timestamp(this)
        val watchBattery = WatchHeartbeatStore.battery(this)
        root.addView(deviceCard(
            title = "Galaxy Watch",
            connected = watchConnected,
            rows = listOf(
                "Durum" to if (watchConnected) "Bağlı" else "Bağlı değil",
                "Pil seviyesi" to if (watchBattery in 0..100) "%$watchBattery" else "—",
                "Son bağlantı" to if (heartbeatAt > 0) ageText((now - heartbeatAt).coerceAtLeast(0L)) else "—"
            ),
            action = null
        ))

        val pc = Pc60StatusStore.load(this)
        val pcConnected = pc.lastPacketAt > 0 && now - pc.lastPacketAt in 0..15_000L
        root.addView(deviceCard(
            title = "PC-60FW",
            connected = pcConnected,
            rows = listOf(
                "Durum" to if (pcConnected) "Bağlı" else "Bağlı değil",
                "Pil seviyesi" to pc.battery?.let { "%$it" } ?: "—",
                "Son veri" to if (pc.lastPacketAt > 0) ageText((now - pc.lastPacketAt).coerceAtLeast(0L)) else "—"
            ),
            action = { startActivity(Intent(this, Pc60Activity::class.java)) }
        ), UiStyle.sectionParams(this))
    }

    private fun deviceCard(title: String, connected: Boolean, rows: List<Pair<String, String>>, action: (() -> Unit)?): LinearLayout = UiStyle.card(this, 17).apply {
        val header = LinearLayout(this@DevicesActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(UiStyle.text(this@DevicesActivity, title, 18f, UiStyle.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(UiStyle.text(this@DevicesActivity, if (connected) "● Bağlı" else "● Bağlı değil", 12.5f, if (connected) UiStyle.GREEN else UiStyle.RED, true))
        addView(header)
        rows.forEachIndexed { index, item ->
            if (index == 0) addView(UiStyle.divider(this@DevicesActivity))
            val row = LinearLayout(this@DevicesActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, UiStyle.dp(this@DevicesActivity, 7), 0, UiStyle.dp(this@DevicesActivity, 7)) }
            row.addView(UiStyle.text(this@DevicesActivity, item.first, 13f, UiStyle.MUTED), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(UiStyle.text(this@DevicesActivity, item.second, 13f, UiStyle.TEXT, true))
            addView(row)
        }
        if (action != null) {
            addView(UiStyle.button(this@DevicesActivity, "Cihaz ayrıntılarını aç", false).apply { setOnClickListener { action() } })
        }
    }

    private fun ageText(age: Long): String = when {
        age < 5_000 -> "şimdi"
        age < 60_000 -> "${age / 1000} sn önce"
        age < 3_600_000 -> "${age / 60_000} dk önce"
        else -> "${age / 3_600_000} sa önce"
    }
}
