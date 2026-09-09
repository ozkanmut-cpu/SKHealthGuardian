package com.skhealth.guardian.mobile
import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
class HistoryActivity: Activity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(ScrollView(this).apply { addView(TextView(this@HistoryActivity).apply { textSize=16f; setPadding(24,24,24,24); text=HistoryStore.formatted(this@HistoryActivity,300).ifBlank { "Henüz ölçüm yok" } }) }) }
}
