package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class SpO2ReliabilityActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val s = SpO2ReliabilityStore.summary(this)
        val matches = SpO2ReliabilityStore.recentMatches(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply { text = "Watch Doğrulama"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply {
            text = "Galaxy Watch ile PC-60FW eşzamanlı SpO₂ ölçümleri karşılaştırılır. Saat değeri değiştirilmez; bu yalnız güvenilirlik analizidir."
            textSize = 15f; setPadding(0, 10, 0, 18)
        })

        val mae = s.meanAbsoluteError?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
        val bias = s.meanBias?.let { String.format(Locale.US, "%+.1f", it) } ?: "—"
        root.addView(TextView(this).apply { text = s.label; textSize = 22f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 6, 0, 8) })
        root.addView(TextView(this).apply {
            text = "${s.count} karşılaştırma • ort. fark $mae puan • sapma $bias puan\nAktif alarm kaynağı: ${SourcePriorityCoordinator.activeSourceLabel(this@SpO2ReliabilityActivity)}"
            textSize = 16f; setPadding(0, 0, 0, 18)
        })
        root.addView(TextView(this).apply {
            text = when {
                s.count < 5 -> "Daha güvenilir yorum için en az 5 eşzamanlı ölçüm gerekiyor."
                (s.meanAbsoluteError ?: Double.MAX_VALUE) <= 2.0 -> "Saat ile parmak oksimetresi arasında güçlü uyum var."
                (s.meanBias ?: 0.0) >= 5.0 -> "Saat belirgin şekilde daha yüksek okuyor. Alarm kararında PC-60FW önceliği korunur."
                (s.meanBias ?: 0.0) <= -5.0 -> "Saat belirgin şekilde daha düşük okuyor. Alarm kararında PC-60FW önceliği korunur."
                else -> "Ölçümler arasında fark var; daha fazla eşleşmeyle değerlendirme güçlenecek."
            }
            textSize = 17f; setPadding(0, 0, 0, 22)
        })

        root.addView(TextView(this).apply { text = "Fark grafiği • Watch − PC-60FW"; textSize = 19f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(ReliabilityChartView(this, matches).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 420) })
        root.addView(TextView(this).apply { text = "Son eşleşmeler"; textSize = 19f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 22, 0, 8) })

        if (matches.isEmpty()) root.addView(TextView(this).apply { text = "Henüz eşzamanlı Watch-PC60 ölçümü yok."; textSize = 16f })
        else {
            val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
            matches.takeLast(20).asReversed().forEach { m ->
                val sign = if (m.diff > 0) "+" else ""
                root.addView(TextView(this).apply { text = "${fmt.format(Date(m.timestampMs))} • Watch ${m.watch}% • PC60 ${m.pc60}% • fark $sign${m.diff}"; textSize = 16f; setPadding(0, 8, 0, 8) })
            }
        }
    }

    private class ReliabilityChartView(context: Context, private val data: List<SpO2ReliabilityStore.Match>) : View(context) {
        private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; style = Paint.Style.STROKE }
        private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val items = data.takeLast(30); val left = 70f; val right = width - 24f; val top = 24f; val bottom = height - 48f
            canvas.drawLine(left, top, left, bottom, axis); val zeroY = (top + bottom) / 2f; canvas.drawLine(left, zeroY, right, zeroY, axis); canvas.drawText("0", 18f, zeroY + 10f, text)
            if (items.isEmpty()) { canvas.drawText("Veri yok", left + 30f, zeroY, text); return }
            val maxAbs = max(5, items.maxOf { abs(it.diff) }); val usableW = (right - left).coerceAtLeast(1f); val usableH = (bottom - top) / 2f
            var lastX = 0f; var lastY = 0f
            items.forEachIndexed { index, m ->
                val x = if (items.size == 1) left + usableW / 2f else left + usableW * index / (items.size - 1)
                val y = zeroY - (m.diff.toFloat() / maxAbs.toFloat()) * usableH
                if (index > 0) canvas.drawLine(lastX, lastY, x, y, line); canvas.drawCircle(x, y, 6f, point); lastX = x; lastY = y
            }
            canvas.drawText("+$maxAbs", 4f, top + 22f, text); canvas.drawText("-$maxAbs", 4f, bottom, text)
        }
    }
}
