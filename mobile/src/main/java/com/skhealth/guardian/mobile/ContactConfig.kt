package com.skhealth.guardian.mobile

import android.content.Context

data class EmergencyContact(
    val name: String,
    val phoneNumber: String,
    val smsEnabled: Boolean = true,
    val callEnabled: Boolean = false
)

object ContactStore {
    private const val PREF = "emergency_contacts"
    fun contacts(context: Context): List<EmergencyContact> {
        val rows = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getStringSet("rows", emptySet()) ?: emptySet()
        return rows.mapNotNull { row ->
            val p = row.split('\u001F')
            if (p.size < 4) null else EmergencyContact(p[0], p[1], p[2].toBoolean(), p[3].toBoolean())
        }.sortedBy { it.name }
    }
    fun save(context: Context, contacts: List<EmergencyContact>) {
        val rows = contacts.map { listOf(it.name, it.phoneNumber, it.smsEnabled, it.callEnabled).joinToString("\u001F") }.toSet()
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putStringSet("rows", rows).apply()
    }
}
