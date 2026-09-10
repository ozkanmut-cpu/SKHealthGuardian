package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcurrentExactAckChaosStressTest {
    @Test
    fun acknowledgementNeverRemovesAnotherExactAlarmResources() {
        val currentIds = (0 until 512).map { index ->
            val suffix = "%08d-1111-2222-3333-%012d".format(index, index.toLong())
            if (index % 2 == 0) {
                "SPO2_CRITICAL:event:$suffix"
            } else {
                "HEART_RATE_HIGH_CONFIRMED:event:$suffix"
            }
        }.toMutableList()

        var activeNotifications = currentIds.toMutableSet()
        var activeEscalations = currentIds.toMutableSet()
        val acknowledged = ArrayDeque<String>()
        var seed = 0x6A09E667F3BCC909L

        repeat(500_000) { step ->
            seed = seed * 2862933555777941757L + 3037000493L
            val index = ((seed ushr 1) % currentIds.size).toInt()
            val id = currentIds[index]

            when (((seed ushr 12) and 7L).toInt()) {
                0, 1, 2 -> {
                    // ACK, including duplicate ACKs. Exact equality catches any collateral removal.
                    val expectedNotifications = activeNotifications - id
                    val expectedEscalations = activeEscalations - id

                    activeNotifications = ExactAlertResourcePolicy
                        .remainingAfterAcknowledgement(activeNotifications, id)
                        .toMutableSet()
                    activeEscalations = ExactAlertResourcePolicy
                        .remainingAfterAcknowledgement(activeEscalations, id)
                        .toMutableSet()

                    assertEquals("step=$step ACK $id touched another notification", expectedNotifications, activeNotifications)
                    assertEquals("step=$step ACK $id touched another escalation", expectedEscalations, activeEscalations)

                    acknowledged.addLast(id)
                    if (acknowledged.size > 1024) acknowledged.removeFirst()
                }
                3 -> {
                    // A genuinely new incident reuses one bounded slot. The previous incident is
                    // logically recovered before the replacement is activated, keeping the chaos
                    // state bounded while exercising hundreds of thousands of fresh exact IDs.
                    activeNotifications.remove(id)
                    activeEscalations.remove(id)
                    val replacement = id.substringBefore(":event:") + ":event:replacement-$step-$index"
                    if (!AlertIdentity.isExact(replacement)) {
                        throw AssertionError("step=$step generated non-exact replacement $replacement")
                    }
                    currentIds[index] = replacement
                    activeNotifications += replacement
                    activeEscalations += replacement
                }
                4 -> {
                    // Delayed ACK for an old alarm must be a no-op except if that exact old identity
                    // is still active (which is the expected exact-resource behavior).
                    val old = acknowledged.firstOrNull() ?: id
                    val expectedNotifications = activeNotifications - old
                    val expectedEscalations = activeEscalations - old
                    activeNotifications = ExactAlertResourcePolicy
                        .remainingAfterAcknowledgement(activeNotifications, old)
                        .toMutableSet()
                    activeEscalations = ExactAlertResourcePolicy
                        .remainingAfterAcknowledgement(activeEscalations, old)
                        .toMutableSet()
                    assertEquals("step=$step delayed ACK touched unrelated notification", expectedNotifications, activeNotifications)
                    assertEquals("step=$step delayed ACK touched unrelated escalation", expectedEscalations, activeEscalations)
                }
                else -> {
                    // Reboot reconstruction: serialized state is reconstructed without identity loss.
                    activeNotifications = activeNotifications.toList().toMutableSet()
                    activeEscalations = activeEscalations.toList().toMutableSet()
                }
            }

            // Full-set exactness checks are periodic to keep this a 500k-operation stress test
            // without accidentally turning validation itself into quadratic work.
            if ((step and 0xFF) == 0) {
                assertTrue(activeNotifications.size <= currentIds.size)
                assertTrue(activeEscalations.size <= currentIds.size)
                assertTrue(activeNotifications.all(AlertIdentity::isExact))
                assertTrue(activeEscalations.all(AlertIdentity::isExact))
            }
        }

        assertTrue(activeNotifications.all(AlertIdentity::isExact))
        assertTrue(activeEscalations.all(AlertIdentity::isExact))
    }
}
