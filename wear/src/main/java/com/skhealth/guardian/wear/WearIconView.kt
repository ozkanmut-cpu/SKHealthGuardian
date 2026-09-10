package com.skhealth.guardian.wear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

enum class WearIcon { ALERT, BELL_OFF, REFRESH, HOME, CHEVRON_RIGHT, WATCH, SPO2, HEART }

class WearIconView(
    context: Context,
    var icon: WearIcon,
    var tint: Int
) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        stroke.color = tint
        fill.color = tint
        stroke.strokeWidth = s * .085f
        when (icon) {
            WearIcon.ALERT -> {
                val p = Path().apply { moveTo(s*.50f,s*.12f); lineTo(s*.88f,s*.82f); lineTo(s*.12f,s*.82f); close() }
                canvas.drawPath(p, stroke)
                canvas.drawLine(s*.50f,s*.35f,s*.50f,s*.60f,stroke)
                canvas.drawCircle(s*.50f,s*.70f,s*.04f,fill)
            }
            WearIcon.BELL_OFF -> {
                val p = Path().apply { moveTo(s*.25f,s*.68f); lineTo(s*.32f,s*.56f); lineTo(s*.32f,s*.40f); cubicTo(s*.32f,s*.18f,s*.68f,s*.18f,s*.68f,s*.40f); lineTo(s*.68f,s*.56f); lineTo(s*.75f,s*.68f); close() }
                canvas.drawPath(p, stroke)
                canvas.drawCircle(s*.50f,s*.76f,s*.06f,fill)
                canvas.drawLine(s*.18f,s*.18f,s*.82f,s*.82f,stroke)
            }
            WearIcon.REFRESH -> {
                canvas.drawArc(RectF(s*.20f,s*.20f,s*.80f,s*.80f),35f,250f,false,stroke)
                val p=Path().apply{moveTo(s*.76f,s*.18f);lineTo(s*.80f,s*.36f);lineTo(s*.62f,s*.31f);close()}
                canvas.drawPath(p,fill)
            }
            WearIcon.HOME -> {
                val p=Path().apply{moveTo(s*.16f,s*.48f);lineTo(s*.50f,s*.20f);lineTo(s*.84f,s*.48f);moveTo(s*.26f,s*.44f);lineTo(s*.26f,s*.80f);lineTo(s*.74f,s*.80f);lineTo(s*.74f,s*.44f)}
                canvas.drawPath(p,stroke)
            }
            WearIcon.CHEVRON_RIGHT -> { canvas.drawLine(s*.36f,s*.24f,s*.64f,s*.50f,stroke); canvas.drawLine(s*.64f,s*.50f,s*.36f,s*.76f,stroke) }
            WearIcon.WATCH -> { canvas.drawRoundRect(RectF(s*.28f,s*.22f,s*.72f,s*.78f),s*.12f,s*.12f,stroke); canvas.drawLine(s*.38f,s*.22f,s*.38f,s*.08f,stroke); canvas.drawLine(s*.62f,s*.22f,s*.62f,s*.08f,stroke); canvas.drawLine(s*.38f,s*.78f,s*.38f,s*.92f,stroke); canvas.drawLine(s*.62f,s*.78f,s*.62f,s*.92f,stroke) }
            WearIcon.SPO2 -> {
                canvas.drawLine(s*.50f,s*.18f,s*.50f,s*.58f,stroke)
                val l=Path().apply{moveTo(s*.47f,s*.35f);cubicTo(s*.34f,s*.28f,s*.18f,s*.42f,s*.18f,s*.70f);cubicTo(s*.18f,s*.84f,s*.36f,s*.83f,s*.46f,s*.70f);close()}
                val r=Path().apply{moveTo(s*.53f,s*.35f);cubicTo(s*.66f,s*.28f,s*.82f,s*.42f,s*.82f,s*.70f);cubicTo(s*.82f,s*.84f,s*.64f,s*.83f,s*.54f,s*.70f);close()}
                canvas.drawPath(l,stroke); canvas.drawPath(r,stroke)
            }
            WearIcon.HEART -> {
                val p=Path().apply{moveTo(s*.50f,s*.82f);cubicTo(s*.10f,s*.58f,s*.18f,s*.20f,s*.38f,s*.20f);cubicTo(s*.47f,s*.20f,s*.50f,s*.30f,s*.50f,s*.30f);cubicTo(s*.50f,s*.30f,s*.53f,s*.20f,s*.62f,s*.20f);cubicTo(s*.82f,s*.20f,s*.90f,s*.58f,s*.50f,s*.82f);close()}
                canvas.drawPath(p,fill)
            }
        }
        canvas.restore()
    }
}
