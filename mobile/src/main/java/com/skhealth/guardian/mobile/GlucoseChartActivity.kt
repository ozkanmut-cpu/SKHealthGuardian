package com.skhealth.guardian.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.skhealth.guardian.shared.BloodGlucoseReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class GlucoseChartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)

        val root = UiStyle.page(this)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(UiStyle.BG)
            addView(root)
        })

        val rows = BloodGlucoseStore.confirmedOrko(this, 500).sortedBy { it.measuredAtMs }
        root.addView(
            UiStyle.detailHeader(
                this,
                "Şeker grafiği",
                if (rows.isEmpty()) "Henüz Orko olarak doğrulanmış ölçüm yok" else "Yalnızca Orko olarak doğrulanan ölçümler"
            )
        )

        if (rows.isEmpty()) {
            root.addView(UiStyle.card(this).apply {
                addView(UiStyle.text(this@GlucoseChartActivity, "Grafik oluşturmak için önce Accu-Chek ölçümlerini Orko olarak doğrulayın.", 14f, UiStyle.MUTED))
            }, UiStyle.sectionParams(this, 10))
            return
        }

        root.addView(summaryCard(rows), UiStyle.sectionParams(this, 8))
        root.addView(
            UiStyle.card(this, 17).apply {
                addView(
                    GlucoseChartView(this@GlucoseChartActivity, rows),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiStyle.dp(this@GlucoseChartActivity, 280))
                )
            },
            UiStyle.sectionParams(this, 12)
        )

        val latest = rows.takeLast(20).asReversed()
        root.addView(UiStyle.text(this, "Son ölçümler", 18f, UiStyle.TEXT, true), UiStyle.sectionParams(this, 16))
        val fmt = SimpleDateFormat("dd.MM • HH:mm", Locale("tr", "TR"))
        latest.forEach { r ->
            root.addView(UiStyle.card(this, 15).apply {
                val row = LinearLayout(this@GlucoseChartActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                row.addView(UiStyle.text(this@GlucoseChartActivity, fmt.format(Date(r.measuredAtMs)), 13f, UiStyle.MUTED), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(UiStyle.text(this@GlucoseChartActivity, "${r.valueMgDl} mg/dL", 15f, UiStyle.TEXT, true))
                addView(row)
            }, UiStyle.sectionParams(this, 7))
        }
    }

    private fun summaryCard(rows: List<BloodGlucoseReading>): LinearLayout {
        val values = rows.map { it.valueMgDl }
        val avg = values.average().toInt()
        val latest = rows.last()
        return UiStyle.card(this, 17).apply {
            addView(UiStyle.text(this@GlucoseChartActivity, "Genel özet", 17f, UiStyle.TEXT, true))
            addView(UiStyle.text(this@GlucoseChartActivity, "Son: ${latest.valueMgDl} mg/dL • Ortalama: $avg mg/dL • Min: ${values.minOrNull()} • Maks: ${values.maxOrNull()}", 13.5f, UiStyle.MUTED).apply {
                setPadding(0, UiStyle.dp(this@GlucoseChartActivity, 8), 0, 0)
            })
        }
    }
}

private class GlucoseChartView(
    context: android.content.Context,
    private val rows: List<BloodGlucoseReading>
) : View(context) {
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyle.MUTED
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyle.BLUE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyle.GREEN
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiStyle.MUTED
        textSize = 28f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rows.isEmpty()) return

        val left = 74f
        val right = width - 24f
        val top = 24f
        val bottom = height - 52f
        if (right <= left || bottom <= top) return

        val values = rows.map { it.valueMgDl }
        val minValue = max(0, (values.minOrNull() ?: 0) - 20)
        val maxValue = max(minValue + 40, (values.maxOrNull() ?: 100) + 20)
        val firstTime = rows.first().measuredAtMs
        val lastTime = rows.last().measuredAtMs
        val timeSpan = max(1L, lastTime - firstTime)

        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)

        val path = Path()
        rows.forEachIndexed { index, row ->
            val x = left + ((row.measuredAtMs - firstTime).toFloat() / timeSpan.toFloat()) * (right - left)
            val normalized = (row.valueMgDl - minValue).toFloat() / (maxValue - minValue).toFloat()
            val y = bottom - normalized * (bottom - top)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)

        rows.forEach { row ->
            val x = left + ((row.measuredAtMs - firstTime).toFloat() / timeSpan.toFloat()) * (right - left)
            val normalized = (row.valueMgDl - minValue).toFloat() / (maxValue - minValue).toFloat()
            val y = bottom - normalized * (bottom - top)
            canvas.drawCircle(x, y, 5f, pointPaint)
        }

        canvas.drawText(maxValue.toString(), 4f, top + 10f, textPaint)
        canvas.drawText(minValue.toString(), 4f, bottom, textPaint)
    }
}
