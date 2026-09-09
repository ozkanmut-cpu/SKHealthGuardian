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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Sağlık alarmı"
        val spo2 = intent.getIntExtra(EXTRA_SPO2, -1)
        val hr = intent.getIntExtra(EXTRA_HR, -1)
        val status = intent.getStringExtra(EXTRA_REMOTE_STATUS) ?: "Acil durum kişilerine bildirim durumu kontrol ediliyor"
        val alertTs = intent.getLongExtra(EXTRA_ALERT_TS, 0L)
        val source = SourcePriorityCoordinator.activeSourceLabel(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(44, 64, 44, 44) }
        root.addView(TextView(this).apply { text = "⚠ DİKKAT"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = reason; textSize = 30f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, 24, 0, 28) })

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        metrics.addView(metric("SpO₂", if (spo2 >= 0) "$spo2%" else "—"), LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(metric("Nabız", if (hr >= 0) "$hr bpm" else "—"), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(metrics)
        root.addView(TextView(this).apply { text = "Kaynak: $source"; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 16, 0, 20) })
        root.addView(TextView(this).apply { text = "Alarm doğrulandı. Ölçümünüzü kontrol edin; kendinizi ciddi şekilde kötü hissediyorsanız acil yardım alın."; textSize = 17f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 22) })
        root.addView(TextView(this).apply { text = status; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 24) })

        root.addView(Button(this).apply {
            text = "ALARM SESİNİ SUSTUR"
            setOnClickListener {
                getSystemService(NotificationManager::class.java).cancel(CRITICAL_NOTIFICATION_ID)
                WatchCommandSender(this@AlarmActivity).silenceAlarm()
                AlertAcknowledgementStore.acknowledge(this@AlarmActivity, if (alertTs > 0) alertTs else System.currentTimeMillis())
                AlarmTimelineStore.add(this@AlarmActivity, "ALARM SUSTURULDU", reason)
                finish()
            }
        })
        root.addView(Button(this).apply { text = "Tekrar ölç"; setOnClickListener { WatchCommandSender(this@AlarmActivity).requestMeasurement(); AlarmTimelineStore.add(this@AlarmActivity, "TEKRAR ÖLÇÜM", "Alarm ekranından manuel ölçüm istendi") } })
        root.addView(Button(this).apply {
            text = "ACİL DURUM KİŞİSİNİ ARA"
            setOnClickListener { ContactStore.contacts(this@AlarmActivity).firstOrNull { it.callEnabled }?.let { CallPlacer(this@AlarmActivity).call(it.phoneNumber) } }
        })
        root.addView(Button(this).apply { text = "Uygulamaya dön"; setOnClickListener { startActivity(Intent(this@AlarmActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish() } })
        setContentView(root)
    }

    private fun metric(title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(8, 18, 8, 18)
        addView(TextView(this@AlarmActivity).apply { text = title; textSize = 16f; gravity = Gravity.CENTER })
        addView(TextView(this@AlarmActivity).apply { text = value; textSize = 36f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
    }

    companion object {
        const val EXTRA_REASON = "reason"; const val EXTRA_SPO2 = "spo2"; const val EXTRA_HR = "hr"; const val EXTRA_REMOTE_STATUS = "remote_status"; const val EXTRA_ALERT_TS = "alert_ts"; const val CRITICAL_NOTIFICATION_ID = 100
    }
}
