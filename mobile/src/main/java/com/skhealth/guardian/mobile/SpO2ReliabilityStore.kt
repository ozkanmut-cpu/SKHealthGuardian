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
        val diff: Int,
        val watchHr: Int? = null,
        val pc60Hr: Int? = null
    ) {
        val hrDiff: Int? get() = if (watchHr != null && pc60Hr != null) watchHr - pc60Hr else null
    }

    fun onWatchReading(context: Context, id: String, timestampMs: Long, spo2: Int?, valid: Boolean, heartRate: Int? = null) {
        if (!valid || spo2 !in 1..100) return
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        p.edit()
            .putString("watch_id", id)
            .putLong("watch_ts", timestampMs)
            .putInt("watch_spo2", spo2!!)
            .putInt("watch_hr", heartRate?.takeIf { it in 1..511 } ?: -1)
            .apply()
        tryMatch(context)
    }

    fun onPc60Reading(context: Context, timestampMs: Long, spo2: Int?, valid: Boolean, heartRate: Int? = null) {
        if (!valid || spo2 !in 1..100) return
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        p.edit()
            .putLong("pc_ts", timestampMs)
            .putInt("pc_spo2", spo2!!)
            .putInt("pc_hr", heartRate?.takeIf { it in 1..511 } ?: -1)
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
        val watchHr = p.getInt("watch_hr", -1).takeIf { it in 1..511 }
        val pcHr = p.getInt("pc_hr", -1).takeIf { it in 1..511 }
        val hrSigned = if (watchHr != null && pcHr != null) watchHr - pcHr else null
        val count = p.getInt("count", 0) + 1
        val sumAbs = p.getInt("sum_abs", 0) + absDiff
        val sumSigned = p.getInt("sum_signed", 0) + signed
        val hrCount = p.getInt("hr_count", 0) + if (hrSigned != null) 1 else 0
        val hrSumAbs = p.getInt("hr_sum_abs", 0) + (hrSigned?.let(::abs) ?: 0)
        val hrSumSigned = p.getInt("hr_sum_signed", 0) + (hrSigned ?: 0)
        val ts = maxOf(watchTs, pcTs)
        val recent = recentMatches(context).toMutableList().apply {
            add(Match(ts, watch, pc, signed, watchHr, pcHr))
            while (size > RECENT_LIMIT) removeAt(0)
        }
        p.edit()
            .putString("matched_watch_id", watchId)
            .putInt("count", count)
            .putInt("sum_abs", sumAbs)
            .putInt("sum_signed", sumSigned)
            .putInt("hr_count", hrCount)
            .putInt("hr_sum_abs", hrSumAbs)
            .putInt("hr_sum_signed", hrSumSigned)
            .putLong("last_match_ts", ts)
            .putInt("last_watch", watch)
            .putInt("last_pc", pc)
            .putInt("last_diff", signed)
            .putInt("last_watch_hr", watchHr ?: -1)
            .putInt("last_pc_hr", pcHr ?: -1)
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
        val label: String,
        val hrCount: Int,
        val hrMeanAbsoluteError: Double?,
        val hrMeanBias: Double?,
        val lastWatchHr: Int?,
        val lastPc60Hr: Int?
    )

    fun summary(context: Context): Summary {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val count = p.getInt("count", 0)
        val hrCount = p.getInt("hr_count", 0)
        val mae = if (count > 0) p.getInt("sum_abs", 0).toDouble() / count else null
        val bias = if (count > 0) p.getInt("sum_signed", 0).toDouble() / count else null
        val hrMae = if (hrCount > 0) p.getInt("hr_sum_abs", 0).toDouble() / hrCount else null
        val hrBias = if (hrCount > 0) p.getInt("hr_sum_signed", 0).toDouble() / hrCount else null
        return Summary(
            count = count,
            meanAbsoluteError = mae,
            meanBias = bias,
            lastWatch = p.getInt("last_watch", -1).takeIf { it >= 0 },
            lastPc60 = p.getInt("last_pc", -1).takeIf { it >= 0 },
            lastDiff = p.getInt("last_diff", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            lastMatchAt = p.getLong("last_match_ts", 0L).takeIf { it > 0L },
            label = SpO2ReliabilityClassifier.label(count, mae, bias),
            hrCount = hrCount,
            hrMeanAbsoluteError = hrMae,
            hrMeanBias = hrBias,
            lastWatchHr = p.getInt("last_watch_hr", -1).takeIf { it > 0 },
            lastPc60Hr = p.getInt("last_pc_hr", -1).takeIf { it > 0 }
        )
    }

    fun recentMatches(context: Context): List<Match> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("recent_matches", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split(',')
            if (p.size < 4) return@mapNotNull null
            val ts = p[0].toLongOrNull() ?: return@mapNotNull null
            val watch = p[1].toIntOrNull() ?: return@mapNotNull null
            val pc = p[2].toIntOrNull() ?: return@mapNotNull null
            val diff = p[3].toIntOrNull() ?: return@mapNotNull null
            Match(ts, watch, pc, diff, p.getOrNull(4)?.toIntOrNull()?.takeIf { it > 0 }, p.getOrNull(5)?.toIntOrNull()?.takeIf { it > 0 })
        }.toList()
    }

    fun restore(context: Context, matches: List<Match>) {
        val valid = matches.filter { it.timestampMs > 0L && it.watch in 1..100 && it.pc60 in 1..100 }
            .map { it.copy(diff = it.watch - it.pc60) }
            .takeLast(RECENT_LIMIT)
        val count = valid.size
        val sumAbs = valid.sumOf { abs(it.diff) }
        val sumSigned = valid.sumOf { it.diff }
        val hrValid = valid.mapNotNull { it.hrDiff }
        val last = valid.lastOrNull()
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .remove("watch_id").remove("matched_watch_id").remove("watch_ts").remove("watch_spo2").remove("watch_hr")
            .remove("pc_ts").remove("pc_spo2").remove("pc_hr")
            .putInt("count", count)
            .putInt("sum_abs", sumAbs)
            .putInt("sum_signed", sumSigned)
            .putInt("hr_count", hrValid.size)
            .putInt("hr_sum_abs", hrValid.sumOf { abs(it) })
            .putInt("hr_sum_signed", hrValid.sum())
            .putLong("last_match_ts", last?.timestampMs ?: 0L)
            .putInt("last_watch", last?.watch ?: -1)
            .putInt("last_pc", last?.pc60 ?: -1)
            .putInt("last_diff", last?.diff ?: Int.MIN_VALUE)
            .putInt("last_watch_hr", last?.watchHr ?: -1)
            .putInt("last_pc_hr", last?.pc60Hr ?: -1)
            .putString("recent_matches", encode(valid))
            .apply()
    }

    private fun encode(items: List<Match>): String =
        items.joinToString("\n") { "${it.timestampMs},${it.watch},${it.pc60},${it.diff},${it.watchHr ?: -1},${it.pc60Hr ?: -1}" }

    fun formatted(context: Context): String {
        val s = summary(context)
        if (s.count == 0) return "Henüz eşzamanlı karşılaştırma yok"
        val mae = ((s.meanAbsoluteError ?: 0.0) * 10).roundToInt() / 10.0
        val bias = ((s.meanBias ?: 0.0) * 10).roundToInt() / 10.0
        val hr = if (s.hrCount > 0) {
            val hrMae = ((s.hrMeanAbsoluteError ?: 0.0) * 10).roundToInt() / 10.0
            " | Nabız farkı: $hrMae bpm"
        } else ""
        return "${s.label} | Eşleşme: ${s.count} | Ort. mutlak fark: $mae puan | Ort. sapma (Saat-PC60): $bias puan$hr | Son: Saat %${s.lastWatch} / PC-60FW %${s.lastPc60}"
    }
}
