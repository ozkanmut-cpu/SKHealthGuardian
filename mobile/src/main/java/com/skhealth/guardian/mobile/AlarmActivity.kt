package com.skhealth.guardian.mobile

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Sağlık alarmı"
        val spo2 = intent.getIntExtra(EXTRA_SPO2, -1)
        val hr = intent.getIntExtra(EXTRA_HR, -1)
        val status = intent.getStringExtra(EXTRA_REMOTE_STATUS) ?: "SMS/arama durumu kontrol ediliyor"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 72, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "KRİTİK SAĞLIK UYARISI"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = reason
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 36)
        })
        root.addView(TextView(this).apply {
            text = buildString {
                append("SpO₂: ")
                append(if (spo2 >= 0) "$spo2%" else "—")
                append("\nNabız: ")
                append(if (hr >= 0) "$hr bpm" else "—")
            }
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = status
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 28)
        })

        root.addView(Button(this).apply {
            text = "Alarmı sustur"
            setOnClickListener {
                getSystemService(NotificationManager::class.java).cancel(CRITICAL_NOTIFICATION_ID)
                WatchCommandSender(this@AlarmActivity).silenceAlarm()
                finish()
            }
        })
        root.addView(Button(this).apply {
            text = "Tekrar ölç"
            setOnClickListener {
                WatchCommandSender(this@AlarmActivity).requestMeasurement()
            }
        })
        root.addView(Button(this).apply {
            text = "Birincil kişiyi ara"
            setOnClickListener {
                ContactStore.contacts(this@AlarmActivity)
                    .firstOrNull { it.callEnabled }
                    ?.let { CallPlacer(this@AlarmActivity).call(it.phoneNumber) }
            }
        })
        root.addView(Button(this).apply {
            text = "Uygulamaya dön"
            setOnClickListener {
                startActivity(Intent(this@AlarmActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
        })

        setContentView(root)
    }

    companion object {
        const val EXTRA_REASON = "reason"
        const val EXTRA_SPO2 = "spo2"
        const val EXTRA_HR = "hr"
        const val EXTRA_REMOTE_STATUS = "remote_status"
        const val CRITICAL_NOTIFICATION_ID = 100
    }
}
