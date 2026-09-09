package com.skhealth.guardian.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class AlarmTimelineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 17f
            text = AlarmTimelineStore.formatted(this@AlarmTimelineActivity)
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }
}
