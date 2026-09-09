package com.skhealth.guardian.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import com.skhealth.guardian.shared.HealthReading

class HistoryChartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(HistoryChartView(this, HistoryStore.recent(this, 500)))
    }
}

private class HistoryChartView(
    context: android.content.Context,
    private val rows: List<HealthReading>
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; typeface = Typeface.DEFAULT_BOLD }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.WHITE)
        if (rows.isEmpty()) {
            canvas.drawText("Henüz ölçüm verisi yok", 40f, 80f, textPaint)
            return
        }

        val left = 70f
        val right = width.toFloat() - 30f
        val top1 = 80f
        val bottom1 = height * 0.45f
        val top2 = height * 0.55f
        val bottom2 = height.toFloat() - 80f
        val minTs = rows.minOf { it.timestampMs }
        val maxTs = rows.maxOf { it.timestampMs }.coerceAtLeast(minTs + 1)

        textPaint.color = android.graphics.Color.BLACK
        canvas.drawText("SpO₂ (%)", left, 45f, textPaint)
        canvas.drawText("Nabız (bpm)", left, top2 - 25f, textPaint)
        axisPaint.color = android.graphics.Color.GRAY
        canvas.drawRect(left, top1, right, bottom1, axisPaint)
        canvas.drawRect(left, top2, right, bottom2, axisPaint)

        drawSeries(canvas, rows.filter { it.spo2 != null }, left, right, top1, bottom1, minTs, maxTs, 70f, 100f, true)
        drawSeries(canvas, rows.filter { it.heartRate != null }, left, right, top2, bottom2, minTs, maxTs, 40f, 180f, false)
    }

    private fun drawSeries(
        canvas: Canvas,
        series: List<HealthReading>,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        minTs: Long,
        maxTs: Long,
        minY: Float,
        maxY: Float,
        spo2: Boolean
    ) {
        if (series.isEmpty()) return
        paint.color = if (spo2) android.graphics.Color.rgb(30, 110, 190) else android.graphics.Color.rgb(190, 60, 50)
        var lastX: Float? = null
        var lastY: Float? = null
        series.sortedBy { it.timestampMs }.forEach { r ->
            val value = (if (spo2) r.spo2 else r.heartRate)?.toFloat() ?: return@forEach
            val x = left + ((r.timestampMs - minTs).toFloat() / (maxTs - minTs).toFloat()) * (right - left)
            val clipped = value.coerceIn(minY, maxY)
            val y = bottom - ((clipped - minY) / (maxY - minY)) * (bottom - top)
            if (lastX != null && lastY != null) canvas.drawLine(lastX!!, lastY!!, x, y, paint)
            canvas.drawCircle(x, y, 4f, paint)
            lastX = x
            lastY = y
        }
    }
}
