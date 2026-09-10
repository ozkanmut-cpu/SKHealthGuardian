package com.skhealth.guardian.shared

import org.junit.Assert.assertTrue
import org.junit.Test

class ConcurrentExactAckChaosStressTest {
    @Test
    fun acknowledgementNeverRemovesAnotherExactAlarmResources() {
        val alarmIds = (0 until 512).map { index ->
            val suffix = "%08d-1111-2222-3333-%012d".format(index, index.toLong())
            if (index % 2 == 0) {
                "SPO2_CRITICAL:event:$suffix"
            } else {
                "HEART_RATE_HIGH_CONFIRMED:event:$suffix"
            }
        }

        var activeNotifications = alarmIds.toMutableSet()
        var activeEscalations = alarmIds.toMutableSet()
        val acknowledged = mutableSetOf<String>()
        var seed = 0x6A09E667F3BCC909L

        repeat(500_000) { step ->
            seed = seed * 2862933555777941757L + 3037000493L
            val index = ((seed ushr 1) % alarmIds.size).toInt()
            val id = alarmIds[index]

            when (((seed ushr 12) and 7L).toInt()) {
                0, 1, 2 -> {
                    // ACK, including duplicate/delayed ACKs. Snapshot all unrelated resources first.
                    val unrelatedNotifications = activeNotifications - id
                    val unrelatedEscalations = activeEscalations - id

                    activeNotifications = ExactAlertResourcePolicy
                        .remainingAfterAcknowledgement(activeNotifications, id)
                        .toMutableSet()
                    activeEscalations = ExactAlertResourcePolicy
                        .remainingAfterAcknowledgement(activeEscalations, id)
                        .toMutableSet()
                    acknowledged += id

                    if (!activeNotifications.containsAll(unrelatedNotifications)) {
                        throw AssertionError("step=$step ACK $id removed another notification")
                    }
                    if (!activeEscalations.containsAll(unrelatedEscalations)) {
                        throw AssertionError("step=$step ACK $id removed another escalation")
                    }
                }
                3 -> {
                    // A genuinely new incident reuses the test slot by replacing its exact identity.
                    val replacement = if (id.contains(":event:")) {
                        id.substringBefore(":event:") + ":event:replacement-$step-$index"
                    } else id
                    if (AlertIdentity.isExact(replacement)) {
                        activeNotifications += replacement
                        activeEscalations += replacement
                    }
                }
                4 -> {
                    // Delayed receipt/ACK for an already-acknowledged old alarm must be a no-op.
                    val old = acknowledged.firstOrNull() ?: id
                    val beforeN = activeNotifications.toSet()
                    val beforeE = activeEscalations.toSet()
                    activeNotifications = ExactAlertResourcePolicy
                        .remainingAfterAcknowledgement(activeNotifications, old)
                        .toMutableSet()
                    activeEscalations = ExactAlertResourcePolicy
                        .remainingAfterAcknowledgement(activeEscalations, old)
                        .toMutableSet()
                    if ((beforeN - old) != activeNotifications) {
                        throw AssertionError("step=$step delayed ACK touched unrelated notification")
                    }
                    if ((beforeE - old) != activeEscalations) {
                        throw AssertionError("step=$step delayed ACK touched unrelated escalation")
                    }
                }
                else -> {
                    // Reboot reconstruction: sets are serialized logically and reconstructed.
                    activeNotifications = activeNotifications.toList().toMutableSet()
                    activeEscalations = activeEscalations.toList().toMutableSet()
                }
            }

            assertTrue(activeNotifications.all(AlertIdentity::isExact))
            assertTrue(activeEscalations.all(AlertIdentity::isExact))
        }
    }
}
