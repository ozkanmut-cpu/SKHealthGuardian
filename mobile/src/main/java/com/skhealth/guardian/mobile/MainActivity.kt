package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
        ContextCompat.startForegroundService(this, Intent(this, WatchdogService::class.java))
        render()
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
        listOf(spo2Critical, spo2Low, hrHigh, stale).forEach(root::addView)
        root.addView(Button(this).apply { text = "Ayarları kaydet"; setOnClickListener {
            AppSettings.save(this@MainActivity, AlarmConfig(
                spo2CriticalImmediate = spo2Critical.text.toString().toIntOrNull() ?: 80,
                spo2LowThreshold = spo2Low.text.toString().toIntOrNull() ?: 90,
                heartRateHighThreshold = hrHigh.text.toString().toIntOrNull() ?: 130,
                staleDataMs = (stale.text.toString().toLongOrNull() ?: 10) * 60_000L
            ))
            Toast.makeText(this@MainActivity, "Kaydedildi ve saate senkronlandı", Toast.LENGTH_SHORT).show()
        }})
        root.addView(Button(this).apply { text = "Kişi / telefon tanımla"; setOnClickListener { startActivity(Intent(this@MainActivity, ContactsActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Gerçek SMS testi"; setOnClickListener { testSms() } })
        root.addView(Button(this).apply { text = "Gerçek arama testi"; setOnClickListener { testCall() } })
        root.addView(Button(this).apply { text = "Saat self-test gönder"; setOnClickListener { sendSelfTest() } })
        root.addView(Button(this).apply { text = "Self-test durumu"; setOnClickListener { showStatus() } })
        root.addView(Button(this).apply { text = "Sistem sağlık kontrolü"; setOnClickListener { startActivity(Intent(this@MainActivity, SystemHealthActivity::class.java)) } })
        root.addView(Button(this).apply { text = "SMS / arama kayıtları"; setOnClickListener { startActivity(Intent(this@MainActivity, DeliveryLogActivity::class.java)) } })
        root.addView(Button(this).apply { text = "Ölçüm geçmişi"; setOnClickListener { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) } })
    }

    private fun testSms() {
        val c = ContactStore.contacts(this).firstOrNull { it.smsEnabled } ?: return toast("SMS kişisi tanımlı değil")
        val ok = SmsSender(this).send(c.phoneNumber, "SK Health Guardian TEST SMS - sistem zinciri testidir.")
        DeliveryLogStore.add(this, "SMS TEST", "***${c.phoneNumber.takeLast(4)}", ok, if (ok) "test gönderim isteği kabul edildi" else "test gönderilemedi / izin yok")
        toast(if (ok) "SMS gönderildi" else "SMS gönderilemedi / izin yok")
    }

    private fun testCall() {
        val c = ContactStore.contacts(this).firstOrNull { it.callEnabled } ?: return toast("Aranacak kişi tanımlı değil")
        val ok = CallPlacer(this).call(c.phoneNumber)
        DeliveryLogStore.add(this, "ARAMA TEST", "***${c.phoneNumber.takeLast(4)}", ok, if (ok) "test araması başlatıldı" else "test araması başlatılamadı / izin yok")
        toast(if (ok) "Arama başlatıldı" else "Arama başlatılamadı / izin yok")
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
            .setTitle("Self-test")
            .setMessage("Saat: ${WatchStatusStore.status(this)}\nSon self-test: $time\nSon veri: $lastTime\nSMS izni: ${has(Manifest.permission.SEND_SMS)}\nArama izni: ${has(Manifest.permission.CALL_PHONE)}")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun requestPermissions() {
        val wanted = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT)
        val missing = wanted.filterNot { has(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 10)
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
