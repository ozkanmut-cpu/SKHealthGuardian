package com.skhealth.guardian.mobile

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmTimelineStore {
    private const val PREF = "alarm_timeline"
    private const val KEY = "events"
    private const val MAX = 500

    fun add(context: Context, category: String, detail: String, timestampMs: Long = System.currentTimeMillis()) {
        val line = listOf(timestampMs.toString(), clean(category), clean(detail)).joinToString("|")
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val items = prefs.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
        items += line
        prefs.edit().putString(KEY, items.takeLast(MAX).joinToString("\n")).apply()
    }

    fun formatted(context: Context, limit: Int = 200): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        return prefs.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(limit).asReversed().joinToString("\n\n") { line ->
            val p = line.split('|', limit = 3)
            if (p.size < 3) line else {
                val ts = p[0].toLongOrNull() ?: 0L
                "${fmt.format(Date(ts))} • ${p[1]}\n${p[2]}"
            }
        }.ifBlank { "Henüz alarm olayı yok." }
    }

    private fun clean(value: String): String = value.replace('|', '/').replace('\n', ' ')
}
