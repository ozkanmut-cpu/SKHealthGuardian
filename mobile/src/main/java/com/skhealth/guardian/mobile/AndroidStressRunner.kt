package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.HealthReading
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

 data class AndroidStressResult(
    val id: String,
    val title: String,
    val passed: Boolean,
    val operations: Int,
    val elapsedMs: Long,
    val detail: String
)

data class AndroidStressReport(val results: List<AndroidStressResult>) {
    val passed: Boolean get() = results.all { it.passed }
    val totalOperations: Int get() = results.sumOf { it.operations }
    val totalElapsedMs: Long get() = results.sumOf { it.elapsedMs }
}

/**
 * Exercises the real Android persistence layer used by production.
 * Existing history/timeline are snapshotted and restored after each destructive test.
 * No SMS, call, BLE command or remote alarm is sent.
 */
object AndroidStressRunner {
    fun run(context: Context, heavy: Boolean = false): AndroidStressReport {
        val scale = if (heavy) 5 else 1
        return AndroidStressReport(
            listOf(
                historyWriteReadTrim(context, 2_500 * scale),
                timelineWriteReadTrim(context, 1_500 * scale),
                duplicateLookupStorm(context, 10_000 * scale),
                concurrentHistoryReaders(context, 8, 2_000 * scale),
                prefsReplayStorm(context, 20_000 * scale)
            )
        )
    }

    private fun historyWriteReadTrim(context: Context, n: Int): AndroidStressResult {
        val backup = HistoryStore.recent(context, 1000)
        return try {
            timed("history-rw", "HistoryStore yaz/oku/trim", n) {
                HistoryStore.replace(context, emptyList())
                repeat(n) { i ->
                    HistoryStore.add(
                        context,
                        HealthReading(
                            id = "stress-$i",
                            timestampMs = 1_000_000L + i,
                            spo2 = 90 + (i % 10),
                            heartRate = 60 + (i % 80),
                            valid = true,
                            source = "stress"
                        )
                    )
                }
                val rows = HistoryStore.recent(context, 2_000)
                val expectedSize = minOf(n, 1000)
                val tailOk = rows.lastOrNull()?.id == "stress-${n - 1}"
                Check(rows.size == expectedSize && tailOk, "rows=${rows.size}/$expectedSize tail=${rows.lastOrNull()?.id}")
            }
        } finally {
            HistoryStore.replace(context, backup)
        }
    }

    private fun timelineWriteReadTrim(context: Context, n: Int): AndroidStressResult {
        val backup = AlarmTimelineStore.events(context, 500)
        return try {
            timed("timeline-rw", "AlarmTimeline yaz/oku/trim", n) {
                AlarmTimelineStore.replace(context, emptyList())
                repeat(n) { i -> AlarmTimelineStore.add(context, "STRESS", "event-$i", 2_000_000L + i) }
                val rows = AlarmTimelineStore.events(context, 1000)
                val expectedSize = minOf(n, 500)
                val tailOk = rows.lastOrNull()?.detail == "event-${n - 1}"
                Check(rows.size == expectedSize && tailOk, "events=${rows.size}/$expectedSize tail=${rows.lastOrNull()?.detail}")
            }
        } finally {
            AlarmTimelineStore.replace(context, backup)
        }
    }

    private fun duplicateLookupStorm(context: Context, n: Int): AndroidStressResult {
        val backup = HistoryStore.recent(context, 1000)
        return try {
            timed("duplicate-lookup", "Duplicate/replay lookup fırtınası", n) {
                val seed = (0 until 1000).map { i ->
                    HealthReading(id = "dup-$i", timestampMs = 3_000_000L + i, spo2 = 97, heartRate = 78, source = "stress")
                }
                HistoryStore.replace(context, seed)
                var miss = 0
                repeat(n) { i -> if (!HistoryStore.contains(context, "dup-${i % 1000}")) miss++ }
                Check(miss == 0, "lookup miss=$miss")
            }
        } finally {
            HistoryStore.replace(context, backup)
        }
    }

    private fun concurrentHistoryReaders(context: Context, workers: Int, perWorker: Int): AndroidStressResult {
        val backup = HistoryStore.recent(context, 1000)
        val ops = workers * perWorker
        return try {
            val seed = (0 until 1000).map { i ->
                HealthReading(id = "parallel-$i", timestampMs = 4_000_000L + i, spo2 = 96, heartRate = 75, source = "stress")
            }
            HistoryStore.replace(context, seed)
            timed("history-parallel", "8 paralel HistoryStore okuyucu", ops) {
                val executor = Executors.newFixedThreadPool(workers)
                try {
                    val jobs = (0 until workers).map { worker ->
                        Callable {
                            var failures = 0
                            repeat(perWorker) { i ->
                                val id = "parallel-${(i + worker) % 1000}"
                                if (!HistoryStore.contains(context, id)) failures++
                                if (HistoryStore.recent(context, 10).size != 10) failures++
                            }
                            failures
                        }
                    }
                    val failures = executor.invokeAll(jobs).sumOf { it.get(30, TimeUnit.SECONDS) }
                    Check(failures == 0, "parallel failure=$failures")
                } finally {
                    executor.shutdownNow()
                }
            }
        } finally {
            HistoryStore.replace(context, backup)
        }
    }

    private fun prefsReplayStorm(context: Context, n: Int): AndroidStressResult =
        timed("prefs-replay", "SharedPreferences replay/churn", n) {
            val prefs = context.getSharedPreferences("qa_android_stress", Context.MODE_PRIVATE)
            repeat(n) { i ->
                prefs.edit().putLong("seq", i.toLong()).putString("payload", "packet-${i % 257}").apply()
                val seq = prefs.getLong("seq", -1L)
                if (seq < 0L) return@timed Check(false, "negative sequence")
            }
            val ok = prefs.getLong("seq", -1L) == n - 1L
            prefs.edit().clear().apply()
            Check(ok, "last=${if (ok) n - 1 else prefs.getLong("seq", -1L)}")
        }

    private data class Check(val ok: Boolean, val detail: String)

    private inline fun timed(id: String, title: String, operations: Int, block: () -> Check): AndroidStressResult {
        val start = System.nanoTime()
        val check = runCatching(block).getOrElse { Check(false, "exception=${it.javaClass.simpleName}: ${it.message.orEmpty()}") }
        val elapsed = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        return AndroidStressResult(id, title, check.ok, operations, elapsed, check.detail)
    }
}
