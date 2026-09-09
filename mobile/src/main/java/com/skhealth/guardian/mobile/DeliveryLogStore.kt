package com.skhealth.guardian.mobile

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeliveryLogStore {
    private const val PREF = "delivery_log"
    private const val KEY = "rows"
    private const val MAX = 300

    fun add(context: Context, channel: String, target: String, ok: Boolean, detail: String) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val rows = prefs.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
        rows += listOf(System.currentTimeMillis(), channel, target, ok, detail.replace('|', '/')).joinToString("|")
        prefs.edit().putString(KEY, rows.takeLast(MAX).joinToString("\n")).apply()
    }

    fun formatted(context: Context): String {
        val df = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            .lines().filter { it.isNotBlank() }.takeLast(100).asReversed().joinToString("\n") { row ->
                val p = row.split('|')
                if (p.size < 5) row else "${df.format(Date(p[0].toLongOrNull() ?: 0L))} ${if (p[3].toBoolean()) "✓" else "✗"} ${p[1]} ${p[2]} — ${p[4]}"
            }
    }
}
