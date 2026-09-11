package com.skhealth.guardian.mobile

import android.content.Context

data class DosefolkBridgeStatus(
    val lastEventId: String = "",
    val lastAnchor: String = "",
    val lastTakenAtMs: Long = 0L,
    val lastReceivedAtMs: Long = 0L
)

object DosefolkBridgeStatusStore {
    private const val PREFS = "dosefolk_bridge_status"
    private const val KEY_EVENT_ID = "event_id"
    private const val KEY_ANCHOR = "anchor"
    private const val KEY_TAKEN_AT = "taken_at"
    private const val KEY_RECEIVED_AT = "received_at"

    fun load(context: Context): DosefolkBridgeStatus {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DosefolkBridgeStatus(
            lastEventId = p.getString(KEY_EVENT_ID, "").orEmpty(),
            lastAnchor = p.getString(KEY_ANCHOR, "").orEmpty(),
            lastTakenAtMs = p.getLong(KEY_TAKEN_AT, 0L),
            lastReceivedAtMs = p.getLong(KEY_RECEIVED_AT, 0L)
        )
    }

    fun markReceived(context: Context, eventId: String, anchor: String, takenAtMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENT_ID, eventId)
            .putString(KEY_ANCHOR, anchor)
            .putLong(KEY_TAKEN_AT, takenAtMs)
            .putLong(KEY_RECEIVED_AT, System.currentTimeMillis())
            .apply()
    }
}
