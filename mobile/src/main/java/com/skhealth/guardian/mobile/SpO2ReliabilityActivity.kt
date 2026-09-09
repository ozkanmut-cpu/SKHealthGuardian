package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
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
        window.statusBarColor=UiStyle.BG; window.navigationBarColor=UiStyle.BG
        val s=SpO2ReliabilityStore.summary(this); val matches=SpO2ReliabilityStore.recentMatches(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(UiStyle.dp(this@SpO2ReliabilityActivity,20),UiStyle.dp(this@SpO2ReliabilityActivity,20),UiStyle.dp(this@SpO2ReliabilityActivity,20),UiStyle.dp(this@SpO2ReliabilityActivity,36));setBackgroundColor(UiStyle.BG)}
        setContentView(ScrollView(this).apply{setBackgroundColor(UiStyle.BG);addView(root)})
        root.addView(UiStyle.text(this,"Watch ↔ PC-60FW",28f,UiStyle.TEXT,true))
        root.addView(UiStyle.text(this,"Ölçüm doğrulama ve güvenilirlik analizi",14f,UiStyle.MUTED).apply{setPadding(0,4,0,18)})
        val mae=s.meanAbsoluteError?.let{String.format(Locale.US,"%.1f",it)}?:"—"; val bias=s.meanBias?.let{String.format(Locale.US,"%+.1f",it)}?:"—"; val hrMae=s.hrMeanAbsoluteError?.let{String.format(Locale.US,"%.1f",it)}?:"—"; val hrBias=s.hrMeanBias?.let{String.format(Locale.US,"%+.1f",it)}?:"—"
        val qualityColor=when{ s.count<5->UiStyle.AMBER;(s.meanAbsoluteError?:99.0)<=2.0->UiStyle.GREEN;else->UiStyle.AMBER }
        root.addView(UiStyle.card(this).apply{addView(UiStyle.text(this@SpO2ReliabilityActivity,s.label,20f,qualityColor,true));addView(UiStyle.text(this@SpO2ReliabilityActivity,"Aktif kaynak: ${SourcePriorityCoordinator.activeSourceLabel(this@SpO2ReliabilityActivity)}",14f,UiStyle.MUTED).apply{setPadding(0,6,0,0)})},UiStyle.sectionParams(this))
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;weightSum=2f}
        row.addView(statCard("SpO₂","$mae puan","Ort. mutlak fark\nSapma $bias",UiStyle.BLUE),LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginEnd=UiStyle.dp(this@SpO2ReliabilityActivity,6)})
        row.addView(statCard("Nabız","$hrMae bpm","Ort. mutlak fark\nSapma $hrBias",UiStyle.RED),LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=UiStyle.dp(this@SpO2ReliabilityActivity,6)})
        root.addView(row,UiStyle.sectionParams(this))
        val interpretation=when{s.count<5->"Daha güvenilir SpO₂ yorumu için en az 5 eşzamanlı ölçüm gerekiyor.";(s.meanAbsoluteError?:Double.MAX_VALUE)<=2.0->"Saat ile parmak oksimetresi arasında SpO₂ açısından güçlü uyum var.";(s.meanBias?:0.0)>=5.0->"Saat SpO₂'yi belirgin şekilde daha yüksek okuyor. PC-60FW önceliği korunur.";(s.meanBias?:0.0)<=-5.0->"Saat SpO₂'yi belirgin şekilde daha düşük okuyor. PC-60FW önceliği korunur.";else->"Ölçümler arasında fark var; daha fazla eşleşmeyle değerlendirme güçlenecek."}
        root.addView(UiStyle.card(this).apply{addView(UiStyle.text(this@SpO2ReliabilityActivity,"Değerlendirme",16f,UiStyle.TEXT,true));addView(UiStyle.text(this@SpO2ReliabilityActivity,interpretation,15f,UiStyle.MUTED).apply{setPadding(0,8,0,0)});addView(UiStyle.text(this@SpO2ReliabilityActivity,"Saat değeri kalibre edilmez veya değiştirilmez; bu ekran yalnız karşılaştırma yapar.",13f,UiStyle.MUTED).apply{setPadding(0,8,0,0)})},UiStyle.sectionParams(this))
        root.addView(UiStyle.text(this,"SpO₂ farkı • Watch − PC-60FW",17f,UiStyle.TEXT,true).apply{setPadding(0,8,0,8)})
        root.addView(ReliabilityChartView(this,matches).apply{background=UiStyle.rounded(UiStyle.SURFACE,UiStyle.dp(this@SpO2ReliabilityActivity,18).toFloat());layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,UiStyle.dp(this@SpO2ReliabilityActivity,230))},UiStyle.sectionParams(this))
        root.addView(UiStyle.text(this,"Son eşleşmeler",19f,UiStyle.TEXT,true).apply{setPadding(0,12,0,8)})
        if(matches.isEmpty()) root.addView(UiStyle.card(this).apply{addView(UiStyle.text(this@SpO2ReliabilityActivity,"Henüz eşzamanlı Watch-PC60 ölçümü yok.",15f,UiStyle.MUTED))},UiStyle.sectionParams(this)) else { val fmt=SimpleDateFormat("dd.MM • HH:mm:ss",Locale.forLanguageTag("tr-TR"));matches.takeLast(20).asReversed().forEach{m->val sign=if(m.diff>0) "+" else "";val hr=m.hrDiff?.let{d->"Nabız  ${m.watchHr} / ${m.pc60Hr} bpm  •  fark ${if(d>0) "+" else ""}$d"}?:"Nabız eşleşmesi yok";root.addView(UiStyle.card(this,UiStyle.SURFACE_2).apply{addView(UiStyle.text(this@SpO2ReliabilityActivity,fmt.format(Date(m.timestampMs)),13f,UiStyle.MUTED));addView(UiStyle.text(this@SpO2ReliabilityActivity,"SpO₂  Watch ${m.watch}%  /  PC60 ${m.pc60}%",16f,UiStyle.TEXT,true).apply{setPadding(0,5,0,0)});addView(UiStyle.text(this@SpO2ReliabilityActivity,"Fark $sign${m.diff} puan  •  $hr",14f,if(abs(m.diff)<=2) UiStyle.GREEN else UiStyle.AMBER).apply{setPadding(0,5,0,0)})},UiStyle.sectionParams(this))} }
    }
    private fun statCard(title:String,value:String,detail:String,color:Int)=UiStyle.card(this).apply{addView(UiStyle.text(this@SpO2ReliabilityActivity,title,14f,UiStyle.MUTED));addView(UiStyle.text(this@SpO2ReliabilityActivity,value,25f,color,true).apply{setPadding(0,5,0,2)});addView(UiStyle.text(this@SpO2ReliabilityActivity,detail,12f,UiStyle.MUTED))}
    private class ReliabilityChartView(context:Context,private val data:List<SpO2ReliabilityStore.Match>):View(context){private val axis=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=2f;color=UiStyle.MUTED};private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=4f;style=Paint.Style.STROKE;color=UiStyle.BLUE};private val point=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL;color=UiStyle.BLUE};private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=28f;color=UiStyle.MUTED};override fun onDraw(canvas:Canvas){super.onDraw(canvas);val items=data.takeLast(30);val left=70f;val right=width-24f;val top=28f;val bottom=height-48f;canvas.drawLine(left,top,left,bottom,axis);val zeroY=(top+bottom)/2f;canvas.drawLine(left,zeroY,right,zeroY,axis);canvas.drawText("0",18f,zeroY+10f,text);if(items.isEmpty()){canvas.drawText("Veri yok",left+30f,zeroY,text);return};val maxAbs=max(5,items.maxOf{abs(it.diff)});val usableW=(right-left).coerceAtLeast(1f);val usableH=(bottom-top)/2f;var lx=0f;var ly=0f;items.forEachIndexed{index,m->val x=if(items.size==1)left+usableW/2f else left+usableW*index/(items.size-1);val y=zeroY-(m.diff.toFloat()/maxAbs)*usableH;if(index>0)canvas.drawLine(lx,ly,x,y,line);canvas.drawCircle(x,y,6f,point);lx=x;ly=y};canvas.drawText("+$maxAbs",4f,top+22f,text);canvas.drawText("-$maxAbs",4f,bottom,text)}}
}
