package com.skhealth.guardian.mobile

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

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
    fun page(context: Context)=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(context,20),dp(context,18),dp(context,20),dp(context,40)); setBackgroundColor(BG) }
    fun title(context: Context, value:String)=text(context,value,27f,TEXT,true).apply{setPadding(0,0,0,dp(context,6))}
    fun subtitle(context: Context,value:String)=text(context,value,15f,MUTED).apply{setPadding(0,0,0,dp(context,16))}
    fun sectionTitle(context:Context,value:String)=text(context,value,20f,TEXT,true).apply{setPadding(0,dp(context,22),0,dp(context,10))}
    fun field(context:Context,label:String,value:String="",numeric:Boolean=false)=EditText(context).apply{
        hint=label; setText(value); setTextColor(TEXT); setHintTextColor(MUTED); textSize=16f; setPadding(dp(context,16),dp(context,13),dp(context,16),dp(context,13)); background=rounded(SURFACE_2,14,0xFF303A46.toInt(),1,context)
        if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER
        layoutParams=sectionParams(context,8)
    }
    fun check(context:Context,label:String,checked:Boolean=false)=CheckBox(context).apply{ text=label; isChecked=checked; setTextColor(TEXT); textSize=16f; buttonTintList=android.content.res.ColorStateList.valueOf(BLUE); setPadding(dp(context,4),dp(context,8),0,dp(context,8)) }
    fun button(context:Context,label:String,primary:Boolean=true)=Button(context).apply{
        text=label; isAllCaps=false; textSize=16f; setTypeface(typeface,Typeface.BOLD); setTextColor(TEXT); background=rounded(if(primary) 0xFF1677FF.toInt() else SURFACE_2,18,if(primary) null else 0xFF405064.toInt(),1,context); minHeight=dp(context,54); layoutParams=sectionParams(context,10)
    }
    fun applyBars(activity:android.app.Activity){ activity.window.statusBarColor=BG; activity.window.navigationBarColor=BG }
}
