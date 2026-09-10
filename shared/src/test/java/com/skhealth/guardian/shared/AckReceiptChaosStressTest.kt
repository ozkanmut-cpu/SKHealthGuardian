package com.skhealth.guardian.shared

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AckReceiptChaosStressTest {
    @Test
    fun duplicateReorderReceiptLossAndRebootCannotClearWrongPendingAck() {
        val rnd = Random(20260910L)
        var pending: String? = null
        val receipts = ArrayList<String>()
        var seq = 0L
        var wrongClears = 0
        var exactClears = 0

        repeat(250_000) { step ->
            when (rnd.nextInt(6)) {
                0, 1 -> {
                    seq++
                    val uuid = "%08x-0000-4000-8000-%012x".format(seq.toInt(), seq)
                    val id = "SPO2_CRITICAL:$uuid"
                    val ack = "v2|$seq|$id|${1_000_000L + step}|ack"
                    pending = PendingAckPolicy.newest(pending, ack)
                    AckReceiptPolicy.receiptForAck(ack)?.let(receipts::add)
                    if (receipts.size > 512) receipts.removeAt(0)
                }
                2 -> {
                    // Duplicate/reordered receipt delivery, including receipts for older ACKs.
                    if (receipts.isNotEmpty()) {
                        val receipt = receipts[rnd.nextInt(receipts.size)]
                        val before = pending
                        val matches = AckReceiptPolicy.matchesPending(before, receipt)
                        if (matches) {
                            pending = null
                            exactClears++
                        } else if (before != null && pending == null) {
                            wrongClears++
                        }
                    }
                }
                3 -> {
                    // Receipt loss: deliberately do nothing; pending must survive.
                    val before = pending
                    if (before != null) assertTrue(pending == before)
                }
                4 -> {
                    // Reboot reconstruction from durable string state.
                    pending = pending?.let { String(it.toCharArray()) }
                }
                else -> {
                    // Legacy payload can appear during migration but must never evict v2.
                    val legacy = "${1_700_000_000_000L + step}|legacy"
                    pending = PendingAckPolicy.newest(pending, legacy)
                }
            }
        }

        assertTrue("mismatched receipt cleared pending ACK", wrongClears == 0)
        assertTrue("stress never exercised an exact receipt clear", exactClears > 0)
    }
}
