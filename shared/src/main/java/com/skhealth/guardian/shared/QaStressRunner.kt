package com.skhealth.guardian.shared

import kotlin.math.max
import kotlin.random.Random

data class QaStressResult(
    val id: String,
    val title: String,
    val passed: Boolean,
    val operations: Int,
    val elapsedMs: Long,
    val detail: String
) {
    val opsPerSecond: Long
        get() = if (elapsedMs <= 0L) operations.toLong() * 1000L else operations.toLong() * 1000L / elapsedMs
}

data class QaStressReport(
    val profile: String,
    val results: List<QaStressResult>
) {
    val passed: Boolean get() = results.all { it.passed }
    val totalOperations: Long get() = results.sumOf { it.operations.toLong() }
    val totalElapsedMs: Long get() = results.sumOf { it.elapsedMs }
}

/**
 * CPU/memory safe deterministic load tests for the production alarm policies.
 * No Android API, network, SMS, call or physical sensor is touched here.
 */
object QaStressRunner {
    fun run(config: AlarmConfig = AlarmConfig(), scale: Int = 1): QaStressReport {
        val s = scale.coerceIn(1, 20)
        return QaStressReport(
            profile = if (s >= 10) "HEAVY" else "STANDARD",
            results = listOf(
                normalSoak(config, 25_000 * s),
                invalidStorm(config, 10_000 * s),
                thresholdJitter(config, 15_000 * s),
                criticalBurst(config, 10_000 * s),
                heartRateBurst(config, 10_000 * s),
                pc60Soak(15_000 * s),
                deterministicFuzz(config, 15_000 * s)
            )
        )
    }

    private fun normalSoak(config: AlarmConfig, n: Int): QaStressResult = timed("normal-soak", "Normal ölçüm soak", n) {
        val e = AlarmEngine(config)
        var unexpected = 0
        repeat(n) { i ->
            val alerts = e.evaluate(HealthReading(timestampMs = i * 1000L, spo2 = 96 + (i % 3), heartRate = 65 + (i % 25)))
            unexpected += alerts.size
        }
        Check(unexpected == 0, "beklenmeyen alarm=$unexpected")
    }

    private fun invalidStorm(config: AlarmConfig, n: Int): QaStressResult = timed("invalid-storm", "Geçersiz veri fırtınası", n) {
        val e = AlarmEngine(config)
        var unexpected = 0
        repeat(n) { i ->
            unexpected += e.evaluate(HealthReading(timestampMs = i.toLong(), spo2 = 50 + (i % 50), heartRate = 160 + (i % 80), valid = false)).size
        }
        Check(unexpected == 0, "geçersiz veriden alarm=$unexpected")
    }

    private fun thresholdJitter(config: AlarmConfig, n: Int): QaStressResult = timed("threshold-jitter", "Eşik çevresi jitter", n) {
        val e = AlarmEngine(config)
        val low = max(config.spo2CriticalImmediate, config.spo2LowThreshold - 1)
        val normal = config.spo2LowThreshold
        var lowConfirmed = 0
        repeat(n) { i ->
            val spo2 = if (i % 2 == 0) low else normal
            lowConfirmed += e.evaluate(HealthReading(timestampMs = i * 1000L, spo2 = spo2, heartRate = 80))
                .count { it.type == AlertType.SPO2_LOW_CONFIRMED }
        }
        Check(lowConfirmed == 0, "alternan düşük/normal dizide doğrulanmış düşük alarm=$lowConfirmed")
    }

    private fun criticalBurst(config: AlarmConfig, n: Int): QaStressResult = timed("critical-burst", "Kritik SpO₂ recovery-cycle burst", n) {
        val e = AlarmEngine(config)
        val critical = (config.spo2CriticalImmediate - 1).coerceAtLeast(1)
        val normal = config.spo2LowThreshold.coerceAtLeast(config.spo2CriticalImmediate)
        var actual = 0
        var repeatedWhileLatched = 0
        repeat(n) { i ->
            actual += e.evaluate(HealthReading(timestampMs = i * 3_000L, spo2 = critical, heartRate = 80))
                .count { it.type == AlertType.SPO2_CRITICAL }
            repeatedWhileLatched += e.evaluate(HealthReading(timestampMs = i * 3_000L + 1_000L, spo2 = critical, heartRate = 80))
                .count { it.type == AlertType.SPO2_CRITICAL }
            e.evaluate(HealthReading(timestampMs = i * 3_000L + 2_000L, spo2 = normal, heartRate = 80))
        }
        Check(actual == n && repeatedWhileLatched == 0, "episodeAlarm=$actual/$n latchedRepeat=$repeatedWhileLatched")
    }

    private fun heartRateBurst(config: AlarmConfig, n: Int): QaStressResult = timed("hr-burst", "Yüksek nabız recovery-cycle burst", n) {
        val count = config.heartRateHighConfirmCount.coerceAtLeast(1)
        val e = AlarmEngine(config)
        var actual = 0
        var repeatedWhileLatched = 0
        var ts = 0L
        repeat(n) {
            repeat(count) {
                actual += e.evaluate(
                    HealthReading(timestampMs = ts++, spo2 = 97, heartRate = config.heartRateHighThreshold + 5)
                ).count { it.type == AlertType.HEART_RATE_HIGH_CONFIRMED }
            }
            repeatedWhileLatched += e.evaluate(
                HealthReading(timestampMs = ts++, spo2 = 97, heartRate = config.heartRateHighThreshold + 20)
            ).count { it.type == AlertType.HEART_RATE_HIGH_CONFIRMED }
            e.evaluate(HealthReading(timestampMs = ts++, spo2 = 97, heartRate = config.heartRateHighThreshold))
        }
        Check(actual == n && repeatedWhileLatched == 0, "episodeAlarm=$actual/$n latchedRepeat=$repeatedWhileLatched")
    }

    private fun pc60Soak(n: Int): QaStressResult = timed("pc60-soak", "PC-60FW uzun akış", n) {
        val p = Pc60AlarmPolicy(alarmThreshold = 85, confirmDelayMs = 120_000L, recoveryThreshold = 85, recoveryStableMs = 10_000L)
        var alarms = 0
        var recovered = 0
        repeat(n) { i ->
            // 5 dk normal, 121 sn düşük (alarm 120 sn'de tetiklenir), ardından normal dönem.
            // Politika ALARM verdiğinde pending session'ı resetlediği için bu döngüde RECOVERED beklenmez.
            val cycle = i % 440
            val spo2 = when {
                cycle < 300 -> 97
                cycle < 421 -> 84
                else -> 86
            }
            when (p.evaluate(Pc60Sample(i * 1000L, spo2, 78, 4.0, probeOff = false, pulseSearching = false))) {
                Pc60Decision.ALARM -> alarms++
                Pc60Decision.RECOVERED -> recovered++
                else -> Unit
            }
        }
        val completedCycles = n / 440
        Check(alarms in completedCycles..(completedCycles + 1) && recovered == 0, "alarm=$alarms cycle≈$completedCycles recovery=$recovered")
    }

    private fun deterministicFuzz(config: AlarmConfig, n: Int): QaStressResult = timed("deterministic-fuzz", "Deterministik izole fuzz", n) {
        val random = Random(0x5A17C0DE)
        val e = AlarmEngine(config)
        var invalidAlerts = 0
        var missedCritical = 0
        var duplicateCritical = 0
        repeat(n) { i ->
            val valid = random.nextInt(100) >= 8
            val spo2 = random.nextInt(50, 101)
            val hr = random.nextInt(35, 181)
            val baseTs = i * 3_000L
            val alerts = e.evaluate(HealthReading(timestampMs = baseTs, spo2 = spo2, heartRate = hr, valid = valid))
            if (!valid && alerts.isNotEmpty()) invalidAlerts++
            if (valid && spo2 < config.spo2CriticalImmediate && alerts.none { it.type == AlertType.SPO2_CRITICAL }) missedCritical++
            if (valid && spo2 < config.spo2CriticalImmediate) {
                duplicateCritical += e.evaluate(
                    HealthReading(timestampMs = baseTs + 1_000L, spo2 = spo2, heartRate = hr, valid = true)
                ).count { it.type == AlertType.SPO2_CRITICAL }
            }
            // Isolate the next fuzz case from this case's episode state.
            e.evaluate(
                HealthReading(
                    timestampMs = baseTs + 2_000L,
                    spo2 = config.spo2LowThreshold.coerceAtLeast(95),
                    heartRate = 80,
                    valid = true
                )
            )
        }
        Check(
            invalidAlerts == 0 && missedCritical == 0 && duplicateCritical == 0,
            "invalidAlarm=$invalidAlerts missedCritical=$missedCritical duplicateCritical=$duplicateCritical"
        )
    }

    private data class Check(val ok: Boolean, val detail: String)

    private inline fun timed(id: String, title: String, operations: Int, block: () -> Check): QaStressResult {
        val start = System.nanoTime()
        val check = runCatching(block).getOrElse { Check(false, "exception=${it.javaClass.simpleName}: ${it.message.orEmpty()}") }
        val elapsed = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        return QaStressResult(id, title, check.ok, operations, elapsed, check.detail)
    }
}
