package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.SpO2ReliabilityClassifier
import kotlin.math.abs
import kotlin.math.roundToInt

object SpO2ReliabilityStore {
    private const val PREF = "spo2_reliability"
    private const val MATCH_WINDOW_MS = 30_000L
    private const val RECENT_LIMIT = 60

    data class Match(
        val timestampMs: Long,
        val watch: Int,
        val pc60: Int,
        val diff: Int
    )

    fun onWatchReading(context: Context, id: String, timestampMs: Long, spo2: Int?, valid: Boolean) {
        if (!valid || spo2 !in 1..100) return
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        p.edit()
            .putString("watch_id", id)
            .putLong("watch_ts", timestampMs)
            .putInt("watch_spo2", spo2!!)
            .apply()
        tryMatch(context)
    }

    fun onPc60Reading(context: Context, timestampMs: Long, spo2: Int?, valid: Boolean) {
        if (!valid || spo2 !in 1..100) return
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        p.edit()
            .putLong("pc_ts", timestampMs)
            .putInt("pc_spo2", spo2!!)
            .apply()
        tryMatch(context)
    }

    private fun tryMatch(context: Context) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val watchId = p.getString("watch_id", "") ?: ""
        val matchedId = p.getString("matched_watch_id", "") ?: ""
        if (watchId.isBlank() || watchId == matchedId) return
        val watchTs = p.getLong("watch_ts", 0L)
        val pcTs = p.getLong("pc_ts", 0L)
        val watch = p.getInt("watch_spo2", -1)
        val pc = p.getInt("pc_spo2", -1)
        if (watch !in 1..100 || pc !in 1..100 || abs(watchTs - pcTs) > MATCH_WINDOW_MS) return

        val signed = watch - pc
        val absDiff = abs(signed)
        val count = p.getInt("count", 0) + 1
        val sumAbs = p.getInt("sum_abs", 0) + absDiff
        val sumSigned = p.getInt("sum_signed", 0) + signed
        val ts = maxOf(watchTs, pcTs)
        val recent = recentMatches(context).toMutableList().apply {
            add(Match(ts, watch, pc, signed))
            while (size > RECENT_LIMIT) removeAt(0)
        }
        p.edit()
            .putString("matched_watch_id", watchId)
            .putInt("count", count)
            .putInt("sum_abs", sumAbs)
            .putInt("sum_signed", sumSigned)
            .putLong("last_match_ts", ts)
            .putInt("last_watch", watch)
            .putInt("last_pc", pc)
            .putInt("last_diff", signed)
            .putString("recent_matches", encode(recent))
            .apply()
    }

    data class Summary(
        val count: Int,
        val meanAbsoluteError: Double?,
        val meanBias: Double?,
        val lastWatch: Int?,
        val lastPc60: Int?,
        val lastDiff: Int?,
        val lastMatchAt: Long?,
        val label: String
    )

    fun summary(context: Context): Summary {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val count = p.getInt("count", 0)
        val mae = if (count > 0) p.getInt("sum_abs", 0).toDouble() / count else null
        val bias = if (count > 0) p.getInt("sum_signed", 0).toDouble() / count else null
        return Summary(
            count = count,
            meanAbsoluteError = mae,
            meanBias = bias,
            lastWatch = p.getInt("last_watch", -1).takeIf { it >= 0 },
            lastPc60 = p.getInt("last_pc", -1).takeIf { it >= 0 },
            lastDiff = p.getInt("last_diff", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            lastMatchAt = p.getLong("last_match_ts", 0L).takeIf { it > 0L },
            label = SpO2ReliabilityClassifier.label(count, mae, bias)
        )
    }

    fun recentMatches(context: Context): List<Match> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("recent_matches", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split(',')
            if (p.size != 4) return@mapNotNull null
            val ts = p[0].toLongOrNull() ?: return@mapNotNull null
            val watch = p[1].toIntOrNull() ?: return@mapNotNull null
            val pc = p[2].toIntOrNull() ?: return@mapNotNull null
            val diff = p[3].toIntOrNull() ?: return@mapNotNull null
            Match(ts, watch, pc, diff)
        }.toList()
    }

    private fun encode(items: List<Match>): String =
        items.joinToString("\n") { "${it.timestampMs},${it.watch},${it.pc60},${it.diff}" }

    fun formatted(context: Context): String {
        val s = summary(context)
        if (s.count == 0) return "Henüz eşzamanlı karşılaştırma yok"
        val mae = ((s.meanAbsoluteError ?: 0.0) * 10).roundToInt() / 10.0
        val bias = ((s.meanBias ?: 0.0) * 10).roundToInt() / 10.0
        return "${s.label} | Eşleşme: ${s.count} | Ort. mutlak fark: $mae puan | Ort. sapma (Saat-PC60): $bias puan | Son: Saat %${s.lastWatch} / PC-60FW %${s.lastPc60}"
    }
}
