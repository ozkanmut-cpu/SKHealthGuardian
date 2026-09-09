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

    fun add(context: Context, r: HealthReading) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val rows = (p.getString(KEY, "") ?: "").lineSequence().filter { it.isNotBlank() }.toMutableList()
        rows += listOf(r.timestampMs, r.spo2 ?: "", r.heartRate ?: "", r.valid, r.source).joinToString("|")
        while (rows.size > MAX) rows.removeAt(0)
        p.edit().putString(KEY, rows.joinToString("\n")).apply()
    }

    fun recent(context: Context, limit: Int = 100): List<HealthReading> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 5) null else HealthReading(
                timestampMs = p[0].toLongOrNull() ?: return@mapNotNull null,
                spo2 = p[1].toIntOrNull(), heartRate = p[2].toIntOrNull(),
                valid = p[3].toBooleanStrictOrNull() ?: false, source = p[4]
            )
        }.toList().takeLast(limit)
    }

    fun formatted(context: Context, limit: Int = 100): String {
        val df = SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR"))
        return recent(context, limit).asReversed().joinToString("\n") { r ->
            val value = when { r.spo2 != null -> "SpO₂ %${r.spo2}"; r.heartRate != null -> "Nabız ${r.heartRate} bpm"; else -> "Ölçüm başarısız" }
            "${df.format(Date(r.timestampMs))}  $value"
        }
    }
}
