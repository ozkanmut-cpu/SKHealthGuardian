package com.skhealth.guardian.wear

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class WearAlarmActivity : Activity() {
    private val bg = Color.rgb(13,17,23)
    private val surface = Color.rgb(23,29,36)
    private val surface2 = Color.rgb(32,40,50)
    private val text = Color.rgb(243,246,249)
    private val muted = Color.rgb(151,162,174)
    private val red = Color.rgb(255,92,92)
    private val amber = Color.rgb(255,183,77)
    private val blue = Color.rgb(66,165,245)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Sağlık alarmı"
        val spo2 = intent.getIntExtra(EXTRA_SPO2, -1)
        val hr = intent.getIntExtra(EXTRA_HR, -1)
        val compact = resources.configuration.screenWidthDp < 220 || resources.configuration.fontScale >= 1.25f
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(if(compact) 12 else 16), dp(12), dp(if(compact) 12 else 16), dp(28))
        }

        val banner = card(Color.rgb(58,23,26)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        banner.addView(label("⚠  SAĞLIK ALARMI", if(compact) 15f else 17f, red, true).apply { gravity = Gravity.CENTER })
        banner.addView(label(reason, if(compact) 18f else 21f, text, true).apply { gravity = Gravity.CENTER; setPadding(0,dp(8),0,0) })
        root.addView(banner)

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        metrics.addView(metricCard("SpO₂", if(spo2 >= 0) "$spo2%" else "—", red), LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply { marginEnd=dp(4) })
        metrics.addView(metricCard("Nabız", if(hr >= 0) hr.toString() else "—", if(hr >= 0) amber else muted, "bpm"), LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply { marginStart=dp(4) })
        root.addView(metrics, sectionParams(6))

        root.addView(card().apply {
            addView(label("Kaynak",11f,muted))
            addView(label("Galaxy Watch",14f,text,true).apply { setPadding(0,dp(3),0,0) })
            addView(label("Alarm doğrulandı. Ölçümü kontrol et. Belirgin kötüleşmede acil yardım al.",12f,text).apply { setPadding(0,dp(8),0,0) })
            addView(label("Telefona da iletilmeye çalışılıyor.",11f,muted).apply { setPadding(0,dp(7),0,0) })
        }, sectionParams(6))

        root.addView(action("🔕","Alarmı sustur",red) {
            LocalAlarm.cancel(this)
            getSystemService(NotificationManager::class.java).cancel(LocalAlarm.NOTIFICATION_ID)
            finish()
        }, sectionParams(8))

        root.addView(action("↻","Tekrar ölç",blue) {
            runCatching { ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_MEASURE_NOW)) }
        }, sectionParams(6))

        root.addView(action("⌂","Ana ekrana dön",muted) {
            LocalAlarm.cancel(this)
            startActivity(Intent(this, WatchSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }, sectionParams(6))

        setContentView(ScrollView(this).apply { isFillViewport=true; isVerticalScrollBarEnabled=false; setBackgroundColor(bg); addView(root) })
    }

    private fun metricCard(title:String,value:String,color:Int,unit:String="") = card(surface2).apply {
        gravity=Gravity.CENTER
        addView(label(title,11f,muted).apply{gravity=Gravity.CENTER})
        addView(label(value,if(resources.configuration.screenWidthDp<220)27f else 31f,color,true).apply{gravity=Gravity.CENTER;setPadding(0,dp(3),0,0)})
        if(unit.isNotBlank()) addView(label(unit,10f,muted).apply{gravity=Gravity.CENTER})
    }
    private fun action(icon:String,title:String,accent:Int,block:()->Unit)=card(surface2).apply {
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(52);isClickable=true;isFocusable=true;setOnClickListener{block()}
        addView(label(icon,19f,accent,true),LinearLayout.LayoutParams(dp(34),ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(label(title,14f,text,true),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        addView(label("›",20f,muted))
    }
    private fun card(color:Int=surface)=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(11),dp(9),dp(11),dp(9));background=rounded(color,18f) }
    private fun label(value:String,size:Float,color:Int,bold:Boolean=false)=TextView(this).apply{text=value;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD)}
    private fun sectionParams(top:Int)=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=dp(top)}
    private fun rounded(color:Int,radiusDp:Float)=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;setColor(color);cornerRadius=dp(radiusDp.toInt()).toFloat()}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_REASON="reason"
        const val EXTRA_SPO2="spo2"
        const val EXTRA_HR="hr"
    }
}
