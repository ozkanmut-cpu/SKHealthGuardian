package com.skhealth.guardian.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

enum class SkIcon {
    HOME, CHARTS, EVENTS, SETTINGS,
    WATCH, OXIMETER, LINK, LINK_OFF, BATTERY, DEVICE_INFO,
    SPO2, HEART, THERMOMETER, BLOOD_PRESSURE, DROP, STEPS, SLEEP, FIRE,
    PLAY, CLOCK, CONTACTS, BELL, MEDICINE, CALENDAR,
    INFO, HELP, SHARE, LOGOUT,
    CHEVRON_RIGHT, CHEVRON_LEFT, PLUS, EDIT, DELETE, SEARCH, FILTER,
    DOWNLOAD, UPLOAD, REFRESH, MORE,
    STATUS_OK, STATUS_WARNING, STATUS_ALERT, STATUS_NONE, SYNC
}

class SkIconView(
    context: Context,
    var icon: SkIcon,
    var tint: Int = UiStyle.TEXT
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
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        canvas.save()
        canvas.translate(ox, oy)
        stroke.color = tint
        fill.color = tint
        stroke.strokeWidth = s * 0.085f
        val r = RectF(s * .12f, s * .12f, s * .88f, s * .88f)

        when (icon) {
            SkIcon.HOME -> {
                val p = Path().apply { moveTo(s*.16f,s*.48f); lineTo(s*.50f,s*.20f); lineTo(s*.84f,s*.48f); moveTo(s*.26f,s*.44f); lineTo(s*.26f,s*.80f); lineTo(s*.74f,s*.80f); lineTo(s*.74f,s*.44f); moveTo(s*.43f,s*.80f); lineTo(s*.43f,s*.60f); lineTo(s*.57f,s*.60f); lineTo(s*.57f,s*.80f) }
                canvas.drawPath(p, stroke)
            }
            SkIcon.CHARTS -> {
                canvas.drawRoundRect(RectF(s*.18f,s*.56f,s*.30f,s*.82f),s*.04f,s*.04f,fill)
                canvas.drawRoundRect(RectF(s*.43f,s*.34f,s*.55f,s*.82f),s*.04f,s*.04f,fill)
                canvas.drawRoundRect(RectF(s*.68f,s*.18f,s*.80f,s*.82f),s*.04f,s*.04f,fill)
            }
            SkIcon.EVENTS -> {
                for (y in listOf(.28f,.50f,.72f)) { canvas.drawCircle(s*.22f,s*y,s*.045f,fill); canvas.drawLine(s*.34f,s*y,s*.80f,s*y,stroke) }
            }
            SkIcon.SETTINGS -> gear(canvas,s)
            SkIcon.WATCH -> watch(canvas,s)
            SkIcon.OXIMETER -> oximeter(canvas,s)
            SkIcon.LINK -> link(canvas,s,false)
            SkIcon.LINK_OFF -> link(canvas,s,true)
            SkIcon.BATTERY -> battery(canvas,s)
            SkIcon.DEVICE_INFO -> {
                canvas.drawRoundRect(RectF(s*.24f,s*.12f,s*.76f,s*.88f),s*.08f,s*.08f,stroke)
                canvas.drawCircle(s*.50f,s*.28f,s*.045f,fill)
                canvas.drawLine(s*.50f,s*.40f,s*.50f,s*.68f,stroke)
            }
            SkIcon.SPO2 -> lungs(canvas,s)
            SkIcon.HEART -> heart(canvas,s)
            SkIcon.THERMOMETER -> thermometer(canvas,s)
            SkIcon.BLOOD_PRESSURE -> bloodPressure(canvas,s)
            SkIcon.DROP -> drop(canvas,s)
            SkIcon.STEPS -> steps(canvas,s)
            SkIcon.SLEEP -> moon(canvas,s)
            SkIcon.FIRE -> fire(canvas,s)
            SkIcon.PLAY -> {
                val p=Path().apply{moveTo(s*.32f,s*.22f);lineTo(s*.78f,s*.50f);lineTo(s*.32f,s*.78f);close()};canvas.drawPath(p,fill)
            }
            SkIcon.CLOCK -> clock(canvas,s)
            SkIcon.CONTACTS -> contacts(canvas,s)
            SkIcon.BELL -> bell(canvas,s)
            SkIcon.MEDICINE -> medicine(canvas,s)
            SkIcon.CALENDAR -> calendar(canvas,s)
            SkIcon.INFO -> info(canvas,s)
            SkIcon.HELP -> help(canvas,s)
            SkIcon.SHARE -> share(canvas,s)
            SkIcon.LOGOUT -> logout(canvas,s)
            SkIcon.CHEVRON_RIGHT -> chevron(canvas,s,true)
            SkIcon.CHEVRON_LEFT -> chevron(canvas,s,false)
            SkIcon.PLUS -> {canvas.drawLine(s*.22f,s*.50f,s*.78f,s*.50f,stroke);canvas.drawLine(s*.50f,s*.22f,s*.50f,s*.78f,stroke)}
            SkIcon.EDIT -> edit(canvas,s)
            SkIcon.DELETE -> delete(canvas,s)
            SkIcon.SEARCH -> search(canvas,s)
            SkIcon.FILTER -> filter(canvas,s)
            SkIcon.DOWNLOAD -> download(canvas,s,false)
            SkIcon.UPLOAD -> download(canvas,s,true)
            SkIcon.REFRESH, SkIcon.SYNC -> refresh(canvas,s)
            SkIcon.MORE -> {canvas.drawCircle(s*.25f,s*.5f,s*.055f,fill);canvas.drawCircle(s*.5f,s*.5f,s*.055f,fill);canvas.drawCircle(s*.75f,s*.5f,s*.055f,fill)}
            SkIcon.STATUS_OK -> status(canvas,s,0)
            SkIcon.STATUS_WARNING -> status(canvas,s,1)
            SkIcon.STATUS_ALERT -> status(canvas,s,2)
            SkIcon.STATUS_NONE -> {canvas.drawCircle(s*.50f,s*.50f,s*.30f,stroke);canvas.drawLine(s*.36f,s*.50f,s*.64f,s*.50f,stroke)}
        }
        canvas.restore()
    }

    private fun gear(c:Canvas,s:Float){c.drawCircle(s*.5f,s*.5f,s*.23f,stroke);c.drawCircle(s*.5f,s*.5f,s*.075f,stroke);for(i in 0..7){val a=Math.toRadians((i*45).toDouble());val x1=(s*.5f+kotlin.math.cos(a).toFloat()*s*.30f);val y1=(s*.5f+kotlin.math.sin(a).toFloat()*s*.30f);val x2=(s*.5f+kotlin.math.cos(a).toFloat()*s*.40f);val y2=(s*.5f+kotlin.math.sin(a).toFloat()*s*.40f);c.drawLine(x1,y1,x2,y2,stroke)}}
    private fun watch(c:Canvas,s:Float){c.drawRoundRect(RectF(s*.27f,s*.23f,s*.73f,s*.77f),s*.12f,s*.12f,stroke);c.drawLine(s*.38f,s*.23f,s*.38f,s*.08f,stroke);c.drawLine(s*.62f,s*.23f,s*.62f,s*.08f,stroke);c.drawLine(s*.38f,s*.77f,s*.38f,s*.92f,stroke);c.drawLine(s*.62f,s*.77f,s*.62f,s*.92f,stroke);c.drawCircle(s*.50f,s*.50f,s*.15f,stroke)}
    private fun oximeter(c:Canvas,s:Float){c.drawRoundRect(RectF(s*.28f,s*.12f,s*.72f,s*.88f),s*.12f,s*.12f,stroke);c.drawRoundRect(RectF(s*.36f,s*.25f,s*.64f,s*.53f),s*.05f,s*.05f,stroke);c.drawCircle(s*.50f,s*.70f,s*.05f,fill)}
    private fun link(c:Canvas,s:Float,off:Boolean){c.drawArc(RectF(s*.14f,s*.32f,s*.52f,s*.68f),120f,220f,false,stroke);c.drawArc(RectF(s*.48f,s*.32f,s*.86f,s*.68f),300f,220f,false,stroke);c.drawLine(s*.38f,s*.50f,s*.62f,s*.50f,stroke);if(off)c.drawLine(s*.20f,s*.20f,s*.80f,s*.80f,stroke)}
    private fun battery(c:Canvas,s:Float){c.drawRoundRect(RectF(s*.18f,s*.30f,s*.76f,s*.70f),s*.05f,s*.05f,stroke);c.drawRoundRect(RectF(s*.78f,s*.41f,s*.87f,s*.59f),s*.02f,s*.02f,fill);c.drawRect(RectF(s*.27f,s*.39f,s*.60f,s*.61f),fill)}
    private fun lungs(c:Canvas,s:Float){c.drawLine(s*.50f,s*.18f,s*.50f,s*.58f,stroke);val l=Path().apply{moveTo(s*.47f,s*.35f);cubicTo(s*.34f,s*.28f,s*.18f,s*.42f,s*.18f,s*.70f);cubicTo(s*.18f,s*.84f,s*.36f,s*.83f,s*.46f,s*.70f);close()};val r=Path().apply{moveTo(s*.53f,s*.35f);cubicTo(s*.66f,s*.28f,s*.82f,s*.42f,s*.82f,s*.70f);cubicTo(s*.82f,s*.84f,s*.64f,s*.83f,s*.54f,s*.70f);close()};c.drawPath(l,stroke);c.drawPath(r,stroke)}
    private fun heart(c:Canvas,s:Float){val p=Path().apply{moveTo(s*.50f,s*.82f);cubicTo(s*.10f,s*.58f,s*.18f,s*.20f,s*.38f,s*.20f);cubicTo(s*.47f,s*.20f,s*.50f,s*.30f,s*.50f,s*.30f);cubicTo(s*.50f,s*.30f,s*.53f,s*.20f,s*.62f,s*.20f);cubicTo(s*.82f,s*.20f,s*.90f,s*.58f,s*.50f,s*.82f);close()};c.drawPath(p,fill)}
    private fun thermometer(c:Canvas,s:Float){c.drawRoundRect(RectF(s*.40f,s*.12f,s*.60f,s*.68f),s*.10f,s*.10f,stroke);c.drawCircle(s*.50f,s*.73f,s*.16f,stroke);c.drawLine(s*.50f,s*.32f,s*.50f,s*.70f,stroke)}
    private fun bloodPressure(c:Canvas,s:Float){heart(c,s*.72f);c.save();c.translate(s*.36f,s*.30f);c.drawCircle(s*.35f,s*.35f,s*.18f,stroke);c.drawLine(s*.35f,s*.35f,s*.45f,s*.26f,stroke);c.restore()}
    private fun drop(c:Canvas,s:Float){val p=Path().apply{moveTo(s*.50f,s*.12f);cubicTo(s*.36f,s*.34f,s*.24f,s*.48f,s*.24f,s*.64f);cubicTo(s*.24f,s*.84f,s*.40f,s*.90f,s*.50f,s*.90f);cubicTo(s*.60f,s*.90f,s*.76f,s*.84f,s*.76f,s*.64f);cubicTo(s*.76f,s*.48f,s*.64f,s*.34f,s*.50f,s*.12f);close()};c.drawPath(p,fill)}
    private fun steps(c:Canvas,s:Float){c.drawOval(RectF(s*.20f,s*.20f,s*.42f,s*.60f),fill);c.drawOval(RectF(s*.55f,s*.40f,s*.77f,s*.80f),fill);c.drawCircle(s*.18f,s*.16f,s*.04f,fill);c.drawCircle(s*.26f,s*.11f,s*.04f,fill);c.drawCircle(s*.34f,s*.12f,s*.04f,fill);c.drawCircle(s*.63f,s*.35f,s*.04f,fill);c.drawCircle(s*.71f,s*.31f,s*.04f,fill);c.drawCircle(s*.79f,s*.33f,s*.04f,fill)}
    private fun moon(c:Canvas,s:Float){c.drawCircle(s*.48f,s*.50f,s*.30f,fill);val old=fill.color;fill.color=UiStyle.BG;c.drawCircle(s*.61f,s*.40f,s*.28f,fill);fill.color=old}
    private fun fire(c:Canvas,s:Float){val p=Path().apply{moveTo(s*.52f,s*.10f);cubicTo(s*.58f,s*.32f,s*.80f,s*.36f,s*.78f,s*.62f);cubicTo(s*.76f,s*.84f,s*.60f,s*.90f,s*.50f,s*.90f);cubicTo(s*.30f,s*.90f,s*.18f,s*.76f,s*.22f,s*.58f);cubicTo(s*.26f,s*.44f,s*.40f,s*.36f,s*.42f,s*.18f);cubicTo(s*.46f,s*.26f,s*.50f,s*.24f,s*.52f,s*.10f);close()};c.drawPath(p,fill)}
    private fun clock(c:Canvas,s:Float){c.drawCircle(s*.50f,s*.50f,s*.32f,stroke);c.drawLine(s*.50f,s*.50f,s*.50f,s*.30f,stroke);c.drawLine(s*.50f,s*.50f,s*.66f,s*.60f,stroke)}
    private fun contacts(c:Canvas,s:Float){c.drawCircle(s*.37f,s*.35f,s*.13f,fill);c.drawCircle(s*.67f,s*.38f,s*.10f,fill);c.drawArc(RectF(s*.14f,s*.45f,s*.60f,s*.88f),180f,180f,false,stroke);c.drawArc(RectF(s*.49f,s*.50f,s*.86f,s*.86f),180f,180f,false,stroke)}
    private fun bell(c:Canvas,s:Float){val p=Path().apply{moveTo(s*.25f,s*.68f);lineTo(s*.32f,s*.56f);lineTo(s*.32f,s*.40f);cubicTo(s*.32f,s*.18f,s*.68f,s*.18f,s*.68f,s*.40f);lineTo(s*.68f,s*.56f);lineTo(s*.75f,s*.68f);close()};c.drawPath(p,stroke);c.drawCircle(s*.50f,s*.76f,s*.06f,fill)}
    private fun medicine(c:Canvas,s:Float){c.save();c.rotate(-45f,s*.5f,s*.5f);c.drawRoundRect(RectF(s*.24f,s*.40f,s*.76f,s*.60f),s*.10f,s*.10f,stroke);c.drawLine(s*.50f,s*.40f,s*.50f,s*.60f,stroke);c.restore()}
    private fun calendar(c:Canvas,s:Float){c.drawRoundRect(RectF(s*.18f,s*.22f,s*.82f,s*.82f),s*.07f,s*.07f,stroke);c.drawLine(s*.18f,s*.38f,s*.82f,s*.38f,stroke);c.drawLine(s*.34f,s*.14f,s*.34f,s*.30f,stroke);c.drawLine(s*.66f,s*.14f,s*.66f,s*.30f,stroke)}
    private fun info(c:Canvas,s:Float){c.drawCircle(s*.50f,s*.50f,s*.32f,stroke);c.drawCircle(s*.50f,s*.34f,s*.045f,fill);c.drawLine(s*.50f,s*.46f,s*.50f,s*.68f,stroke)}
    private fun help(c:Canvas,s:Float){c.drawCircle(s*.50f,s*.50f,s*.32f,stroke);c.drawArc(RectF(s*.37f,s*.27f,s*.63f,s*.52f),200f,230f,false,stroke);c.drawLine(s*.50f,s*.52f,s*.50f,s*.61f,stroke);c.drawCircle(s*.50f,s*.72f,s*.04f,fill)}
    private fun share(c:Canvas,s:Float){val pts=arrayOf(.28f to .50f,.68f to .25f,.68f to .75f);pts.forEach{c.drawCircle(s*it.first,s*it.second,s*.08f,stroke)};c.drawLine(s*.35f,s*.46f,s*.60f,s*.30f,stroke);c.drawLine(s*.35f,s*.54f,s*.60f,s*.70f,stroke)}
    private fun logout(c:Canvas,s:Float){c.drawArc(RectF(s*.16f,s*.20f,s*.62f,s*.80f),90f,180f,false,stroke);c.drawLine(s*.46f,s*.50f,s*.84f,s*.50f,stroke);c.drawLine(s*.70f,s*.36f,s*.84f,s*.50f,stroke);c.drawLine(s*.70f,s*.64f,s*.84f,s*.50f,stroke)}
    private fun chevron(c:Canvas,s:Float,right:Boolean){if(right){c.drawLine(s*.36f,s*.24f,s*.62f,s*.50f,stroke);c.drawLine(s*.62f,s*.50f,s*.36f,s*.76f,stroke)}else{c.drawLine(s*.64f,s*.24f,s*.38f,s*.50f,stroke);c.drawLine(s*.38f,s*.50f,s*.64f,s*.76f,stroke)}}
    private fun edit(c:Canvas,s:Float){c.save();c.rotate(-45f,s*.5f,s*.5f);c.drawRoundRect(RectF(s*.42f,s*.16f,s*.58f,s*.76f),s*.04f,s*.04f,stroke);c.drawLine(s*.42f,s*.68f,s*.58f,s*.68f,stroke);c.restore()}
    private fun delete(c:Canvas,s:Float){c.drawRoundRect(RectF(s*.30f,s*.32f,s*.70f,s*.82f),s*.04f,s*.04f,stroke);c.drawLine(s*.24f,s*.28f,s*.76f,s*.28f,stroke);c.drawLine(s*.40f,s*.20f,s*.60f,s*.20f,stroke);c.drawLine(s*.42f,s*.42f,s*.42f,s*.70f,stroke);c.drawLine(s*.58f,s*.42f,s*.58f,s*.70f,stroke)}
    private fun search(c:Canvas,s:Float){c.drawCircle(s*.43f,s*.43f,s*.24f,stroke);c.drawLine(s*.60f,s*.60f,s*.80f,s*.80f,stroke)}
    private fun filter(c:Canvas,s:Float){c.drawLine(s*.18f,s*.28f,s*.82f,s*.28f,stroke);c.drawLine(s*.30f,s*.50f,s*.70f,s*.50f,stroke);c.drawLine(s*.42f,s*.72f,s*.58f,s*.72f,stroke)}
    private fun download(c:Canvas,s:Float,up:Boolean){val y1=if(up)s*.66f else s*.24f;val y2=if(up)s*.24f else s*.66f;c.drawLine(s*.50f,y1,s*.50f,y2,stroke);if(up){c.drawLine(s*.50f,s*.24f,s*.36f,s*.38f,stroke);c.drawLine(s*.50f,s*.24f,s*.64f,s*.38f,stroke)}else{c.drawLine(s*.50f,s*.66f,s*.36f,s*.52f,stroke);c.drawLine(s*.50f,s*.66f,s*.64f,s*.52f,stroke)};c.drawLine(s*.24f,s*.80f,s*.76f,s*.80f,stroke)}
    private fun refresh(c:Canvas,s:Float){c.drawArc(RectF(s*.20f,s*.20f,s*.80f,s*.80f),35f,270f,false,stroke);c.drawLine(s*.70f,s*.20f,s*.82f,s*.26f,stroke);c.drawLine(s*.82f,s*.26f,s*.79f,s*.13f,stroke)}
    private fun status(c:Canvas,s:Float,type:Int){c.drawCircle(s*.5f,s*.5f,s*.32f,stroke);when(type){0->{c.drawLine(s*.32f,s*.50f,s*.45f,s*.63f,stroke);c.drawLine(s*.45f,s*.63f,s*.70f,s*.37f,stroke)}1->{c.drawLine(s*.50f,s*.30f,s*.50f,s*.56f,stroke);c.drawCircle(s*.50f,s*.68f,s*.04f,fill)}else->{val p=Path().apply{moveTo(s*.50f,s*.18f);lineTo(s*.82f,s*.78f);lineTo(s*.18f,s*.78f);close()};c.drawPath(p,stroke);c.drawLine(s*.50f,s*.38f,s*.50f,s*.58f,stroke);c.drawCircle(s*.50f,s*.68f,s*.04f,fill)}}}
}
