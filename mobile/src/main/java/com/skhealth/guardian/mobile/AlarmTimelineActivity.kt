package com.skhealth.guardian.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmTimelineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); UiStyle.applyBars(this)
        val root = UiStyle.page(this)
        root.addView(UiStyle.title(this, "Olaylar"))
        root.addView(UiStyle.subtitle(this, "Alarm, bildirim, yeniden ölçüm ve susturma olaylarının zaman çizelgesi."))
        val events = AlarmTimelineStore.events(this, 200).asReversed()
        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        if (events.isEmpty()) {
            root.addView(UiStyle.card(this).apply { addView(UiStyle.text(this@AlarmTimelineActivity, "Henüz alarm olayı yok.", 16f, UiStyle.MUTED)) })
        } else events.forEach { e ->
            val severity = when {
                e.category.contains("ALARM", true) -> UiStyle.RED
                e.category.contains("SMS", true) || e.category.contains("ARAMA", true) -> UiStyle.GREEN
                e.category.contains("QA", true) -> UiStyle.BLUE
                else -> UiStyle.AMBER
            }
            val card = UiStyle.card(this)
            card.addView(UiStyle.text(this, e.category, 17f, severity, true))
            card.addView(UiStyle.text(this, fmt.format(Date(e.timestampMs)), 13f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@AlarmTimelineActivity, 5), 0, 0) })
            card.addView(UiStyle.text(this, e.detail, 15f, UiStyle.TEXT).apply { setPadding(0, UiStyle.dp(this@AlarmTimelineActivity, 8), 0, 0) })
            root.addView(card, UiStyle.sectionParams(this, 10))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
    }
}
