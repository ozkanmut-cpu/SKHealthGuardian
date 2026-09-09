package com.skhealth.guardian.mobile

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.skhealth.guardian.shared.AlertIdentity

class AlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.statusBarColor = UiStyle.BG
        window.navigationBarColor = UiStyle.BG

        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Sağlık alarmı"
        val spo2 = intent.getIntExtra(EXTRA_SPO2, -1)
        val hr = intent.getIntExtra(EXTRA_HR, -1)
        val status = intent.getStringExtra(EXTRA_REMOTE_STATUS) ?: "Acil durum kişilerine bildirim durumu kontrol ediliyor"
        val alertId = intent.getStringExtra(EXTRA_ALERT_ID)
        val alertTs = intent.getLongExtra(EXTRA_ALERT_TS, 0L)
        val source = SourcePriorityCoordinator.activeSourceLabel(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiStyle.BG)
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }

        val banner = UiStyle.card(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiStyle.rounded(0xFF3A171A.toInt(), context = this@AlarmActivity)
        }
        banner.addView(UiStyle.text(this, "⚠  SAĞLIK ALARMI", 18f, UiStyle.RED, true, Gravity.CENTER))
        banner.addView(UiStyle.text(this, reason, 25f, UiStyle.TEXT, true, Gravity.CENTER).apply { setPadding(0, dp(12), 0, 0) })
        root.addView(banner)

        val stackMetrics = resources.configuration.fontScale >= 1.3f || resources.configuration.screenWidthDp < 360
        val metrics = LinearLayout(this).apply { orientation = if (stackMetrics) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        val spoCard = metricCard("SpO₂", if (spo2 >= 0) "$spo2%" else "—", UiStyle.RED)
        val hrCard = metricCard("Nabız", if (hr >= 0) "$hr" else "—", if (hr >= 0) UiStyle.AMBER else UiStyle.MUTED)
        if (stackMetrics) {
            metrics.addView(spoCard)
            metrics.addView(hrCard, UiStyle.sectionParams(this, 10))
        } else {
            metrics.weightSum = 2f
            metrics.addView(spoCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            metrics.addView(hrCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        }
        root.addView(metrics, UiStyle.sectionParams(this))

        val info = UiStyle.card(this)
        info.addView(UiStyle.text(this, "Kaynak", 13f, UiStyle.MUTED))
        info.addView(UiStyle.text(this, source, 16f, UiStyle.TEXT, true).apply { setPadding(0, dp(5), 0, 0) })
        info.addView(UiStyle.divider(this))
        info.addView(UiStyle.text(this, "Alarm doğrulandı. Ölçümü kontrol edin. Ciddi nefes darlığı, bilinç değişikliği veya belirgin kötüleşme varsa acil yardım alın.", 15f, UiStyle.TEXT))
        info.addView(UiStyle.text(this, status, 13f, UiStyle.MUTED).apply { setPadding(0, dp(12), 0, 0) })
        root.addView(info, UiStyle.sectionParams(this))

        root.addView(action("🔕", "Alarmı sustur", "Watch ve telefon alarmını kapat", UiStyle.RED) {
            getSystemService(NotificationManager::class.java).cancel(CRITICAL_NOTIFICATION_ID)
            WatchCommandSender(this@AlarmActivity).silenceAlarm()
            if (AlertIdentity.isValid(alertId)) {
                AlertAcknowledgementStore.acknowledge(this@AlarmActivity, alertId!!)
                EscalationScheduler.cancel(this@AlarmActivity, alertId, alertTs)
            } else {
                val acknowledgedTs = if (alertTs > 0) alertTs else System.currentTimeMillis()
                AlertAcknowledgementStore.acknowledge(this@AlarmActivity, acknowledgedTs)
                EscalationScheduler.cancel(this@AlarmActivity, acknowledgedTs)
            }
            AlarmTimelineStore.add(this@AlarmActivity, "ALARM SUSTURULDU", reason)
            finish()
        }, UiStyle.sectionParams(this, 16))

        root.addView(action("↻", "Tekrar ölç", "Watch üzerinden yeni ölçüm iste", UiStyle.BLUE) {
            WatchCommandSender(this@AlarmActivity).requestMeasurement()
            AlarmTimelineStore.add(this@AlarmActivity, "TEKRAR ÖLÇÜM", "Alarm ekranından manuel ölçüm istendi")
        }, UiStyle.sectionParams(this, 10))

        root.addView(action("☎", "Acil durum kişisini ara", "Tanımlı ilk arama kişisini kullan", UiStyle.GREEN) {
            ContactStore.contacts(this@AlarmActivity).firstOrNull { it.callEnabled }?.let { CallPlacer(this@AlarmActivity).call(it.phoneNumber) }
        }, UiStyle.sectionParams(this, 10))

        root.addView(action("⌂", "Uygulamaya dön", "Ana ekrana geç", UiStyle.MUTED) {
            startActivity(Intent(this@AlarmActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }, UiStyle.sectionParams(this, 10))

        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(UiStyle.BG); addView(root) })
    }

    private fun metricCard(title: String, value: String, color: Int) = UiStyle.card(this).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(UiStyle.text(this@AlarmActivity, title, 14f, UiStyle.MUTED, gravity = Gravity.CENTER))
        addView(UiStyle.text(this@AlarmActivity, value, 40f, color, true, Gravity.CENTER).apply { setPadding(0, dp(8), 0, 0) })
    }

    private fun action(icon: String, title: String, subtitle: String, accent: Int, block: () -> Unit) = UiStyle.card(this, 16).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(70)
        isClickable = true
        isFocusable = true
        setOnClickListener { block() }
        addView(UiStyle.text(this@AlarmActivity, icon, 23f, accent, true), LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT))
        val labels = LinearLayout(this@AlarmActivity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(UiStyle.text(this@AlarmActivity, title, 16f, UiStyle.TEXT, true))
        labels.addView(UiStyle.text(this@AlarmActivity, subtitle, 12.5f, UiStyle.MUTED).apply { setPadding(0, dp(4), 0, 0) })
        addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(UiStyle.text(this@AlarmActivity, "›", 24f, UiStyle.MUTED))
    }

    private fun dp(value: Int) = UiStyle.dp(this, value)

    companion object {
        const val EXTRA_REASON = "reason"
        const val EXTRA_SPO2 = "spo2"
        const val EXTRA_HR = "hr"
        const val EXTRA_REMOTE_STATUS = "remote_status"
        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_ALERT_TS = "alert_ts"
        const val CRITICAL_NOTIFICATION_ID = 100
    }
}
