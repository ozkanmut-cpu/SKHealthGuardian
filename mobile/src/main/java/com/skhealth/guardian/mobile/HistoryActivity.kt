package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        setContentView(ScrollView(this).apply { addView(root) })

        val rows = HistoryStore.recent(this, 300).asReversed()
        root.addView(TextView(this).apply { text = "Ölçüm geçmişi"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply {
            text = if (rows.isEmpty()) "Henüz ölçüm kaydı yok" else "Son ${rows.size} kayıt • en yeni ölçüm üstte"
            textSize = 15f; setPadding(0, 10, 0, 18)
        })
        root.addView(Button(this).apply { text = "Grafikleri aç"; setOnClickListener { startActivity(Intent(this@HistoryActivity, HistoryChartActivity::class.java)) } })

        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        rows.forEach { r ->
            val value = buildString {
                append(r.spo2?.let { "SpO₂ $it%" } ?: "SpO₂ —")
                append("   •   ")
                append(r.heartRate?.let { "Nabız $it bpm" } ?: "Nabız —")
            }
            root.addView(TextView(this).apply {
                text = "$value\n${fmt.format(Date(r.timestampMs))} • ${sourceLabel(r.source)}${if (!r.valid) " • geçersiz ölçüm" else ""}"
                textSize = 17f
                setPadding(0, 16, 0, 16)
            })
        }
    }

    private fun sourceLabel(source: String): String = when {
        source.contains("pc60", ignoreCase = true) -> "PC-60FW"
        source.contains("watch", ignoreCase = true) -> "Galaxy Watch"
        else -> source.ifBlank { "Bilinmeyen kaynak" }
    }
}
