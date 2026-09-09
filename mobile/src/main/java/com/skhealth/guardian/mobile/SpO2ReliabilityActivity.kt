package com.skhealth.guardian.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val s = SpO2ReliabilityStore.summary(this)
        val matches = SpO2ReliabilityStore.recentMatches(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply {
            text = "Saat doğrulama"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Galaxy Watch SpO₂ ölçümleri, aynı zaman penceresindeki PC-60FW ölçümleriyle karşılaştırılır. Saat değerleri değiştirilmez; bu ekran yalnız doğrulama/uyum analizi yapar."
            textSize = 16f
            setPadding(0, 12, 0, 20)
        })

        val mae = s.meanAbsoluteError?.let { String.format(Locale.US, "%.1f", it) } ?: "-"
        val bias = s.meanBias?.let { String.format(Locale.US, "%+.1f", it) } ?: "-"
        val lastTime = s.lastMatchAt?.let { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR")).format(Date(it)) } ?: "yok"

        addMetric(root, "Sonuç", s.label)
        addMetric(root, "Eşleşme sayısı", s.count.toString())
        addMetric(root, "Ortalama mutlak fark", "$mae puan")
        addMetric(root, "Ortalama sapma (Saat - PC60)", "$bias puan")
        addMetric(root, "Son eşleşme", lastTime)
        addMetric(root, "Aktif alarm kaynağı", SourcePriorityCoordinator.activeSourceLabel(this))

        root.addView(TextView(this).apply {
            text = when {
                s.count < 5 -> "Güvenilirlik yorumu için en az 5 eşzamanlı ölçüm gerekiyor."
                (s.meanAbsoluteError ?: Double.MAX_VALUE) <= 2.0 -> "Saat ile oksimetre arasında güçlü uyum var."
                (s.meanBias ?: 0.0) >= 5.0 -> "Saat oksimetreye göre sistematik olarak daha yüksek okuyor. Alarm kararında PC-60FW önceliği korunur."
                (s.meanBias ?: 0.0) <= -5.0 -> "Saat oksimetreye göre sistematik olarak daha düşük okuyor. Alarm kararında PC-60FW önceliği korunur."
                else -> "Ölçümler arasında anlamlı fark var; daha fazla eşleşme biriktikçe değerlendirme güçlenecek."
            }
            textSize = 17f
            setPadding(0, 20, 0, 20)
        })

        root.addView(TextView(this).apply { text = "Fark grafiği (Saat - PC-60FW)"; textSize = 20f })
        root.addView(ReliabilityChartView(matches).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 420)
        })

        root.addView(TextView(this).apply {
            text = "Son eşleşmeler"
            textSize = 20f
            setPadding(0, 24, 0, 8)
        })
        if (matches.isEmpty()) {
            root.addView(TextView(this).apply { text = "Henüz eşzamanlı Watch-PC60 ölçümü yok."; textSize = 16f })
        } else {
            val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
            matches.takeLast(20).asReversed().forEach { m ->
                root.addView(TextView(this).apply {
                    val sign = if (m.diff > 0) "+" else ""
                    text = "${fmt.format(Date(m.timestampMs))}  •  Watch %${m.watch}  •  PC-60FW %${m.pc60}  •  fark $sign${m.diff}"
                    textSize = 16f
                    setPadding(0, 8, 0, 8)
                })
            }
        }
    }

    private fun addMetric(root: LinearLayout, label: String, value: String) {
        root.addView(TextView(this).apply {
            text = "$label: $value"
            textSize = 18f
            setPadding(0, 8, 0, 8)
        })
    }

    private class ReliabilityChartView(private val data: List<SpO2ReliabilityStore.Match>) : View(null) {
        constructor(context: android.content.Context, data: List<SpO2ReliabilityStore.Match>) : this(data) { }

        private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; style = Paint.Style.STROKE }
        private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val items = data.takeLast(30)
            val left = 70f
            val right = width - 24f
            val top = 24f
            val bottom = height - 48f
            canvas.drawLine(left, top, left, bottom, axis)
            val zeroY = (top + bottom) / 2f
            canvas.drawLine(left, zeroY, right, zeroY, axis)
            canvas.drawText("0", 18f, zeroY + 10f, text)
            if (items.isEmpty()) {
                canvas.drawText("Veri yok", left + 30f, zeroY, text)
                return
            }
            val maxAbs = max(5, items.maxOf { abs(it.diff) })
            val usableW = (right - left).coerceAtLeast(1f)
            val usableH = (bottom - top) / 2f
            var lastX = 0f
            var lastY = 0f
            items.forEachIndexed { index, m ->
                val x = if (items.size == 1) left + usableW / 2f else left + usableW * index / (items.size - 1)
                val y = zeroY - (m.diff.toFloat() / maxAbs.toFloat()) * usableH
                if (index > 0) canvas.drawLine(lastX, lastY, x, y, line)
                canvas.drawCircle(x, y, 6f, point)
                lastX = x
                lastY = y
            }
            canvas.drawText("+$maxAbs", 4f, top + 22f, text)
            canvas.drawText("-$maxAbs", 4f, bottom, text)
        }
    }
}
