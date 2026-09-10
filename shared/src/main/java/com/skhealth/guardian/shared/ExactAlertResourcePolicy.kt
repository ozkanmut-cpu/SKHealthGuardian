package com.skhealth.guardian.shared

/**
 * Shared identity policy for resources owned by one concrete alarm instance.
 *
 * Android notification tags, pending escalations and acknowledgement state must all be keyed by
 * the exact alert identity. Never derive ownership from wall-clock time or a shared constant.
 */
object ExactAlertResourcePolicy {
    fun notificationTag(alertId: String?): String? =
        alertId?.trim()?.takeIf(AlertIdentity::isExact)

    fun ownsNotification(notificationTag: String?, alertId: String?): Boolean {
        val exact = notificationTag(alertId) ?: return false
        return notificationTag == exact
    }

    fun remainingAfterAcknowledgement(activeAlertIds: Set<String>, acknowledgedAlertId: String): Set<String> {
        if (!AlertIdentity.isExact(acknowledgedAlertId)) return activeAlertIds
        return activeAlertIds - acknowledgedAlertId
    }
}
