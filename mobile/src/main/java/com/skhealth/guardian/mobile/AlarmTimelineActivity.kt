package com.skhealth.guardian.mobile

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AlarmTimelineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        root.addView(TextView(this).apply { text = "Alarm Geçmişi"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "Alarm, bildirim, yeniden ölçüm ve susturma olaylarının zaman çizelgesi."; textSize = 15f; setPadding(0, 10, 0, 20) })
        val events = AlarmTimelineStore.events(this, 200).asReversed()
        if (events.isEmpty()) {
            root.addView(TextView(this).apply { text = "Henüz alarm olayı yok."; textSize = 17f; setPadding(0, 20, 0, 0) })
        } else {
            events.forEach { e ->
                root.addView(TextView(this).apply {
                    text = "${e.category}\n${java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale("tr", "TR")).format(java.util.Date(e.timestampMs))} • ${e.detail}"
                    textSize = 16f
                    setPadding(0, 12, 0, 12)
                })
            }
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
