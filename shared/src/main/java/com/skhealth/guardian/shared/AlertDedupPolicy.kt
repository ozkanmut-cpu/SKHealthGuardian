package com.skhealth.guardian.shared

object AlertDedupPolicy {
    const val DEFAULT_COOLDOWN_MS = 5 * 60_000L

    fun shouldDispatch(
        type: AlertType,
        nowMs: Long,
        lastAtMs: Long,
        lastValue: Int?,
        currentValue: Int?,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ): Boolean {
        if (lastAtMs <= 0L || nowMs - lastAtMs >= cooldownMs) return true

        val escalation = when (type) {
            AlertType.SPO2_CRITICAL, AlertType.SPO2_LOW_CONFIRMED ->
                currentValue != null && lastValue != null && currentValue <= lastValue - 3
            AlertType.HEART_RATE_HIGH_CONFIRMED ->
                currentValue != null && lastValue != null && currentValue >= lastValue + 15
            AlertType.HEART_RATE_LOW_CONFIRMED ->
                currentValue != null && lastValue != null && currentValue <= lastValue - 10
            else -> false
        }
        return escalation
    }
}
