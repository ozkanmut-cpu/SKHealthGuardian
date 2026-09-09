package com.skhealth.guardian.mobile

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.skhealth.guardian.shared.HealthReading

class HistoryChartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);UiStyle.applyBars(this)
        val shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(UiStyle.BG)}
        shell.addView(HistoryChartView(this, HistoryStore.recent(this, 500)),LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        shell.addView(UiStyle.appBottomNavigation(this,"charts"));setContentView(shell)
    }
}

private class HistoryChartView(context: android.content.Context, private val rows: List<HealthReading>) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f * density; style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f * density; typeface = Typeface.DEFAULT_BOLD }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * density }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f * density; style = Paint.Style.STROKE }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f * density }
    init {setBackgroundColor(UiStyle.BG);setPadding((16*density).toInt(),(16*density).toInt(),(16*density).toInt(),(16*density).toInt())}
    override fun onDraw(canvas: Canvas) {super.onDraw(canvas);canvas.drawColor(UiStyle.BG);textPaint.color=UiStyle.TEXT;labelPaint.color=UiStyle.MUTED;canvas.drawText("Ölçüm geçmişi ve grafikler",24f*density,42f*density,textPaint);canvas.drawText("Son kayıtlı Watch / PC-60FW ölçümleri",24f*density,66f*density,labelPaint);if(rows.isEmpty()){val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=UiStyle.MUTED;textSize=15f*density};canvas.drawText("Henüz ölçüm verisi yok",24f*density,115f*density,p);return};val left=56f*density;val right=width.toFloat()-22f*density;val top1=108f*density;val bottom1=height*0.47f;val top2=height*0.57f;val bottom2=height.toFloat()-54f*density;val minTs=rows.minOf{it.timestampMs};val maxTs=rows.maxOf{it.timestampMs}.coerceAtLeast(minTs+1);drawPanel(canvas,left,right,top1,bottom1,"SpO₂ (%)",70,100);drawPanel(canvas,left,right,top2,bottom2,"Nabız (bpm)",40,180);drawSeries(canvas,rows.filter{it.spo2!=null},left,right,top1,bottom1,minTs,maxTs,70f,100f,true);drawSeries(canvas,rows.filter{it.heartRate!=null},left,right,top2,bottom2,minTs,maxTs,40f,180f,false)}
    private fun drawPanel(canvas:Canvas,left:Float,right:Float,top:Float,bottom:Float,title:String,min:Int,max:Int){val panel=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=UiStyle.SURFACE;style=Paint.Style.FILL};canvas.drawRoundRect(left-12f*density,top-34f*density,right+8f*density,bottom+14f*density,18f*density,18f*density,panel);textPaint.color=UiStyle.TEXT;textPaint.textSize=14f*density;canvas.drawText(title,left,top-10f*density,textPaint);axisPaint.color=0xFF34404C.toInt();gridPaint.color=0xFF25303A.toInt();for(i in 0..3){val y=top+(bottom-top)*(i/3f);canvas.drawLine(left,y,right,y,gridPaint);labelPaint.color=UiStyle.MUTED;canvas.drawText((max-((max-min)*(i/3f))).toInt().toString(),left-38f*density,y+4f*density,labelPaint)};canvas.drawRect(left,top,right,bottom,axisPaint)}
    private fun drawSeries(canvas:Canvas,series:List<HealthReading>,left:Float,right:Float,top:Float,bottom:Float,minTs:Long,maxTs:Long,minY:Float,maxY:Float,spo2:Boolean){if(series.isEmpty())return;paint.color=if(spo2)UiStyle.BLUE else UiStyle.RED;var lx:Float?=null;var ly:Float?=null;series.sortedBy{it.timestampMs}.forEach{r->val value=(if(spo2)r.spo2 else r.heartRate)?.toFloat()?:return@forEach;val x=left+((r.timestampMs-minTs).toFloat()/(maxTs-minTs).toFloat())*(right-left);val y=bottom-((value.coerceIn(minY,maxY)-minY)/(maxY-minY))*(bottom-top);if(lx!=null&&ly!=null)canvas.drawLine(lx!!,ly!!,x,y,paint);canvas.drawCircle(x,y,2.8f*density,paint);lx=x;ly=y}}
}
