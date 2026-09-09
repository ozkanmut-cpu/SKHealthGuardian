package com.skhealth.guardian.shared

/**
 * Retention policy for durable bounded ID sets.
 * Protected IDs survive the normal recent-entry window while they are referenced by live state
 * (for example a still-pending exact escalation). Once protection is removed, normal pruning
 * applies again on the next write.
 */
object BoundedRetentionPolicy {
    fun retain(
        existingOrderedIds: List<String>,
        newId: String,
        maxEntries: Int,
        protectedIds: Set<String> = emptySet()
    ): List<String> {
        if (newId.isBlank() || maxEntries <= 0) return existingOrderedIds

        val ordered = existingOrderedIds
            .asSequence()
            .filter { it.isNotBlank() && it != newId }
            .toMutableList()
        ordered += newId

        val protected = ordered.filter { it in protectedIds }.distinct()
        val recentUnprotected = ordered
            .filter { it !in protectedIds }
            .takeLast(maxEntries)

        return (protected + recentUnprotected).distinct()
    }
}
