package com.skhealth.guardian.shared

import org.junit.Assert.assertTrue
import org.junit.Test

class QaChaosRunnerTest {
    @Test
    fun deterministicHalfMillionEventChaosPasses() {
        val report = QaChaosRunner.run(operations = 500_000, seed = 20260909)
        assertTrue(report.violations.joinToString("\n"), report.passed)
        assertTrue("duplicate rejection was not exercised", report.duplicateReadingsRejected > 0)
        assertTrue("remote suppression was not exercised", report.remoteActionsSuppressedAfterAck > 0)
        assertTrue("restart was not exercised", report.processRestarts > 0)
        assertTrue("source flap was not exercised", report.sourceFlaps > 0)
    }

    @Test
    fun tenSeedsCoverFiveMillionEvents() {
        repeat(10) { i ->
            val seed = 20260909 + i * 997
            val report = QaChaosRunner.run(operations = 500_000, seed = seed)
            assertTrue("seed=$seed\n${report.violations.joinToString("\n")}", report.passed)
        }
    }
}
