package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat

class SystemHealthActivity : Activity() {
    private lateinit var root: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); UiStyle.applyBars(this); render() }
    override fun onResume() { super.onResume(); if (::root.isInitialized) render() }

    private fun render() {
        root = UiStyle.page(this)
        val now = System.currentTimeMillis()
        val stale = AppSettings.load(this).staleDataMs
        val lastReading = MonitoringState.lastReading(this)
        val heartbeat = WatchHeartbeatStore.timestamp(this)
        val watchBattery = WatchHeartbeatStore.battery(this)
        val watchConnected = SourcePriorityCoordinator.isWatchConnected(this, now)
        val phoneBattery = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val checks = listOf(
            Check("SMS izni", has(Manifest.permission.SEND_SMS), SkIcon.BELL),
            Check("Arama izni", has(Manifest.permission.CALL_PHONE), SkIcon.CONTACTS),
            Check("Bildirim izni", Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS), SkIcon.BELL),
            Check("Bluetooth bağlantısı", Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT), SkIcon.LINK),
            Check("Tam ekran alarm", canUseFullScreenIntent(), SkIcon.STATUS_ALERT),
            Check("Pil optimizasyonu", isIgnoringBatteryOptimization(), SkIcon.BATTERY),
            Check("Galaxy Watch bağlantısı", watchConnected, SkIcon.WATCH),
            Check("Galaxy Watch veri akışı", lastReading > 0 && now - lastReading <= stale && heartbeat > 0 && now - heartbeat <= 3 * 60_000L, SkIcon.SYNC)
        )
        val okCount = checks.count { it.ok }
        root.addView(UiStyle.detailHeader(this, "Sistem sağlığı", "İzinler, bağlantılar ve arka plan çalışma koşullarını tek ekrandan kontrol et."))

        val summary = UiStyle.card(this)
        val ready = okCount == checks.size
        val source = SourcePriorityCoordinator.activeSourceLabel(this, now)
        val sourceText = if (source == "Aktif kaynak yok") "Yok" else source
        summary.background = UiStyle.rounded(if (ready) 0xFF123222.toInt() else 0xFF332719.toInt(), context = this)
        summary.addView(UiStyle.iconLabel(this, if(ready) SkIcon.STATUS_OK else SkIcon.STATUS_WARNING, if (ready) "SİSTEM HAZIR" else "$okCount/${checks.size} kontrol başarılı", if(ready)UiStyle.GREEN else UiStyle.AMBER, if(ready)UiStyle.GREEN else UiStyle.AMBER, 28, 20f, true, 11))
        summary.addView(UiStyle.iconLabel(this, SkIcon.DEVICE_INFO, "Alarm kaynağı: $sourceText", UiStyle.MUTED, UiStyle.TEXT, 18, 15f, false, 8).apply { setPadding(0, UiStyle.dp(this@SystemHealthActivity, 10), 0, 0) })
        root.addView(summary)

        val checksCard = UiStyle.card(this)
        checksCard.addView(UiStyle.iconLabel(this,SkIcon.INFO,"Kontroller",UiStyle.BLUE,UiStyle.TEXT,22,19f,true,9))
        checks.forEach { check ->
            val color=if(check.ok)UiStyle.GREEN else UiStyle.RED
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,UiStyle.dp(this@SystemHealthActivity,9),0,0)}
            row.addView(UiStyle.icon(this,check.icon,20,UiStyle.MUTED,check.label),LinearLayout.LayoutParams(UiStyle.dp(this,24),UiStyle.dp(this,24)))
            row.addView(UiStyle.text(this,check.label,16f,UiStyle.TEXT).apply{setPadding(UiStyle.dp(this@SystemHealthActivity,9),0,0,0)},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            row.addView(UiStyle.icon(this,if(check.ok)SkIcon.STATUS_OK else SkIcon.STATUS_ALERT,20,color,if(check.ok)"Geçti" else "Sorun"))
            checksCard.addView(row)
        }
        root.addView(checksCard, UiStyle.sectionParams(this))

        val device = UiStyle.card(this)
        device.addView(UiStyle.iconLabel(this,SkIcon.DEVICE_INFO,"Cihaz durumu",UiStyle.BLUE,UiStyle.TEXT,22,19f,true,9))
        val watchState = if (watchConnected) "Bağlı" else "Bağlı değil"
        device.addView(UiStyle.iconLabel(this,SkIcon.WATCH,"Galaxy Watch: $watchState",if(watchConnected)UiStyle.GREEN else UiStyle.MUTED,UiStyle.TEXT,20,15f,false,8).apply{setPadding(0,UiStyle.dp(this@SystemHealthActivity,10),0,0)})
        device.addView(UiStyle.iconLabel(this,SkIcon.BATTERY,"Saat pili: ${if (watchBattery >= 0) "%$watchBattery" else "—"}",UiStyle.GREEN,UiStyle.MUTED,18,14f,false,8).apply{setPadding(0,UiStyle.dp(this@SystemHealthActivity,7),0,0)})
        device.addView(UiStyle.iconLabel(this,SkIcon.BATTERY,"Telefon pili: %$phoneBattery",UiStyle.GREEN,UiStyle.MUTED,18,14f,false,8).apply{setPadding(0,UiStyle.dp(this@SystemHealthActivity,7),0,0)})
        root.addView(device, UiStyle.sectionParams(this))

        val reliability = UiStyle.card(this)
        reliability.addView(UiStyle.iconLabel(this,SkIcon.SYNC,"Watch doğrulama",UiStyle.BLUE,UiStyle.TEXT,22,19f,true,9))
        reliability.addView(UiStyle.text(this, SpO2ReliabilityStore.formatted(this), 15f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@SystemHealthActivity, 8), 0, 0) })
        reliability.isClickable=true; reliability.isFocusable=true
        reliability.setOnClickListener { startActivity(Intent(this, SpO2ReliabilityActivity::class.java)) }
        root.addView(reliability, UiStyle.sectionParams(this))

        root.addView(actionButton(SkIcon.SETTINGS,"Uygulama izinlarını aç") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })
        root.addView(actionButton(SkIcon.BATTERY,"Pil optimizasyonu ayarını aç") { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        if (Build.VERSION.SDK_INT >= 34) root.addView(actionButton(SkIcon.STATUS_ALERT,"Tam ekran alarm ayarını aç") { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) })
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
    }

    private fun actionButton(icon:SkIcon,label:String,onClick:()->Unit)=UiStyle.card(this,14).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isClickable=true;isFocusable=true;setOnClickListener{onClick()}
        addView(UiStyle.icon(this@SystemHealthActivity,icon,22,UiStyle.BLUE,label),LinearLayout.LayoutParams(UiStyle.dp(this@SystemHealthActivity,28),UiStyle.dp(this@SystemHealthActivity,28)))
        addView(UiStyle.text(this@SystemHealthActivity,label,15.5f,UiStyle.TEXT,true).apply{setPadding(UiStyle.dp(this@SystemHealthActivity,10),0,0,0)},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        addView(UiStyle.icon(this@SystemHealthActivity,SkIcon.CHEVRON_RIGHT,20,UiStyle.MUTED,"Aç"))
        layoutParams=UiStyle.sectionParams(this@SystemHealthActivity,10)
    }

    private data class Check(val label:String,val ok:Boolean,val icon:SkIcon)
    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun isIgnoringBatteryOptimization() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    private fun canUseFullScreenIntent() = Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
