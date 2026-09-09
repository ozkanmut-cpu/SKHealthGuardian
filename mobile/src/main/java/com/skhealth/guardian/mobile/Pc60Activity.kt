package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Pc60Activity : Activity() {
    private lateinit var root: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); UiStyle.applyBars(this); requestBlePermissions(); render() }
    override fun onResume() { super.onResume(); if (::root.isInitialized) render() }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQ_BLE)render()}
    private fun render() {
        root=UiStyle.page(this);setContentView(ScrollView(this).apply{setBackgroundColor(UiStyle.BG);addView(root)})
        val s=Pc60StatusStore.load(this);val now=System.currentTimeMillis();val age=if(s.lastPacketAt>0)(now-s.lastPacketAt).coerceAtLeast(0) else Long.MAX_VALUE;val fresh=age<=10_000L;val good=fresh&&s.spo2 in 1..100&&!s.probeOff&&!s.pulseSearching
        val signal=when{s.probeOff->"Parmak algılanmıyor";s.pulseSearching->"Nabız aranıyor";!fresh||s.spo2 !in 1..100->"Ölçüm bekleniyor";else->"Ölçüm güvenilir"};val signalColor=if(good)UiStyle.GREEN else UiStyle.AMBER;val last=if(s.lastPacketAt==0L)"yok" else SimpleDateFormat("HH:mm:ss",Locale("tr","TR")).format(Date(s.lastPacketAt));val battery=s.batteryLevel?.let{when(it){0->"0–25%";1->"25–50%";2->"50–75%";3->"75–100%";else->it.toString()}}?:"—"
        root.addView(UiStyle.detailHeader(this,"PC-60FW","Parmak oksimetresi • canlı izleme"))
        root.addView(UiStyle.card(this).apply{addView(UiStyle.text(this@Pc60Activity,"●  $signal",20f,signalColor,true));addView(UiStyle.text(this@Pc60Activity,"${s.state} • son veri ${ageText(age)}",14f,UiStyle.MUTED).apply{setPadding(0,6,0,0)})},UiStyle.sectionParams(this))
        val metrics=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;weightSum=2f};metrics.addView(metric("SpO₂",s.spo2?.let{"$it%"}?:"—",UiStyle.BLUE),LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginEnd=UiStyle.dp(this@Pc60Activity,6)});metrics.addView(metric("Nabız",s.heartRate?.let{"$it"}?:"—",UiStyle.RED,"bpm"),LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=UiStyle.dp(this@Pc60Activity,6)});root.addView(metrics,UiStyle.sectionParams(this))
        root.addView(UiStyle.card(this).apply{addView(UiStyle.text(this@Pc60Activity,"Sinyal ve cihaz",16f,UiStyle.TEXT,true));addView(UiStyle.text(this@Pc60Activity,"PI   ${s.perfusionIndex?.let{String.format(Locale.US,"%.1f%%",it)}?:"—"}  •  ${piLabel(s.perfusionIndex)}\nPil   $battery\nSon paket   $last\nCihaz   ${s.deviceName.ifBlank{"—"}}",15f,UiStyle.MUTED).apply{setPadding(0,10,0,0)})},UiStyle.sectionParams(this))
        val authoritative=SourcePriorityCoordinator.isPc60Authoritative(this);root.addView(UiStyle.card(this,if(authoritative)0xFF14241C.toInt() else UiStyle.SURFACE).apply{addView(UiStyle.text(this@Pc60Activity,if(authoritative)"PC-60FW ana alarm kaynağı" else "Galaxy Watch yedek kaynak",16f,if(authoritative)UiStyle.GREEN else UiStyle.AMBER,true));addView(UiStyle.text(this@Pc60Activity,if(authoritative)"Geçerli ve taze PC-60FW ölçümleri alarm kararında kullanılıyor." else "Geçerli PC-60FW verisi gelene kadar Galaxy Watch alarm değerlendirmesine devam eder.",14f,UiStyle.MUTED).apply{setPadding(0,6,0,0)})},UiStyle.sectionParams(this))
        root.addView(action("Bağlan / izlemeyi başlat","Bluetooth bağlantısını başlat",UiStyle.GREEN){if(!blePermissionsReady())requestBlePermissions() else ContextCompat.startForegroundService(this,Intent(this,Pc60BleService::class.java))},UiStyle.sectionParams(this));root.addView(action("Cihazı yeniden tara","PC-60FW için yeni tarama başlat",UiStyle.BLUE){if(!blePermissionsReady())requestBlePermissions() else ContextCompat.startForegroundService(this,Intent(this,Pc60BleService::class.java).setAction(Pc60BleService.ACTION_RESCAN))},UiStyle.sectionParams(this));root.addView(action("Durumu yenile","Ekrandaki canlı bilgileri güncelle",UiStyle.TEXT){render()},UiStyle.sectionParams(this));root.addView(action("İzlemeyi durdur","PC-60FW servisini kapat",UiStyle.RED){stopService(Intent(this,Pc60BleService::class.java));render()},UiStyle.sectionParams(this))
    }
    private fun metric(title:String,value:String,color:Int,unit:String="")=UiStyle.card(this).apply{gravity=Gravity.CENTER;addView(UiStyle.text(this@Pc60Activity,title,14f,UiStyle.MUTED,false,Gravity.CENTER));addView(UiStyle.text(this@Pc60Activity,value,38f,color,true,Gravity.CENTER));if(unit.isNotBlank())addView(UiStyle.text(this@Pc60Activity,unit,13f,UiStyle.MUTED,false,Gravity.CENTER))}
    private fun action(title:String,subtitle:String,color:Int,onClick:()->Unit)=UiStyle.card(this,UiStyle.SURFACE_2).apply{isClickable=true;isFocusable=true;addView(UiStyle.text(this@Pc60Activity,title,16f,color,true));addView(UiStyle.text(this@Pc60Activity,subtitle,13f,UiStyle.MUTED).apply{setPadding(0,4,0,0)});setOnClickListener{onClick()}}
    private fun piLabel(pi:Double?)=when{pi==null||pi<=0.0->"sinyal yok";pi<0.5->"zayıf perfüzyon";pi<1.0->"düşük perfüzyon";else->"uygun sinyal"};private fun ageText(age:Long)=when{age==Long.MAX_VALUE->"yok";age<5_000->"şimdi";age<60_000->"${age/1000} sn önce";else->"${age/60_000} dk önce"}
    private fun requestBlePermissions(){val wanted=if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION);val missing=wanted.filterNot{has(it)};if(missing.isNotEmpty())ActivityCompat.requestPermissions(this,missing.toTypedArray(),REQ_BLE)};private fun blePermissionsReady()=if(Build.VERSION.SDK_INT>=31)has(Manifest.permission.BLUETOOTH_SCAN)&&has(Manifest.permission.BLUETOOTH_CONNECT) else has(Manifest.permission.ACCESS_FINE_LOCATION);private fun has(permission:String)=ContextCompat.checkSelfPermission(this,permission)==PackageManager.PERMISSION_GRANTED
    companion object{private const val REQ_BLE=30}
}
