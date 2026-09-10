package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AckReceiptPolicyTest {
    private val id1 = "SPO2_CRITICAL:11111111-1111-1111-1111-111111111111"
    private val id2 = "SPO2_CRITICAL:22222222-2222-2222-2222-222222222222"

    @Test
    fun exactV2ReceiptClearsOnlyMatchingPendingAck() {
        val ack = "v2|7|$id1|1000|ok"
        val receipt = AckReceiptPolicy.receiptForAck(ack)!!
        assertTrue(AckReceiptPolicy.matchesPending(ack, receipt))
    }

    @Test
    fun delayedOlderReceiptCannotClearNewerPendingAck() {
        val oldAck = "v2|7|$id1|1000|old"
        val newAck = "v2|8|$id2|2000|new"
        val oldReceipt = AckReceiptPolicy.receiptForAck(oldAck)!!
        assertFalse(AckReceiptPolicy.matchesPending(newAck, oldReceipt))
    }

    @Test
    fun sameSequenceDifferentAlertCannotClearPendingAck() {
        val ack1 = "v2|9|$id1|1000|one"
        val ack2 = "v2|9|$id2|1000|two"
        val receipt1 = AckReceiptPolicy.receiptForAck(ack1)!!
        assertFalse(AckReceiptPolicy.matchesPending(ack2, receipt1))
    }

    @Test
    fun duplicateReceiptIsIdempotentAfterQueueAlreadyCleared() {
        val ack = "v2|10|$id1|1000|ok"
        val receipt = AckReceiptPolicy.receiptForAck(ack)!!
        assertFalse(AckReceiptPolicy.matchesPending(null, receipt))
    }

    @Test
    fun rebootReconstructedPendingAckStillMatchesSameReceipt() {
        val beforeReboot = "v2|11|$id1|1000|ok"
        val persisted = beforeReboot
        val afterReboot = String(persisted.toCharArray())
        val receipt = AckReceiptPolicy.receiptForAck(beforeReboot)!!
        assertTrue(AckReceiptPolicy.matchesPending(afterReboot, receipt))
    }

    @Test
    fun legacyAndV2ReceiptsNeverCrossMatch() {
        val legacy = "1700000000000|legacy"
        val v2 = "v2|12|$id1|1700000000000|v2"
        assertFalse(AckReceiptPolicy.matchesPending(v2, AckReceiptPolicy.receiptForAck(legacy)!!))
        assertFalse(AckReceiptPolicy.matchesPending(legacy, AckReceiptPolicy.receiptForAck(v2)!!))
    }

    @Test
    fun malformedPayloadsDoNotProduceReceipts() {
        assertNull(AckReceiptPolicy.receiptForAck(""))
        assertNull(AckReceiptPolicy.receiptForAck("v2|x|$id1|1|bad"))
        assertNull(AckReceiptPolicy.receiptForAck("v2|1|bad-id|1|bad"))
        assertNull(AckReceiptPolicy.receiptForAck("v2|1|SPO2_CRITICAL:ts:1700000000000|1700000000000|legacy-identity"))
        assertNull(AckReceiptPolicy.receiptForAck("v2|1|SPO2_CRITICAL:event:|1700000000000|blank-event"))
        assertNull(AckReceiptPolicy.receiptForAck("0|legacy"))
    }
}
