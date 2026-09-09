package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SequentialFailoverTest {
    @Test
    fun stopsAtFirstSuccessfulTarget() {
        val attempted = mutableListOf<String>()
        val winner = SequentialFailover.firstSuccessful(listOf("A", "B", "C")) { target ->
            attempted += target
            target == "B"
        }

        assertEquals("B", winner)
        assertEquals(listOf("A", "B"), attempted)
    }

    @Test
    fun triesAllTargetsWhenNoneCanStart() {
        val attempted = mutableListOf<Int>()
        val winner = SequentialFailover.firstSuccessful(listOf(1, 2, 3)) { target ->
            attempted += target
            false
        }

        assertNull(winner)
        assertEquals(listOf(1, 2, 3), attempted)
    }
}
