package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); UiStyle.applyBars(this)
        val root = UiStyle.page(this); setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
        val rows = HistoryStore.recent(this, 300).asReversed()
        root.addView(UiStyle.detailHeader(this,"Ölçüm geçmişi",if(rows.isEmpty()) "Henüz ölçüm kaydı yok" else "Son ${rows.size} kayıt • en yeni ölçüm üstte"))
        root.addView(UiStyle.button(this, "Grafikleri aç").apply { setOnClickListener { startActivity(Intent(this@HistoryActivity, HistoryChartActivity::class.java)) } })
        val fmt = SimpleDateFormat("dd.MM.yyyy • HH:mm:ss", Locale("tr", "TR"))
        if (rows.isEmpty()) {root.addView(UiStyle.card(this).apply {addView(UiStyle.text(this@HistoryActivity, "Ölçümler burada görünecek", 18f, UiStyle.TEXT, true));addView(UiStyle.text(this@HistoryActivity, "Galaxy Watch veya PC-60FW'den geçerli veri geldiğinde geçmiş otomatik oluşur.", 14f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@HistoryActivity, 7), 0, 0) })}, UiStyle.sectionParams(this));return}
        rows.forEach { r ->
            val source=sourceLabel(r.source);val spo2=r.spo2;val heartRate=r.heartRate;val accent=when{!r.valid->UiStyle.AMBER;spo2!=null&&spo2<90->UiStyle.RED;source=="PC-60FW"->UiStyle.GREEN;else->UiStyle.BLUE}
            val card=UiStyle.card(this);val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};header.addView(UiStyle.text(this,source,15f,accent,true),LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));header.addView(UiStyle.text(this,if(r.valid)"GEÇERLİ" else "GEÇERSİZ",12f,if(r.valid)UiStyle.GREEN else UiStyle.AMBER,true));card.addView(header)
            val metrics=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;weightSum=2f;setPadding(0,UiStyle.dp(this@HistoryActivity,12),0,0)};metrics.addView(metric("SpO₂",spo2?.let{"$it%"}?:"—",UiStyle.BLUE),LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginEnd=UiStyle.dp(this@HistoryActivity,6)});metrics.addView(metric("Nabız",heartRate?.toString()?:"—",UiStyle.RED,"bpm"),LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=UiStyle.dp(this@HistoryActivity,6)});card.addView(metrics);card.addView(UiStyle.text(this,fmt.format(Date(r.timestampMs)),13f,UiStyle.MUTED).apply{setPadding(0,UiStyle.dp(this@HistoryActivity,12),0,0)});root.addView(card,UiStyle.sectionParams(this,10))
        }
    }
    private fun metric(label:String,value:String,color:Int,unit:String="")=UiStyle.card(this).apply{gravity=Gravity.CENTER;addView(UiStyle.text(this@HistoryActivity,label,13f,UiStyle.MUTED,false,Gravity.CENTER));addView(UiStyle.text(this@HistoryActivity,value,30f,color,true,Gravity.CENTER));if(unit.isNotBlank())addView(UiStyle.text(this@HistoryActivity,unit,12f,UiStyle.MUTED,false,Gravity.CENTER))}
    private fun sourceLabel(source:String):String=when{source.contains("pc60",true)->"PC-60FW";source.contains("watch",true)->"Galaxy Watch";else->source.ifBlank{"Bilinmeyen kaynak"}}
}
