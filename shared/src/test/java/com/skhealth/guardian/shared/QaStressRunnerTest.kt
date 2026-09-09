package com.skhealth.guardian.shared

import org.junit.Assert.assertTrue
import org.junit.Test

class QaStressRunnerTest {
    @Test
    fun standardStressProfilePasses() {
        val report = QaStressRunner.run(AlarmConfig(), scale = 1)
        assertTrue(report.results.joinToString("\n") { "${it.id}: ${it.detail}" }, report.passed)
        assertTrue(report.totalOperations >= 100_000L)
    }
}
