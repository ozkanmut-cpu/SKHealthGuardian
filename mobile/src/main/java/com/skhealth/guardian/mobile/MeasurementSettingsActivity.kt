package com.skhealth.guardian.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.*
import com.skhealth.guardian.shared.AlarmConfig

class MeasurementSettingsActivity : Activity() {
 private lateinit var root:LinearLayout
 private fun number(label:String,value:Int)=UiStyle.labeledField(this,label,value.toString(),true)
 private fun input(field:LinearLayout)=UiStyle.labeledFieldInput(field)
 private fun sectionHeader(icon:SkIcon,title:String,subtitle:String?=null,color:Int=UiStyle.TEXT)=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL
  addView(UiStyle.iconLabel(this@MeasurementSettingsActivity,icon,title,color,UiStyle.TEXT,22,19f,true))
  if(!subtitle.isNullOrBlank()) addView(UiStyle.text(this@MeasurementSettingsActivity,subtitle,14f,UiStyle.MUTED).apply{setPadding(UiStyle.dp(this@MeasurementSettingsActivity,31),UiStyle.dp(this@MeasurementSettingsActivity,6),0,0)})
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);UiStyle.applyBars(this);val cfg=AppSettings.load(this);root=UiStyle.page(this);setContentView(ScrollView(this).apply{setBackgroundColor(UiStyle.BG);addView(root)})
  root.addView(UiStyle.detailHeader(this,"Ölçüm ve alarm ayarları","Galaxy Watch ve PC-60FW SpO₂ kuralları ayrı yönetilir. Değişiklikler saate otomatik gönderilir."))

  val heart=UiStyle.card(this);heart.addView(sectionHeader(SkIcon.HEART,"Nabız",color=UiStyle.BLUE))
  val hrHigh=number("Yüksek nabız (bpm)",cfg.heartRateHighThreshold);val hrHighConfirm=number("Yüksek nabız doğrulama sayısı",cfg.heartRateHighConfirmCount);val hrLowEnabled=UiStyle.check(this,"Düşük nabız alarmı aktif",cfg.heartRateLowEnabled);val hrLow=number("Düşük nabız (bpm)",cfg.heartRateLowThreshold);val hrLowConfirm=number("Düşük nabız doğrulama sayısı",cfg.heartRateLowConfirmCount);listOf(hrHigh,hrHighConfirm).forEach(heart::addView);heart.addView(hrLowEnabled);listOf(hrLow,hrLowConfirm).forEach(heart::addView);root.addView(heart)

  val watch=UiStyle.card(this);watch.addView(sectionHeader(SkIcon.WATCH,"Galaxy Watch SpO₂","85 ve üzeri normal düzene döner; yalnızca düşük ölçümde yoğun takip başlar.",UiStyle.PURPLE))
  val watchImmediate=number("Ani alarm eşiği (< %)",AppSettings.watchSpo2ImmediateThreshold(this));val watchRecovery=number("Takibi bitir / normale dön (≥ %)",AppSettings.watchSpo2RecoveryThreshold(this));val watchConfirm=number("Düşük kalırsa alarm süresi (dk)",AppSettings.watchSpo2ConfirmationMinutes(this));val watchFollowup=number("Düşükken tekrar ölçüm aralığı (sn)",AppSettings.watchSpo2FollowupSeconds(this));val watchInterval=number("Normal ölçüm aralığı (dk)",AppSettings.watchMeasurementMinutes(this));listOf(watchImmediate,watchRecovery,watchConfirm,watchFollowup,watchInterval).forEach(watch::addView);root.addView(watch,UiStyle.sectionParams(this))

  val pc=UiStyle.card(this);pc.addView(sectionHeader(SkIcon.OXIMETER,"PC-60FW SpO₂","Sürekli veri kullanan iki aşamalı toparlanma mantığı.",UiStyle.GREEN))
  val pcImmediate=number("Ani alarm eşiği (< %)",AppSettings.pc60ImmediateThreshold(this));val pcIntermediate=number("3 dk hedefi (≥ %)",AppSettings.pc60IntermediateThreshold(this));val pcFullRecovery=number("5 dk tam toparlanma hedefi (≥ %)",AppSettings.pc60FullRecoveryThreshold(this));val pcEarly=number("İlk kontrol süresi (dk)",AppSettings.pc60EarlyWindowMinutes(this));val pcTotal=number("Tam toparlanma süresi (dk)",AppSettings.pc60TotalWindowMinutes(this));val pcStable=number("Tam toparlanma stabilizasyonu (sn)",AppSettings.pc60StableSeconds(this));listOf(pcImmediate,pcIntermediate,pcFullRecovery,pcEarly,pcTotal,pcStable).forEach(pc::addView);root.addView(pc,UiStyle.sectionParams(this))

  val technical=UiStyle.card(this);technical.addView(sectionHeader(SkIcon.STATUS_OK,"Teknik izleme","Bağlantı, veri gelmeme ve sensör tekrar süreleri",UiStyle.TEXT));val stale=number("Veri gelmeme alarmı (dk)",(cfg.staleDataMs/60000L).toInt());val watchHeartbeat=number("Saat bağlantı kesildi alarmı (dk)",AppSettings.watchHeartbeatTimeoutMinutes(this));val retry1=number("Teknik hata 1. tekrar (sn)",AppSettings.watchRetry1Seconds(this));val retry2=number("Teknik hata 2. tekrar (sn)",AppSettings.watchRetry2Seconds(this));listOf(stale,watchHeartbeat,retry1,retry2).forEach(technical::addView);root.addView(technical,UiStyle.sectionParams(this))

  val remote=UiStyle.card(this);remote.addView(sectionHeader(SkIcon.BELL,"Uzaktan uyarı","Yanıt alınmazsa tekrar uyarı ve reboot sonrası alarm geri yükleme sınırı",UiStyle.AMBER));val escalation=number("Yanıt yoksa tekrar uyar (dk, 0=kapalı)",AppSettings.escalationMinutes(this));val escalationMaxAge=number("Reboot sonrası eski alarmı geri yükleme üst sınırı (dk)",AppSettings.escalationMaxAgeMinutes(this));remote.addView(escalation);remote.addView(escalationMaxAge);root.addView(remote,UiStyle.sectionParams(this))

  root.addView(UiStyle.iconButton(this,SkIcon.STATUS_OK,"Tüm ayarları kaydet",true,UiStyle.GREEN).apply{setOnClickListener{
   val high=input(hrHigh).intOr(130);val highCount=input(hrHighConfirm).intOr(2);val lowHr=input(hrLow).intOr(45);val lowHrCount=input(hrLowConfirm).intOr(2)
   val wi=input(watchImmediate).intOr(75);val wr=input(watchRecovery).intOr(85);val wc=input(watchConfirm).intOr(3);val wf=input(watchFollowup).intOr(30);val intervalMin=input(watchInterval).intOr(5)
   val pi=input(pcImmediate).intOr(75);val pm=input(pcIntermediate).intOr(85);val pr=input(pcFullRecovery).intOr(90);val pe=input(pcEarly).intOr(3);val pt=input(pcTotal).intOr(5);val ps=input(pcStable).intOr(15)
   val staleMin=input(stale).intOr(10);val watchHeartbeatMin=input(watchHeartbeat).intOr(3);val retry1Sec=input(retry1).intOr(30);val retry2Sec=input(retry2).intOr(60);val escalationMin=input(escalation).intOr(0);val escalationMaxAgeMin=input(escalationMaxAge).intOr(60)
   if(high !in 60..240||lowHr !in 25..120||lowHr>=high||highCount !in 1..5||lowHrCount !in 1..5)return@setOnClickListener toast("Nabız ayarlarını kontrol et")
   if(wi !in 50..84||wr !in 51..99||wi>=wr||wc !in 1..10||wf !in 15..120||intervalMin !in 1..60)return@setOnClickListener toast("Galaxy Watch SpO₂ ayarlarını kontrol et")
   if(pi !in 50..84||pm !in 51..98||pr !in 52..99||!(pi<pm&&pm<pr)||pe !in 1..10||pt !in 2..20||pt<=pe||ps !in 3..120)return@setOnClickListener toast("PC-60FW SpO₂ ayarlarını kontrol et")
   if(staleMin !in 5..120||watchHeartbeatMin !in 2..30||retry1Sec !in 5..300||retry2Sec !in 5..600||retry2Sec<retry1Sec||escalationMin !in 0..60||escalationMaxAgeMin !in 5..1440)return@setOnClickListener toast("Süre ayarlarını kontrol et")

   AppSettings.setWatchSpo2ImmediateThreshold(this@MeasurementSettingsActivity,wi);AppSettings.setWatchSpo2RecoveryThreshold(this@MeasurementSettingsActivity,wr);AppSettings.setWatchSpo2ConfirmationMinutes(this@MeasurementSettingsActivity,wc);AppSettings.setWatchSpo2FollowupSeconds(this@MeasurementSettingsActivity,wf);AppSettings.setWatchMeasurementMinutes(this@MeasurementSettingsActivity,intervalMin)
   AppSettings.setPc60ImmediateThreshold(this@MeasurementSettingsActivity,pi);AppSettings.setPc60IntermediateThreshold(this@MeasurementSettingsActivity,pm);AppSettings.setPc60FullRecoveryThreshold(this@MeasurementSettingsActivity,pr);AppSettings.setPc60EarlyWindowMinutes(this@MeasurementSettingsActivity,pe);AppSettings.setPc60TotalWindowMinutes(this@MeasurementSettingsActivity,pt);AppSettings.setPc60StableSeconds(this@MeasurementSettingsActivity,ps)
   AppSettings.setEscalationMinutes(this@MeasurementSettingsActivity,escalationMin);AppSettings.setEscalationMaxAgeMinutes(this@MeasurementSettingsActivity,escalationMaxAgeMin);AppSettings.setWatchHeartbeatTimeoutMinutes(this@MeasurementSettingsActivity,watchHeartbeatMin);AppSettings.setWatchRetry1Seconds(this@MeasurementSettingsActivity,retry1Sec);AppSettings.setWatchRetry2Seconds(this@MeasurementSettingsActivity,retry2Sec)
   AppSettings.save(this@MeasurementSettingsActivity,AlarmConfig(cfg.spo2CriticalImmediate,cfg.spo2LowThreshold,cfg.spo2ConfirmCount,high,highCount,hrLowEnabled.isChecked,lowHr,lowHrCount,staleMin*60000L));WatchStatusStore.mark(this@MeasurementSettingsActivity,"CONFIG_BEKLENİYOR | saat ACK bekleniyor");toast("Ayarlar kaydedildi; saat onayı bekleniyor")
  }})
 }
 private fun EditText.intOr(default:Int)=text.toString().toIntOrNull()?:default
 private fun toast(text:String)=Toast.makeText(this,text,Toast.LENGTH_LONG).show()
}
