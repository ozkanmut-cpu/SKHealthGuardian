package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class SpO2ReliabilityActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); render() }

    private fun render() {
        UiStyle.applyBars(this)
        val s = SpO2ReliabilityStore.summary(this)
        val matches = SpO2ReliabilityStore.recentMatches(this)
        val root = UiStyle.page(this)
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
        root.addView(UiStyle.detailHeader(this, "Watch ↔ PC-60FW", "Ölçüm doğrulama ve güvenilirlik analizi"))

        val mae = s.meanAbsoluteError?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
        val bias = s.meanBias?.let { String.format(Locale.US, "%+.1f", it) } ?: "—"
        val hrMae = s.hrMeanAbsoluteError?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
        val hrBias = s.hrMeanBias?.let { String.format(Locale.US, "%+.1f", it) } ?: "—"
        val qualityColor = when {
            s.count < 5 -> UiStyle.AMBER
            (s.meanAbsoluteError ?: 99.0) <= 2.0 -> UiStyle.GREEN
            else -> UiStyle.AMBER
        }
        val qualityIcon = if (qualityColor == UiStyle.GREEN) SkIcon.STATUS_OK else SkIcon.STATUS_WARNING
        val source = SourcePriorityCoordinator.activeSourceLabel(this).let { if (it == "Aktif kaynak yok") "Yok" else it }

        val qualityCard = UiStyle.card(this)
        val qualityRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        qualityRow.addView(UiStyle.icon(this, qualityIcon, 28, qualityColor, s.label), LinearLayout.LayoutParams(UiStyle.dp(this, 38), UiStyle.dp(this, 38)))
        val qualityText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        qualityText.addView(UiStyle.text(this, s.label, 20f, qualityColor, true))
        qualityText.addView(UiStyle.text(this, "Alarm kaynağı: $source", 14f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@SpO2ReliabilityActivity, 5), 0, 0) })
        qualityRow.addView(qualityText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        qualityCard.addView(qualityRow)
        root.addView(qualityCard, UiStyle.sectionParams(this))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        row.addView(statCard(SkIcon.SPO2, "SpO₂", "$mae puan", "Ort. mutlak fark\nSapma $bias", UiStyle.BLUE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = UiStyle.dp(this@SpO2ReliabilityActivity, 6) })
        row.addView(statCard(SkIcon.HEART, "Nabız", "$hrMae bpm", "Ort. mutlak fark\nSapma $hrBias", UiStyle.RED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiStyle.dp(this@SpO2ReliabilityActivity, 6) })
        root.addView(row, UiStyle.sectionParams(this))

        val interpretation = when {
            s.count < 5 -> "Daha güvenilir SpO₂ yorumu için en az 5 eşzamanlı ölçüm gerekiyor."
            (s.meanAbsoluteError ?: Double.MAX_VALUE) <= 2.0 -> "Saat ile parmak oksimetresi arasında SpO₂ açısından güçlü uyum var."
            (s.meanBias ?: 0.0) >= 5.0 -> "Saat SpO₂'yi belirgin şekilde daha yüksek okuyor. PC-60FW önceliği korunur."
            (s.meanBias ?: 0.0) <= -5.0 -> "Saat SpO₂'yi belirgin şekilde daha düşük okuyor. PC-60FW önceliği korunur."
            else -> "Ölçümler arasında fark var; daha fazla eşleşmeyle değerlendirme güçlenecek."
        }
        val interpretationCard = UiStyle.card(this)
        interpretationCard.addView(UiStyle.iconLabel(this, SkIcon.INFO, "Değerlendirme", UiStyle.BLUE, UiStyle.TEXT, 22, 16f, true))
        interpretationCard.addView(UiStyle.text(this, interpretation, 15f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@SpO2ReliabilityActivity, 8), 0, 0) })
        interpretationCard.addView(UiStyle.text(this, "Saat değeri kalibre edilmez veya değiştirilmez; bu ekran yalnız karşılaştırma yapar.", 13f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@SpO2ReliabilityActivity, 8), 0, 0) })
        root.addView(interpretationCard, UiStyle.sectionParams(this))

        root.addView(UiStyle.iconLabel(this, SkIcon.CHARTS, "SpO₂ farkı • Watch − PC-60FW", UiStyle.BLUE, UiStyle.TEXT, 22, 17f, true).apply { setPadding(0, UiStyle.dp(this@SpO2ReliabilityActivity, 10), 0, UiStyle.dp(this@SpO2ReliabilityActivity, 8)) })
        root.addView(ReliabilityChartView(this, matches).apply { background = UiStyle.rounded(UiStyle.SURFACE, 18, context = this@SpO2ReliabilityActivity); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiStyle.dp(this@SpO2ReliabilityActivity, 230)) }, UiStyle.sectionParams(this))
        root.addView(UiStyle.iconLabel(this, SkIcon.CLOCK, "Son eşleşmeler", UiStyle.GREEN, UiStyle.TEXT, 22, 19f, true).apply { setPadding(0, UiStyle.dp(this@SpO2ReliabilityActivity, 14), 0, UiStyle.dp(this@SpO2ReliabilityActivity, 8)) })

        if (matches.isEmpty()) {
            val empty = UiStyle.card(this)
            empty.addView(UiStyle.iconLabel(this, SkIcon.STATUS_NONE, "Henüz eşzamanlı Watch-PC60 ölçümü yok.", UiStyle.MUTED, UiStyle.MUTED, 22, 15f, false))
            root.addView(empty, UiStyle.sectionParams(this))
        } else {
            val fmt = SimpleDateFormat("dd.MM • HH:mm:ss", Locale.forLanguageTag("tr-TR"))
            matches.takeLast(20).asReversed().forEach { m ->
                val sign = if (m.diff > 0) "+" else ""
                val hr = m.hrDiff?.let { d -> "Nabız  ${m.watchHr} / ${m.pc60Hr} bpm  •  fark ${if (d > 0) "+" else ""}$d" } ?: "Nabız eşleşmesi yok"
                val goodMatch = abs(m.diff) <= 2
                val card = UiStyle.card(this)
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                top.addView(UiStyle.icon(this, if (goodMatch) SkIcon.STATUS_OK else SkIcon.STATUS_WARNING, 20, if (goodMatch) UiStyle.GREEN else UiStyle.AMBER, null), LinearLayout.LayoutParams(UiStyle.dp(this, 28), UiStyle.dp(this, 28)))
                top.addView(UiStyle.text(this, fmt.format(Date(m.timestampMs)), 13f, UiStyle.MUTED))
                card.addView(top)
                card.addView(UiStyle.iconLabel(this, SkIcon.SPO2, "Watch ${m.watch}%  /  PC60 ${m.pc60}%", UiStyle.BLUE, UiStyle.TEXT, 20, 16f, true).apply { setPadding(0, UiStyle.dp(this@SpO2ReliabilityActivity, 7), 0, 0) })
                card.addView(UiStyle.text(this, "Fark $sign${m.diff} puan  •  $hr", 14f, if (goodMatch) UiStyle.GREEN else UiStyle.AMBER).apply { setPadding(UiStyle.dp(this@SpO2ReliabilityActivity, 29), UiStyle.dp(this@SpO2ReliabilityActivity, 5), 0, 0) })
                root.addView(card, UiStyle.sectionParams(this))
            }
        }
    }

    private fun statCard(icon: SkIcon, title: String, value: String, detail: String, color: Int) = UiStyle.card(this).apply {
        gravity = Gravity.CENTER
        addView(UiStyle.icon(this@SpO2ReliabilityActivity, icon, 24, color, title))
        addView(UiStyle.text(this@SpO2ReliabilityActivity, title, 14f, UiStyle.MUTED, false, Gravity.CENTER).apply { setPadding(0, UiStyle.dp(this@SpO2ReliabilityActivity, 5), 0, 0) })
        addView(UiStyle.text(this@SpO2ReliabilityActivity, value, 25f, color, true, Gravity.CENTER).apply { setPadding(0, UiStyle.dp(this@SpO2ReliabilityActivity, 5), 0, UiStyle.dp(this@SpO2ReliabilityActivity, 2)) })
        addView(UiStyle.text(this@SpO2ReliabilityActivity, detail, 12f, UiStyle.MUTED, false, Gravity.CENTER))
    }

    private class ReliabilityChartView(context: Context, private val data: List<SpO2ReliabilityStore.Match>) : View(context) {
        private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = UiStyle.MUTED }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; style = Paint.Style.STROKE; color = UiStyle.BLUE }
        private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = UiStyle.BLUE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; color = UiStyle.MUTED }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val items = data.takeLast(30)
            val left = 70f
            val right = width - 24f
            val top = 28f
            val bottom = height - 48f
            canvas.drawLine(left, top, left, bottom, axis)
            val zeroY = (top + bottom) / 2f
            canvas.drawLine(left, zeroY, right, zeroY, axis)
            canvas.drawText("0", 18f, zeroY + 10f, text)
            if (items.isEmpty()) { canvas.drawText("Veri yok", left + 30f, zeroY, text); return }
            val maxAbs = max(5, items.maxOf { abs(it.diff) })
            val usableW = (right - left).coerceAtLeast(1f)
            val usableH = (bottom - top) / 2f
            var lx = 0f
            var ly = 0f
            items.forEachIndexed { index, m ->
                val x = if (items.size == 1) left + usableW / 2f else left + usableW * index / (items.size - 1)
                val y = zeroY - (m.diff.toFloat() / maxAbs.toFloat()) * usableH
                if (index > 0) canvas.drawLine(lx, ly, x, y, line)
                canvas.drawCircle(x, y, 6f, point)
                lx = x; ly = y
            }
            canvas.drawText("+$maxAbs", 4f, top + 22f, text)
            canvas.drawText("-$maxAbs", 4f, bottom, text)
        }
    }
}
