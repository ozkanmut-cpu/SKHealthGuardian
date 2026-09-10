package com.skhealth.guardian.mobile

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmTimelineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); UiStyle.applyBars(this)
        val shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(UiStyle.BG)}
        val root = UiStyle.page(this)
        val scroll=ScrollView(this).apply{setBackgroundColor(UiStyle.BG);addView(root)}
        shell.addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        shell.addView(UiStyle.appBottomNavigation(this,"events"))
        setContentView(shell)

        root.addView(UiStyle.iconLabel(this, SkIcon.EVENTS, "Olaylar", UiStyle.BLUE, UiStyle.TEXT, 30, 27f, true, 12))
        root.addView(UiStyle.subtitle(this, "Alarm, bildirim, yeniden ölçüm ve susturma olaylarının zaman çizelgesi."))

        val events = AlarmTimelineStore.events(this, 200).asReversed()
        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        if (events.isEmpty()) {
            root.addView(UiStyle.card(this).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(UiStyle.icon(this@AlarmTimelineActivity, SkIcon.EVENTS, 34, UiStyle.MUTED, "Olay yok"))
                addView(UiStyle.text(this@AlarmTimelineActivity, "Henüz alarm olayı yok.", 16f, UiStyle.MUTED, false, Gravity.CENTER).apply {
                    setPadding(0, UiStyle.dp(this@AlarmTimelineActivity, 10), 0, 0)
                })
            })
        } else events.forEach { e ->
            val upper=e.category.uppercase(Locale("tr","TR"))
            val severity = when {
                upper.contains("ALARM") -> UiStyle.RED
                upper.contains("SMS") || upper.contains("ARAMA") -> UiStyle.GREEN
                upper.contains("QA") -> UiStyle.BLUE
                else -> UiStyle.AMBER
            }
            val icon = when {
                upper.contains("ALARM") -> SkIcon.STATUS_ALERT
                upper.contains("SMS") || upper.contains("ARAMA") -> SkIcon.CONTACTS
                upper.contains("SUSTUR") -> SkIcon.BELL
                upper.contains("ÖLÇ") || upper.contains("OLC") -> SkIcon.REFRESH
                upper.contains("QA") -> SkIcon.INFO
                else -> SkIcon.EVENTS
            }
            val card = UiStyle.card(this)
            card.addView(UiStyle.iconLabel(this, icon, e.category, severity, severity, 24, 17f, true, 10))
            card.addView(UiStyle.iconLabel(this, SkIcon.CLOCK, fmt.format(Date(e.timestampMs)), UiStyle.MUTED, UiStyle.MUTED, 16, 13f, false, 7).apply {
                setPadding(0, UiStyle.dp(this@AlarmTimelineActivity, 7), 0, 0)
            })
            card.addView(UiStyle.text(this, e.detail, 15f, UiStyle.TEXT).apply {
                setPadding(0, UiStyle.dp(this@AlarmTimelineActivity, 10), 0, 0)
            })
            root.addView(card, UiStyle.sectionParams(this, 10))
        }
    }
}
