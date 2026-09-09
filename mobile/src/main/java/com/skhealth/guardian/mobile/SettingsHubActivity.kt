package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

class SettingsHubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);UiStyle.applyBars(this)
        val shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(UiStyle.BG)}
        val root=UiStyle.page(this);shell.addView(ScrollView(this).apply{setBackgroundColor(UiStyle.BG);addView(root)},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));shell.addView(UiStyle.appBottomNavigation(this,"settings"));setContentView(shell)
        root.addView(UiStyle.title(this,"Ayarlar"));root.addView(UiStyle.subtitle(this,"Alarm, cihaz ve uygulama ayarları"))
        root.addView(UiStyle.sectionTitle(this,"İzleme"));root.addView(sectionCard(Triple("Alarm ayarları","SpO₂, nabız ve doğrulama süreleri",MeasurementSettingsActivity::class.java),Triple("Cihazlar","Galaxy Watch ve PC-60FW bağlantıları",DevicesActivity::class.java)))
        root.addView(UiStyle.sectionTitle(this,"Uyarılar"));root.addView(sectionCard(Triple("Acil durum kişileri","Arama ve SMS kişileri",ContactsActivity::class.java),Triple("SMS / arama kayıtları","Teslim ve çağrı durumları",DeliveryLogActivity::class.java)))
        root.addView(UiStyle.sectionTitle(this,"Uygulama"));root.addView(sectionCard(Triple("Veri yönetimi","Yedekleme, CSV ve geri yükleme",ExportActivity::class.java),Triple("Sistem sağlık kontrolü","İzinler ve arka plan servisleri",SystemHealthActivity::class.java),Triple("Kurulum / QA","İzin, bağlantı ve alarm testleri",SystemTestActivity::class.java)))
    }
    private fun sectionCard(vararg items:Triple<String,String,Class<out Activity>>):LinearLayout=UiStyle.card(this,6).apply{items.forEachIndexed{index,item->addItem(this,item.first,item.second,item.third,index!=items.lastIndex)}}
    private fun addItem(parent:LinearLayout,title:String,subtitle:String,target:Class<out Activity>,divider:Boolean){val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(UiStyle.dp(this@SettingsHubActivity,12),UiStyle.dp(this@SettingsHubActivity,15),UiStyle.dp(this@SettingsHubActivity,12),UiStyle.dp(this@SettingsHubActivity,15));isClickable=true;isFocusable=true;setOnClickListener{startActivity(Intent(this@SettingsHubActivity,target))}};val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};labels.addView(UiStyle.text(this,title,15.5f,UiStyle.TEXT,true));labels.addView(UiStyle.text(this,subtitle,12.5f,UiStyle.MUTED).apply{setPadding(0,UiStyle.dp(this@SettingsHubActivity,5),0,0)});row.addView(labels,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));row.addView(UiStyle.text(this,"›",24f,UiStyle.MUTED));parent.addView(row);if(divider)parent.addView(UiStyle.divider(this))}
}
