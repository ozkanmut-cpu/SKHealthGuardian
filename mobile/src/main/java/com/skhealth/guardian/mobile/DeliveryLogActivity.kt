package com.skhealth.guardian.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeliveryLogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); UiStyle.applyBars(this)
        val root = UiStyle.page(this)
        val entries = DeliveryLogStore.entries(this, 200).asReversed()
        val ok = entries.count { it.ok }
        root.addView(UiStyle.title(this, "SMS / Arama kayıtları"))
        root.addView(UiStyle.subtitle(this, if (entries.isEmpty()) "Henüz iletişim kaydı yok." else "$ok/${entries.size} işlem başarılı"))
        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        if (entries.isEmpty()) root.addView(UiStyle.card(this).apply { addView(UiStyle.text(this@DeliveryLogActivity, "İletişim olayları burada görünecek.", 16f, UiStyle.MUTED)) })
        entries.forEach { e ->
            val card = UiStyle.card(this)
            val accent = if (e.ok) UiStyle.GREEN else UiStyle.RED
            card.addView(UiStyle.text(this, "${if (e.ok) "✓" else "✗"} ${e.channel}", 17f, accent, true))
            card.addView(UiStyle.text(this, e.target, 14f, UiStyle.TEXT).apply { setPadding(0, UiStyle.dp(this@DeliveryLogActivity, 6), 0, 0) })
            card.addView(UiStyle.text(this, fmt.format(Date(e.timestampMs)), 13f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@DeliveryLogActivity, 5), 0, 0) })
            card.addView(UiStyle.text(this, e.detail, 15f, UiStyle.TEXT).apply { setPadding(0, UiStyle.dp(this@DeliveryLogActivity, 8), 0, 0) })
            root.addView(card, UiStyle.sectionParams(this, 10))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
    }
}
