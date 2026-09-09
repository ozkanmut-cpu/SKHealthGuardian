package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class QaHeavyStressTest {
    @Test
    fun millionOperationHeavyProfilePasses() {
        val report = QaStressRunner.run(AlarmConfig(), scale = 10)
        assertTrue(report.results.joinToString("\n") { "${it.id}: ${it.detail}" }, report.passed)
        assertTrue("operations=${report.totalOperations}", report.totalOperations >= 1_000_000L)
    }

    @Test
    fun eightParallelAlarmStreamsDoNotLeakState() {
        val workers = 8
        val perWorker = 100_000
        val executor = Executors.newFixedThreadPool(workers)
        try {
            val jobs = (0 until workers).map { worker ->
                Callable {
                    val cfg = AlarmConfig(spo2ConfirmCount = 2, heartRateHighConfirmCount = 2)
                    val engine = AlarmEngine(cfg)
                    var unexpected = 0
                    var expectedCritical = 0
                    var actualCritical = 0
                    repeat(perWorker) { i ->
                        val phase = (i + worker) % 20
                        val reading = when {
                            phase == 0 -> {
                                expectedCritical++
                                HealthReading(timestampMs = i.toLong(), spo2 = 79, heartRate = 80)
                            }
                            phase in 1..4 -> HealthReading(timestampMs = i.toLong(), spo2 = 97, heartRate = 70 + phase)
                            phase == 5 -> HealthReading(timestampMs = i.toLong(), spo2 = 89, heartRate = 80)
                            else -> HealthReading(timestampMs = i.toLong(), spo2 = 96, heartRate = 82)
                        }
                        val alerts = engine.evaluate(reading)
                        actualCritical += alerts.count { it.type == AlertType.SPO2_CRITICAL }
                        unexpected += alerts.count { it.type != AlertType.SPO2_CRITICAL }
                    }
                    Triple(expectedCritical, actualCritical, unexpected)
                }
            }
            val results = executor.invokeAll(jobs).map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(workers, results.size)
            results.forEachIndexed { index, r ->
                assertEquals("worker=$index critical mismatch", r.first, r.second)
                assertEquals("worker=$index unexpected alarms", 0, r.third)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun repeatedConfigResetDoesNotCarryCounters() {
        val engine = AlarmEngine(AlarmConfig(spo2ConfirmCount = 2))
        var falseConfirmed = 0
        repeat(50_000) { i ->
            engine.evaluate(HealthReading(timestampMs = i * 2L, spo2 = 89, heartRate = 80))
            engine.updateConfig(AlarmConfig(spo2ConfirmCount = 2))
            falseConfirmed += engine.evaluate(HealthReading(timestampMs = i * 2L + 1, spo2 = 89, heartRate = 80))
                .count { it.type == AlertType.SPO2_LOW_CONFIRMED }
            engine.updateConfig(AlarmConfig(spo2ConfirmCount = 2))
        }
        assertEquals(0, falseConfirmed)
    }
}
