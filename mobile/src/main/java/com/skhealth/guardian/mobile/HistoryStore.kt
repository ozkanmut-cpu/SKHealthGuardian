package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.HealthReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HistoryStore {
    private const val PREF = "reading_history"
    private const val KEY = "rows"
    private const val MAX = 1000
    private val lock = Any()

    fun add(context: Context, r: HealthReading) {
        synchronized(lock) {
            val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val rows = (p.getString(KEY, "") ?: "").lineSequence().filter { it.isNotBlank() }.toMutableList()
            rows += encode(r)
            while (rows.size > MAX) rows.removeAt(0)
            p.edit().putString(KEY, rows.joinToString("\n")).apply()
        }
    }

    /**
     * Atomically checks the replay id and stores the reading when it is new.
     * Blank ids are treated as non-deduplicable legacy readings and are stored.
     */
    fun addIfAbsent(context: Context, r: HealthReading): Boolean = synchronized(lock) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = p.getString(KEY, "") ?: ""
        if (r.id.isNotBlank() && raw.lineSequence().any { line -> line.substringBefore('|', "") == r.id }) {
            return@synchronized false
        }
        val rows = raw.lineSequence().filter { it.isNotBlank() }.toMutableList()
        rows += encode(r)
        while (rows.size > MAX) rows.removeAt(0)
        p.edit().putString(KEY, rows.joinToString("\n")).apply()
        true
    }

    fun replace(context: Context, readings: List<HealthReading>) {
        synchronized(lock) {
            val rows = readings.takeLast(MAX).joinToString("\n", transform = ::encode)
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, rows).apply()
        }
    }

    fun contains(context: Context, id: String): Boolean {
        if (id.isBlank()) return false
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.lineSequence().any { line -> line.substringBefore('|', "") == id }
    }

    fun recent(context: Context, limit: Int = 100): List<HealthReading> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split('|')
            when {
                p.size >= 6 -> HealthReading(
                    id = p[0],
                    timestampMs = p[1].toLongOrNull() ?: return@mapNotNull null,
                    spo2 = p[2].toIntOrNull(),
                    heartRate = p[3].toIntOrNull(),
                    valid = p[4].toBooleanStrictOrNull() ?: false,
                    source = p[5]
                )
                p.size >= 5 -> HealthReading(
                    timestampMs = p[0].toLongOrNull() ?: return@mapNotNull null,
                    spo2 = p[1].toIntOrNull(),
                    heartRate = p[2].toIntOrNull(),
                    valid = p[3].toBooleanStrictOrNull() ?: false,
                    source = p[4]
                )
                else -> null
            }
        }.toList().takeLast(limit)
    }

    fun formatted(context: Context, limit: Int = 100): String {
        val df = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        return recent(context, limit).asReversed().joinToString("\n") { r ->
            val value = when {
                r.spo2 != null -> "SpO₂ %${r.spo2}"
                r.heartRate != null -> "Nabız ${r.heartRate} bpm"
                else -> "Ölçüm başarısız"
            }
            "${df.format(Date(r.timestampMs))}  $value"
        }
    }

    private fun encode(r: HealthReading): String =
        listOf(r.id, r.timestampMs, r.spo2 ?: "", r.heartRate ?: "", r.valid, r.source).joinToString("|")
}
