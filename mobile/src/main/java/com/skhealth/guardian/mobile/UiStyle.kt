package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object UiStyle {
    const val BG = 0xFF0D1117.toInt()
    const val SURFACE = 0xFF171D24.toInt()
    const val SURFACE_2 = 0xFF202832.toInt()
    const val TEXT = 0xFFF3F6F9.toInt()
    const val MUTED = 0xFF97A2AE.toInt()
    const val GREEN = 0xFF35D07F.toInt()
    const val RED = 0xFFFF5C5C.toInt()
    const val AMBER = 0xFFFFB74D.toInt()
    const val BLUE = 0xFF42A5F5.toInt()

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    fun rounded(color: Int, radiusDp: Int = 18, strokeColor: Int? = null, strokeDp: Int = 1, context: Context) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(color); cornerRadius = dp(context, radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(context, strokeDp), strokeColor)
    }
    fun text(context: Context, value: String, size: Float, color: Int = TEXT, bold: Boolean = false, gravity: Int = Gravity.START) = TextView(context).apply {
        text=value; textSize=size; setTextColor(color); this.gravity=gravity; includeFontPadding=false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    fun card(context: Context, padding: Int = 18) = LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(context,padding),dp(context,padding),dp(context,padding),dp(context,padding)); background=rounded(SURFACE,context=context)
    }
    fun sectionParams(context: Context, top: Int = 12) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=dp(context,top) }
    fun divider(context: Context)=View(context).apply { setBackgroundColor(0xFF2A333D.toInt()); layoutParams=LinearLayout.LayoutParams(-1,dp(context,1)).apply{topMargin=dp(context,12);bottomMargin=dp(context,12)} }
    fun page(context: Context)=LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL
        setPadding(dp(context,20),dp(context,18),dp(context,20),dp(context,32))
        setBackgroundColor(BG)
        applyBottomInset(this, 8)
    }
    fun title(context: Context, value:String)=text(context,value,27f,TEXT,true).apply{setPadding(0,0,0,dp(context,6))}
    fun subtitle(context: Context,value:String)=text(context,value,15f,MUTED).apply{setPadding(0,0,0,dp(context,16))}
    fun sectionTitle(context:Context,value:String)=text(context,value,20f,TEXT,true).apply{setPadding(0,dp(context,22),0,dp(context,10))}

    fun detailHeader(activity: Activity, title: String, subtitle: String? = null) = LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL
        val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val back=text(activity,"‹",34f,TEXT,true,Gravity.CENTER).apply{minimumWidth=dp(activity,48);minimumHeight=dp(activity,48);gravity=Gravity.CENTER;isClickable=true;isFocusable=true;setOnClickListener{activity.finish()}}
        row.addView(back,LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)))
        row.addView(text(activity,title,25f,TEXT,true),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        addView(row)
        if(!subtitle.isNullOrBlank()) addView(text(activity,subtitle,14f,MUTED).apply{setPadding(dp(activity,48),0,0,dp(activity,12))})
    }

    fun appBottomNavigation(activity: Activity, active: String): LinearLayout = LinearLayout(activity).apply {
        orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER
        setPadding(dp(activity,8),dp(activity,8),dp(activity,8),dp(activity,10)); setBackgroundColor(0xFF111820.toInt())
        applyBottomInset(this,0)
        fun item(icon:String,label:String,key:String,target:Class<out Activity>)=LinearLayout(activity).apply{
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;minimumHeight=dp(activity,54);isClickable=true;isFocusable=true
            val selected=active==key;val color=if(selected) BLUE else MUTED
            addView(text(activity,icon,18f,color,true,Gravity.CENTER));addView(text(activity,label,11f,color,selected,Gravity.CENTER).apply{setPadding(0,dp(activity,3),0,0)})
            setOnClickListener{if(!selected){activity.startActivity(Intent(activity,target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));}}
            layoutParams=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
        }
        addView(item("⌂","Ana ekran","home",MainActivity::class.java));addView(item("⌁","Grafikler","charts",HistoryChartActivity::class.java));addView(item("☷","Olaylar","events",AlarmTimelineActivity::class.java));addView(item("⚙","Ayarlar","settings",SettingsHubActivity::class.java))
    }

    fun field(context:Context,label:String,value:String="",numeric:Boolean=false)=EditText(context).apply{
        hint=label; setText(value); setTextColor(TEXT); setHintTextColor(MUTED); textSize=16f
        setPadding(dp(context,16),dp(context,13),dp(context,16),dp(context,13)); background=rounded(SURFACE_2,14,0xFF303A46.toInt(),1,context)
        if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER
        layoutParams=sectionParams(context,8)
    }
    fun labeledField(context:Context,label:String,value:String="",numeric:Boolean=false)=LinearLayout(context).apply{
        orientation=LinearLayout.VERTICAL;layoutParams=sectionParams(context,10)
        addView(text(context,label,12.5f,MUTED,true).apply{setPadding(dp(context,2),0,0,dp(context,6))})
        addView(EditText(context).apply{tag="labeled-value";setText(value);setTextColor(TEXT);setHintTextColor(MUTED);textSize=16f;setPadding(dp(context,16),dp(context,13),dp(context,16),dp(context,13));background=rounded(SURFACE_2,14,0xFF303A46.toInt(),1,context);if(numeric)inputType=android.text.InputType.TYPE_CLASS_NUMBER},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    fun labeledFieldInput(container: LinearLayout): EditText = container.findViewWithTag("labeled-value")
    fun check(context:Context,label:String,checked:Boolean=false)=CheckBox(context).apply{ text=label; isChecked=checked; setTextColor(TEXT); textSize=16f; buttonTintList=android.content.res.ColorStateList.valueOf(BLUE); setPadding(dp(context,4),dp(context,8),0,dp(context,8)) }
    fun button(context:Context,label:String,primary:Boolean=true)=Button(context).apply{text=label;isAllCaps=false;textSize=16f;setTypeface(typeface,Typeface.BOLD);setTextColor(TEXT);background=rounded(if(primary)0xFF1677FF.toInt() else SURFACE_2,18,if(primary)null else 0xFF405064.toInt(),1,context);minHeight=dp(context,54);layoutParams=sectionParams(context,10)}
    fun applyBars(activity: Activity){activity.window.statusBarColor=BG;activity.window.navigationBarColor=BG;WindowCompat.setDecorFitsSystemWindows(activity.window,true);WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply{isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false}}
    fun applyBottomInset(view: View, extraDp: Int = 0) {val l=view.paddingLeft;val t=view.paddingTop;val r=view.paddingRight;val b=view.paddingBottom;ViewCompat.setOnApplyWindowInsetsListener(view){v,insets->val nav=insets.getInsets(WindowInsetsCompat.Type.navigationBars());v.setPadding(l,t,r,b+nav.bottom+dp(v.context,extraDp));insets};ViewCompat.requestApplyInsets(view)}
}
