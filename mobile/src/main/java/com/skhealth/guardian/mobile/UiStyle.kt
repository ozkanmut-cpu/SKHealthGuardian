package com.skhealth.guardian.mobile

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

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

    fun rounded(color: Int, radiusDp: Int = 18, strokeColor: Int? = null, strokeDp: Int = 1, context: Context): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(context, strokeDp), strokeColor)
        }

    fun text(context: Context, value: String, size: Float, color: Int = TEXT, bold: Boolean = false, gravity: Int = Gravity.START): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            this.gravity = gravity
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }

    fun card(context: Context, padding: Int = 18): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        background = rounded(SURFACE, context = context)
    }

    fun sectionParams(context: Context, top: Int = 12): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, top)
        }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(0xFF2A333D.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)).apply {
            topMargin = dp(context, 12)
            bottomMargin = dp(context, 12)
        }
    }
}
