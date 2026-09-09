package com.skhealth.guardian.mobile

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.*
import com.skhealth.guardian.shared.AlarmConfig

class MeasurementSettingsActivity : Activity() {
    private lateinit var root: LinearLayout

    private fun number(label: String, value: Int): EditText = EditText(this).apply {
        hint = label
        setText(value.toString())
        inputType = InputType.TYPE_CLASS_NUMBER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = AppSettings.load(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply { text = "Ölçüm ve alarm ayarları"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "Alarm davranışını kaynaklara göre düzenle. Kritik eşikleri değiştirirken özellikle dikkat et."; textSize = 15f; setPadding(0, 10, 0, 18) })

        section("SpO₂ ve nabız")
        val spo2Critical = number("Kritik SpO₂ (%)", cfg.spo2CriticalImmediate)
        val spo2Low = number("Düşük SpO₂ (%)", cfg.spo2LowThreshold)
        val spo2Confirm = number("Düşük SpO₂ doğrulama sayısı", cfg.spo2ConfirmCount)
        val hrHigh = number("Yüksek nabız (bpm)", cfg.heartRateHighThreshold)
        val hrHighConfirm = number("Yüksek nabız doğrulama sayısı", cfg.heartRateHighConfirmCount)
        val hrLowEnabled = CheckBox(this).apply { text = "Düşük nabız alarmı aktif"; isChecked = cfg.heartRateLowEnabled }
        val hrLow = number("Düşük nabız (bpm)", cfg.heartRateLowThreshold)
        val hrLowConfirm = number("Düşük nabız doğrulama sayısı", cfg.heartRateLowConfirmCount)
        listOf(spo2Critical, spo2Low, spo2Confirm, hrHigh, hrHighConfirm).forEach(root::addView)
        root.addView(hrLowEnabled)
        listOf(hrLow, hrLowConfirm).forEach(root::addView)

        section("Galaxy Watch")
        val stale = number("Veri gelmeme alarmı (dk)", (cfg.staleDataMs / 60_000L).toInt())
        val watchInterval = number("Normal ölçüm aralığı (dk)", AppSettings.watchMeasurementMinutes(this))
        val watchConfirmDelay = number("Düşük değer doğrulama gecikmesi (dk)", AppSettings.watchConfirmMinutes(this))
        val retry1 = number("Teknik hata 1. tekrar (sn)", AppSettings.watchRetry1Seconds(this))
        val retry2 = number("Teknik hata 2. tekrar (sn)", AppSettings.watchRetry2Seconds(this))
        listOf(stale, watchInterval, watchConfirmDelay, retry1, retry2).forEach(root::addView)

        section("PC-60FW")
        root.addView(TextView(this).apply { text = "Parmak oksimetresi geçerli veri üretirken SpO₂ için öncelikli kaynaktır."; textSize = 14f; setPadding(0, 0, 0, 6) })
        val pcAlarm = number("Alarm eşiği (≤ %)", AppSettings.pc60AlarmThreshold(this))
        val pcConfirm = number("Düşük SpO₂ bekleme süresi (dk)", AppSettings.pc60ConfirmMinutes(this))
        val pcRecovery = number("Toparlanma eşiği (> %)", AppSettings.pc60RecoveryThreshold(this))
        val pcStable = number("Toparlanma stabilizasyonu (sn)", AppSettings.pc60StableSeconds(this))
        listOf(pcAlarm, pcConfirm, pcRecovery, pcStable).forEach(root::addView)

        section("Uzaktan uyarı")
        val escalation = number("Yanıt yoksa tekrar uyar (dk, 0=kapalı)", AppSettings.escalationMinutes(this))
        root.addView(escalation)

        root.addView(Button(this).apply {
            text = "TÜM AYARLARI KAYDET"
            setOnClickListener {
                val critical = spo2Critical.intOr(80)
                val low = spo2Low.intOr(90)
                val lowCount = spo2Confirm.intOr(2)
                val high = hrHigh.intOr(130)
                val highCount = hrHighConfirm.intOr(2)
                val lowHr = hrLow.intOr(45)
                val lowHrCount = hrLowConfirm.intOr(2)
                val staleMin = stale.intOr(10)
                val escalationMin = escalation.intOr(0)
                val intervalMin = watchInterval.intOr(5)
                val confirmMin = watchConfirmDelay.intOr(2)
                val retry1Sec = retry1.intOr(30)
                val retry2Sec = retry2.intOr(60)
                val pcAlarmValue = pcAlarm.intOr(85)
                val pcConfirmMin = pcConfirm.intOr(2)
                val pcRecoveryValue = pcRecovery.intOr(85)
                val pcStableSec = pcStable.intOr(10)

                if (critical !in 50..99 || low !in 51..100 || critical >= low) return@setOnClickListener toast("SpO₂ eşiklerini kontrol et")
                if (lowCount !in 1..5 || highCount !in 1..5 || lowHrCount !in 1..5) return@setOnClickListener toast("Doğrulama sayıları 1–5 olmalı")
                if (high !in 60..240 || lowHr !in 25..120 || lowHr >= high) return@setOnClickListener toast("Nabız eşiklerini kontrol et")
                if (staleMin !in 5..120 || escalationMin !in 0..60) return@setOnClickListener toast("Süre ayarlarını kontrol et")
                if (intervalMin !in 1..60 || confirmMin !in 1..10) return@setOnClickListener toast("Saat ölçüm sürelerini kontrol et")
                if (retry1Sec !in 5..300 || retry2Sec !in 5..600 || retry2Sec < retry1Sec) return@setOnClickListener toast("Teknik tekrar sürelerini kontrol et")
                if (pcAlarmValue !in 50..99 || pcRecoveryValue !in 50..99 || pcConfirmMin !in 1..10 || pcStableSec !in 3..120) return@setOnClickListener toast("PC-60FW ayarlarını kontrol et")

                AppSettings.setEscalationMinutes(this@MeasurementSettingsActivity, escalationMin)
                AppSettings.setWatchMeasurementMinutes(this@MeasurementSettingsActivity, intervalMin)
                AppSettings.setWatchConfirmMinutes(this@MeasurementSettingsActivity, confirmMin)
                AppSettings.setWatchRetry1Seconds(this@MeasurementSettingsActivity, retry1Sec)
                AppSettings.setWatchRetry2Seconds(this@MeasurementSettingsActivity, retry2Sec)
                AppSettings.setPc60AlarmThreshold(this@MeasurementSettingsActivity, pcAlarmValue)
                AppSettings.setPc60ConfirmMinutes(this@MeasurementSettingsActivity, pcConfirmMin)
                AppSettings.setPc60RecoveryThreshold(this@MeasurementSettingsActivity, pcRecoveryValue)
                AppSettings.setPc60StableSeconds(this@MeasurementSettingsActivity, pcStableSec)

                AppSettings.save(this@MeasurementSettingsActivity, AlarmConfig(
                    spo2CriticalImmediate = critical,
                    spo2LowThreshold = low,
                    spo2ConfirmCount = lowCount,
                    heartRateHighThreshold = high,
                    heartRateHighConfirmCount = highCount,
                    heartRateLowEnabled = hrLowEnabled.isChecked,
                    heartRateLowThreshold = lowHr,
                    heartRateLowConfirmCount = lowHrCount,
                    staleDataMs = staleMin * 60_000L
                ))
                WatchStatusStore.mark(this@MeasurementSettingsActivity, "CONFIG_BEKLENİYOR | saat ACK bekleniyor")
                toast("Ayarlar kaydedildi; saat onayı bekleniyor")
            }
        })
    }

    private fun section(title: String) { root.addView(TextView(this).apply { text = title; textSize = 20f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 26, 0, 8) }) }
    private fun EditText.intOr(default: Int): Int = text.toString().toIntOrNull() ?: default
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
