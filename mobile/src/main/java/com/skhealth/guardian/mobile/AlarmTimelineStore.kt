package com.skhealth.guardian.mobile

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmTimelineStore {
    private const val PREF = "alarm_timeline"
    private const val KEY = "events"
    private const val MAX = 500
    private val lock = Any()

    data class Event(val timestampMs: Long, val category: String, val detail: String)

    fun add(context: Context, category: String, detail: String, timestampMs: Long = System.currentTimeMillis()) {
        val line = encode(Event(timestampMs, category, detail))
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val items = prefs.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
            items += line
            prefs.edit().putString(KEY, items.takeLast(MAX).joinToString("\n")).apply()
        }
    }

    fun events(context: Context, limit: Int = MAX): List<Event> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.mapNotNull(::decode).toList().takeLast(limit)

    fun replace(context: Context, events: List<Event>) {
        val raw = events.takeLast(MAX).joinToString("\n", transform = ::encode)
        synchronized(lock) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        }
    }

    fun formatted(context: Context, limit: Int = 200): String {
        val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        return events(context, limit).asReversed().joinToString("\n\n") { e ->
            "${fmt.format(Date(e.timestampMs))} • ${e.category}\n${e.detail}"
        }.ifBlank { "Henüz alarm olayı yok."
        }
    }

    private fun encode(e: Event): String = listOf(e.timestampMs.toString(), clean(e.category), clean(e.detail)).joinToString("|")
    private fun decode(line: String): Event? {
        val p = line.split('|', limit = 3)
        if (p.size < 3) return null
        return Event(p[0].toLongOrNull() ?: return null, p[1], p[2])
    }
    private fun clean(value: String): String = value.replace('|', '/').replace('\n', ' ')
}
