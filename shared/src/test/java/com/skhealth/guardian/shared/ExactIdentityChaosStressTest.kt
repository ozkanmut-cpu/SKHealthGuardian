package com.skhealth.guardian.shared

import org.junit.Assert.assertTrue
import org.junit.Test

class ExactIdentityChaosStressTest {
    @Test
    fun exactIdentityChaosSurvivesClockRollbackRestartAndLeaseRecovery() {
        val seeds = listOf(20260909, 17, 123456789, -90210, 0x5A17C0DE)
        seeds.forEach { seed ->
            val report = QaExactChaosRunner.run(operations = 500_000, seed = seed)
            assertTrue(
                "seed=$seed violations=${report.violations.take(5)} alarms=${report.alarmsCreated} " +
                    "acks=${report.acknowledgements} claims=${report.leaseClaims} " +
                    "recoveries=${report.leaseRecoveries} rollbacks=${report.clockRollbacks}",
                report.passed
            )
        }
    }
}
