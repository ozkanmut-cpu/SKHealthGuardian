package com.skhealth.guardian.mobile

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeliveryLogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        root.addView(TextView(this).apply { text = "SMS / Arama Kayıtları"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        val entries = DeliveryLogStore.entries(this, 200).asReversed()
        val ok = entries.count { it.ok }
        root.addView(TextView(this).apply { text = if (entries.isEmpty()) "Henüz iletişim kaydı yok." else "$ok/${entries.size} işlem başarılı"; textSize = 17f; setPadding(0, 12, 0, 20) })
        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        entries.forEach { e ->
            root.addView(TextView(this).apply {
                text = "${if (e.ok) "✓" else "✗"} ${e.channel} • ${e.target}\n${fmt.format(Date(e.timestampMs))} • ${e.detail}"
                textSize = 16f
                setPadding(0, 12, 0, 12)
            })
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
