package com.skhealth.guardian.shared

import org.junit.Assert.assertTrue
import org.junit.Test

class ExactIdentityChaosStressTest {
    @Test
    fun exactIdentityChaosSurvivesClockRollbackRestartAndLeaseRecovery() {
        val seeds = listOf(20260909, 17, 123456789, -90210, 0x5A17C0DE)
        seeds.forEach { seed ->
            val report = QaExactChaosRunner.run(operations = 500_000, seed = seed)
            val diagnostic =
                "EXACT_CHAOS seed=$seed passed=${report.passed} violations=${report.violations.take(10)} " +
                    "alarms=${report.alarmsCreated} acks=${report.acknowledgements} " +
                    "allowed=${report.remoteAllowed} suppressed=${report.remoteSuppressed} " +
                    "claims=${report.leaseClaims} recoveries=${report.leaseRecoveries} " +
                    "restarts=${report.processRestarts} rollbacks=${report.clockRollbacks} " +
                    "sourceFlaps=${report.sourceFlaps}"
            println(diagnostic)
            assertTrue(diagnostic, report.passed)
        }
    }
}
