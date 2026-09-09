package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Wearable
import com.skhealth.guardian.shared.AlarmConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private fun edit(label: String, value: String): EditText = EditText(this).apply { hint = label; setText(value); inputType = 2 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        startWatchdogIfReady()
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) startWatchdogIfReady()
    }

    private fun startWatchdogIfReady() {
        val bluetoothReady = Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT)
        if (!bluetoothReady) return
        runCatching { ContextCompat.startForegroundService(this, Intent(this, WatchdogService::class.java)) }
    }

    private fun render() {
        val cfg = AppSettings.load(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply { text = "SK Health Guardian"; textSize = 24f })
        val spo2Critical = edit("Kritik SpO₂", cfg.spo2CriticalImmediate.toString())
        val spo2Low = edit("Düşük SpO₂", cfg.spo2LowThreshold.toString())
        val hrHigh = edit("Yüksek nabız", cfg.heartRateHighThreshold.toString())
        val stale = edit("Veri gelmeme alarmı (dk)", (cfg.staleDataMs/60_000).toString())
        val escalation = edit("Yanıt yoksa tekrar uyar (dk, 0=kapalı)", AppSettings.escalationMinutes(this).toString())
        listOf(spo2Critical, spo2Low, hrHigh, stale, escalation).forEach(root::addView)
        root.addView(Button(this).apply { text = "Ayarları kaydet"; setOnClickListener {
            val critical = spo2Critical.text.toString().toIntOrNull() ?: 80
            val low = spo2Low.text.toString().toIntOrNull() ?: 90
            val highHr = hrHigh.text.toString().toIntOrNull() ?: 130
            val staleMinutes = stale.text.toString().toLongOrNull() ?: 10L
            val escalationMinutes = escalation.text.toString().toIntOrNull() ?: 0

            if (critical !in 50..99 || low !in 51..100 || critical >= low) return@setOnClickListener toast("SpO₂ eşiklerini kontrol et: kritik değer düşük eşikten küçük olmalı")
            if (highHr !in 60..240) return@setOnClickListener toast("Yüksek nabız eşiği 60–240 arasında olmalı")
            if (staleMinutes !in 5..120) return@setOnClickListener toast("Veri gelmeme süresi 5–120 dk arasında olmalı")
            if (escalationMinutes !in 0..60) return@setOnClickListener toast("Tekrar uyarı süresi 0–60 dk arasında olmalı")

            val newConfig = AlarmConfig(
                spo2CriticalImmediate = critical,
                spo2LowThreshold = low,
                heartRateHighThreshold = highHr,
                staleDataMs = staleMinutes * 60_000L
            )
            AppSettings.save(this@MainActivity, newConfig)
            AppSettings.setEscalationMinutes(this@MainActivity, escalationMinutes)
            WatchStatusStore.mark(this@MainActivity, "CONFIG_BEKLENİYOR | saat ACK bekleniyor")
            Toast.makeText(this@MainActivity, "Kaydedildi; saat onayı bekleniyor", Toast.LENGTH_SHORT).show()
        }})
        root.addView(Button(this).apply { text = "Tam sistem testi"; setOnClickListener { startActivity(Intent(this@MainActivity, SystemTestActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Kişi / telefon tanımla"; setOnClickListener { startActivity(Intent(this@MainActivity, ContactsActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Gerçek SMS testi"; setOnClickListener { testSms() } })
        root.addView(Button(this).apply { text = "Gerçek arama testi"; setOnClickListener { testCall() } })
        root.addView(Button(this).apply { text = "Saat self-test gönder"; setOnClickListener { sendSelfTest() } })
        root.addView(Button(this).apply { text = "Self-test / saat durumu"; setOnClickListener { showStatus() } })
        root.addView(Button(this).apply { text = "Sistem sağlık kontrolü"; setOnClickListener { startActivity(Intent(this@MainActivity, SystemHealthActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Alarm olay geçmişi"; setOnClickListener { startActivity(Intent(this@MainActivity, AlarmTimelineActivity::class.java)) } })
        root.addView(Button(this).apply { text = "SMS / arama kayıtları"; setOnClickListener { startActivity(Intent(this@MainActivity, DeliveryLogActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Ölçüm geçmişi"; setOnClickListener { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Ölçüm grafikleri"; setOnClickListener { startActivity(Intent(this@MainActivity, HistoryChartActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Yedekle / dışa aktar"; setOnClickListener { startActivity(Intent(this@MainActivity, ExportActivity::class.java)) } })
    }

    private fun testSms() {
        val c = ContactStore.contacts(this).firstOrNull { it.smsEnabled } ?: return toast("SMS kişisi tanımlı değil")
        val ok = SmsSender(this).send(c.phoneNumber, "SK Health Guardian TEST SMS - sistem zinciri testidir.")
        DeliveryLogStore.add(this, "SMS TEST", "***${c.phoneNumber.takeLast(4)}", ok, if (ok) "test SMS modem kuyruğuna alındı; sonuç bekleniyor" else "test SMS kuyruğa alınamadı / izin yok")
        toast(if (ok) "SMS kuyruğa alındı; gerçek sonuç kayıt ekranına düşecek" else "SMS kuyruğa alınamadı / izin yok")
    }

    private fun testCall() {
        val targets = ContactStore.contacts(this).filter { it.callEnabled }
        if (targets.isEmpty()) return toast("Aranacak kişi tanımlı değil")
        for (c in targets) {
            val ok = CallPlacer(this).call(c.phoneNumber)
            DeliveryLogStore.add(this, "ARAMA TEST", "***${c.phoneNumber.takeLast(4)}", ok, if (ok) "test araması başlatıldı" else "başlatılamadı; sonraki kişi deneniyor")
            if (ok) return toast("Arama başlatıldı")
        }
        toast("Hiçbir arama kişisi başlatılamadı / izin yok")
    }

    private fun sendSelfTest() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) return@addOnSuccessListener toast("Saat bağlı görünmüyor")
            nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id, "/health/selftest", "ping".toByteArray()) }
            toast("Self-test isteği gönderildi")
        }
    }

    private fun showStatus() {
        val ts = WatchStatusStore.timestamp(this)
        val time = if (ts == 0L) "yok" else SimpleDateFormat("HH:mm:ss", Locale("tr","TR")).format(Date(ts))
        val last = MonitoringState.lastReading(this)
        val lastTime = if (last == 0L) "yok" else SimpleDateFormat("HH:mm:ss", Locale("tr","TR")).format(Date(last))
        android.app.AlertDialog.Builder(this)
            .setTitle("Saat durumu")
            .setMessage("Saat: ${WatchStatusStore.status(this)}\nSon durum/ACK: $time\nSon veri: $lastTime\nSMS izni: ${has(Manifest.permission.SEND_SMS)}\nArama izni: ${has(Manifest.permission.CALL_PHONE)}")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun requestPermissions() {
        val wanted = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT)
        val missing = wanted.filterNot { has(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMISSIONS)
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_PERMISSIONS = 10
    }
}
