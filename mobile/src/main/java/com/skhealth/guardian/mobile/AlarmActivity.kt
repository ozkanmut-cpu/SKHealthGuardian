package com.skhealth.guardian.mobile

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Sağlık alarmı"
        val spo2 = intent.getIntExtra(EXTRA_SPO2, -1)
        val hr = intent.getIntExtra(EXTRA_HR, -1)
        val status = intent.getStringExtra(EXTRA_REMOTE_STATUS) ?: "Acil durum kişilerine bildirim durumu kontrol ediliyor"
        val alertTs = intent.getLongExtra(EXTRA_ALERT_TS, 0L)
        val source = SourcePriorityCoordinator.activeSourceLabel(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(36, 48, 36, 44) }
        root.addView(TextView(this).apply { text = "⚠ SAĞLIK ALARMI"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = reason; textSize = 28f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, 20, 0, 24); maxLines = 4 })

        val stackMetrics = resources.configuration.fontScale >= 1.3f || resources.configuration.screenWidthDp < 360
        val metrics = LinearLayout(this).apply { orientation = if (stackMetrics) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        if (stackMetrics) {
            metrics.addView(metric("SpO₂", if (spo2 >= 0) "$spo2%" else "—"))
            metrics.addView(metric("Nabız", if (hr >= 0) "$hr bpm" else "—"))
        } else {
            metrics.weightSum = 2f
            metrics.addView(metric("SpO₂", if (spo2 >= 0) "$spo2%" else "—"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            metrics.addView(metric("Nabız", if (hr >= 0) "$hr bpm" else "—"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(metrics)
        root.addView(TextView(this).apply { text = "Kaynak: $source"; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 14, 0, 18) })
        root.addView(TextView(this).apply { text = "Alarm doğrulandı. Ölçümü kontrol edin. Ciddi nefes darlığı, bilinç değişikliği veya belirgin kötüleşme varsa acil yardım alın."; textSize = 17f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 20) })
        root.addView(TextView(this).apply { text = status; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 22) })

        root.addView(actionButton("Alarm sesini sustur") {
            getSystemService(NotificationManager::class.java).cancel(CRITICAL_NOTIFICATION_ID)
            WatchCommandSender(this@AlarmActivity).silenceAlarm()
            AlertAcknowledgementStore.acknowledge(this@AlarmActivity, if (alertTs > 0) alertTs else System.currentTimeMillis())
            AlarmTimelineStore.add(this@AlarmActivity, "ALARM SUSTURULDU", reason)
            finish()
        })
        root.addView(actionButton("Tekrar ölç") {
            WatchCommandSender(this@AlarmActivity).requestMeasurement()
            AlarmTimelineStore.add(this@AlarmActivity, "TEKRAR ÖLÇÜM", "Alarm ekranından manuel ölçüm istendi")
        })
        root.addView(actionButton("Acil durum kişisini ara") {
            ContactStore.contacts(this@AlarmActivity).firstOrNull { it.callEnabled }?.let { CallPlacer(this@AlarmActivity).call(it.phoneNumber) }
        })
        root.addView(actionButton("Uygulamaya dön") {
            startActivity(Intent(this@AlarmActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        })

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        minHeight = dp(56)
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun metric(title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(8, 16, 8, 16)
        addView(TextView(this@AlarmActivity).apply { text = title; textSize = 16f; gravity = Gravity.CENTER })
        addView(TextView(this@AlarmActivity).apply { text = value; textSize = 36f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; maxLines = 2 })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_REASON = "reason"; const val EXTRA_SPO2 = "spo2"; const val EXTRA_HR = "hr"; const val EXTRA_REMOTE_STATUS = "remote_status"; const val EXTRA_ALERT_TS = "alert_ts"; const val CRITICAL_NOTIFICATION_ID = 100
    }
}
