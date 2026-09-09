package com.skhealth.guardian.mobile

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeliveryLogStore {
    private const val PREF = "delivery_log"
    private const val KEY = "rows"
    private const val MAX = 300

    data class Entry(
        val timestampMs: Long,
        val channel: String,
        val target: String,
        val ok: Boolean,
        val detail: String
    )

    fun add(context: Context, channel: String, target: String, ok: Boolean, detail: String) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val rows = prefs.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.toMutableList()
        rows += encode(Entry(System.currentTimeMillis(), channel, target, ok, detail))
        prefs.edit().putString(KEY, rows.takeLast(MAX).joinToString("\n")).apply()
    }

    fun entries(context: Context, limit: Int = MAX): List<Entry> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.mapNotNull(::decode).toList().takeLast(limit)

    fun replace(context: Context, entries: List<Entry>) {
        val raw = entries.takeLast(MAX).joinToString("\n", transform = ::encode)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }

    fun formatted(context: Context): String {
        val df = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        return entries(context, 100).asReversed().joinToString("\n") { e ->
            "${df.format(Date(e.timestampMs))} ${if (e.ok) "✓" else "✗"} ${e.channel} ${e.target} — ${e.detail}"
        }
    }

    private fun encode(e: Entry): String = listOf(
        e.timestampMs,
        clean(e.channel),
        clean(e.target),
        e.ok,
        clean(e.detail)
    ).joinToString("|")

    private fun decode(row: String): Entry? {
        val p = row.split('|', limit = 5)
        if (p.size < 5) return null
        return Entry(
            timestampMs = p[0].toLongOrNull() ?: return null,
            channel = p[1],
            target = p[2],
            ok = p[3].toBooleanStrictOrNull() ?: false,
            detail = p[4]
        )
    }

    private fun clean(value: String): String = value.replace('|', '/').replace('\n', ' ')
}
