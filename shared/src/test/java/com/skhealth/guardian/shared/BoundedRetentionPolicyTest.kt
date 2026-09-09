package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedRetentionPolicyTest {
    @Test
    fun protectedIdSurvivesWindowThenPrunesAfterProtectionEnds() {
        val maxEntries = 256
        val protectedId = "protected-ack"
        var state = listOf(protectedId)

        repeat(600) { i ->
            state = BoundedRetentionPolicy.retain(
                existingOrderedIds = state,
                newId = "ack-$i",
                maxEntries = maxEntries,
                protectedIds = setOf(protectedId)
            )
        }

        assertTrue(protectedId in state)
        assertEquals(maxEntries + 1, state.size)
        assertEquals((344 until 600).map { "ack-$it" }, state.filter { it != protectedId })

        state = BoundedRetentionPolicy.retain(
            existingOrderedIds = state,
            newId = "ack-600",
            maxEntries = maxEntries,
            protectedIds = emptySet()
        )

        assertFalse(protectedId in state)
        assertEquals(maxEntries, state.size)
        assertEquals("ack-345", state.first())
        assertEquals("ack-600", state.last())
    }

    @Test
    fun protectedIdsAreUniqueAndDoNotConsumeRecentWindow() {
        val state = BoundedRetentionPolicy.retain(
            existingOrderedIds = listOf("p", "old", "p", "recent"),
            newId = "new",
            maxEntries = 2,
            protectedIds = setOf("p")
        )

        assertEquals(listOf("p", "recent", "new"), state)
    }
}
