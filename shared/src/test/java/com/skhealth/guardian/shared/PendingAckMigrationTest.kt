package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingAckMigrationTest {
    private val id = "SPO2_CRITICAL:11111111-1111-1111-1111-111111111111"

    @Test
    fun v2AlwaysSupersedesLegacyRegardlessOfNumericMagnitude() {
        val legacy = "1893456000000|legacy"
        val v2 = "v2|1|$id|1000|exact"
        assertEquals(v2, PendingAckPolicy.newest(legacy, v2))
    }

    @Test
    fun legacyCanNeverReplaceExistingV2() {
        val v2 = "v2|9|$id|1000|exact"
        val legacy = "9999999999999|legacy"
        assertEquals(v2, PendingAckPolicy.newest(v2, legacy))
    }

    @Test
    fun sameProtocolStillUsesMonotonicOrder() {
        val older = "v2|10|$id|1000|older"
        val newer = "v2|11|$id|1001|newer"
        assertEquals(newer, PendingAckPolicy.newest(older, newer))
        assertEquals(newer, PendingAckPolicy.newest(newer, older))
    }
}
