package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)

        val root = UiStyle.page(this)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(UiStyle.BG)
            addView(root)
        })

        val rows = HistoryStore.recent(this, 300).asReversed()
        root.addView(
            UiStyle.detailHeader(
                this,
                "Ölçüm geçmişi",
                if (rows.isEmpty()) "Henüz ölçüm kaydı yok" else "Son ${rows.size} kayıt • en yeni ölçüm üstte"
            )
        )

        root.addView(UiStyle.iconButton(this, SkIcon.CHARTS, "Grafikleri aç").apply {
            setOnClickListener { startActivity(Intent(this@HistoryActivity, HistoryChartActivity::class.java)) }
        })

        val fmt = SimpleDateFormat("dd.MM.yyyy • HH:mm:ss", Locale("tr", "TR"))
        if (rows.isEmpty()) {
            root.addView(UiStyle.card(this).apply {
                val row = LinearLayout(this@HistoryActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(SkIconView(this@HistoryActivity, SkIcon.CLOCK, UiStyle.MUTED), LinearLayout.LayoutParams(UiStyle.dp(this@HistoryActivity, 38), UiStyle.dp(this@HistoryActivity, 38)))
                val labels = LinearLayout(this@HistoryActivity).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(UiStyle.text(this@HistoryActivity, "Ölçümler burada görünecek", 18f, UiStyle.TEXT, true))
                labels.addView(UiStyle.text(this@HistoryActivity, "Galaxy Watch veya PC-60FW'den geçerli veri geldiğinde geçmiş otomatik oluşur.", 14f, UiStyle.MUTED).apply {
                    setPadding(0, UiStyle.dp(this@HistoryActivity, 7), 0, 0)
                })
                row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiStyle.dp(this@HistoryActivity, 10) })
                addView(row)
            }, UiStyle.sectionParams(this))
            return
        }

        rows.forEach { r ->
            val source = sourceLabel(r.source)
            val spo2 = r.spo2
            val heartRate = r.heartRate
            val accent = when {
                !r.valid -> UiStyle.AMBER
                spo2 != null && spo2 < 90 -> UiStyle.RED
                source == "PC-60FW" -> UiStyle.GREEN
                else -> UiStyle.BLUE
            }

            val card = UiStyle.card(this)
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val sourceIcon = if (source == "PC-60FW") SkIcon.OXIMETER else SkIcon.WATCH
            header.addView(SkIconView(this, sourceIcon, accent), LinearLayout.LayoutParams(UiStyle.dp(this, 30), UiStyle.dp(this, 30)))
            header.addView(UiStyle.text(this, source, 15f, accent, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiStyle.dp(this@HistoryActivity, 8) })
            header.addView(SkIconView(this, if (r.valid) SkIcon.STATUS_OK else SkIcon.STATUS_WARNING, if (r.valid) UiStyle.GREEN else UiStyle.AMBER), LinearLayout.LayoutParams(UiStyle.dp(this, 24), UiStyle.dp(this, 24)))
            header.addView(UiStyle.text(this, if (r.valid) "GEÇERLİ" else "GEÇERSİZ", 12f, if (r.valid) UiStyle.GREEN else UiStyle.AMBER, true).apply { setPadding(UiStyle.dp(this@HistoryActivity, 5), 0, 0, 0) })
            card.addView(header)

            val metrics = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
                setPadding(0, UiStyle.dp(this@HistoryActivity, 12), 0, 0)
            }
            metrics.addView(metric(SkIcon.SPO2, "SpO₂", spo2?.let { "$it%" } ?: "—", UiStyle.BLUE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = UiStyle.dp(this@HistoryActivity, 6) })
            metrics.addView(metric(SkIcon.HEART, "Nabız", heartRate?.toString() ?: "—", UiStyle.RED, "bpm"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiStyle.dp(this@HistoryActivity, 6) })
            card.addView(metrics)

            val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, UiStyle.dp(this@HistoryActivity, 12), 0, 0) }
            timeRow.addView(SkIconView(this, SkIcon.CLOCK, UiStyle.MUTED), LinearLayout.LayoutParams(UiStyle.dp(this, 20), UiStyle.dp(this, 20)))
            timeRow.addView(UiStyle.text(this, fmt.format(Date(r.timestampMs)), 13f, UiStyle.MUTED).apply { setPadding(UiStyle.dp(this@HistoryActivity, 6), 0, 0, 0) })
            card.addView(timeRow)
            root.addView(card, UiStyle.sectionParams(this, 10))
        }
    }

    private fun metric(icon: SkIcon, label: String, value: String, color: Int, unit: String = "") = UiStyle.card(this).apply {
        gravity = Gravity.CENTER
        addView(SkIconView(this@HistoryActivity, icon, color), LinearLayout.LayoutParams(UiStyle.dp(this@HistoryActivity, 28), UiStyle.dp(this@HistoryActivity, 28)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addView(UiStyle.text(this@HistoryActivity, label, 13f, UiStyle.MUTED, false, Gravity.CENTER).apply { setPadding(0, UiStyle.dp(this@HistoryActivity, 5), 0, 0) })
        addView(UiStyle.text(this@HistoryActivity, value, 30f, color, true, Gravity.CENTER))
        if (unit.isNotBlank()) addView(UiStyle.text(this@HistoryActivity, unit, 12f, UiStyle.MUTED, false, Gravity.CENTER))
    }

    private fun sourceLabel(source: String): String = when {
        source.contains("pc60", true) -> "PC-60FW"
        source.contains("watch", true) -> "Galaxy Watch"
        else -> source.ifBlank { "Bilinmeyen kaynak" }
    }
}
