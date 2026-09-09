package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Wearable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemTestActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply { text = "Kurulum / QA sihirbazı"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Telefon izinlarını, acil durum kişilerini, Galaxy Watch bağlantısını, veri tazeliğini ve PC-60FW durumunu tek ekranda kontrol eder. Gerçek SMS/arama ve alarm ekranı testleri yalnız sen başlatırsan çalışır."
            setPadding(0, 16, 0, 16)
        })
        summary = TextView(this).apply { textSize = 20f; setPadding(0, 8, 0, 16) }
        output = TextView(this).apply { textSize = 16f }
        root.addView(summary)
        root.addView(output)
        root.addView(Button(this).apply {
            text = "Tüm kontrolleri yenile"
            setOnClickListener { runPreflight() }
        })
        root.addView(Button(this).apply {
            text = "Saat self-test + manuel ölçüm"
            setOnClickListener { runWatchTest() }
        })
        root.addView(Button(this).apply {
            text = "PC-60FW ekranını aç"
            setOnClickListener { startActivity(Intent(this@SystemTestActivity, Pc60Activity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "Alarm ekranını güvenli test et"
            setOnClickListener { previewAlarmScreen() }
        })
        root.addView(Button(this).apply {
            text = "Gerçek SMS + arama testi"
            setOnClickListener { confirmRealCommunicationTest() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        runPreflight()
    }

    override fun onResume() {
        super.onResume()
        if (::output.isInitialized) runPreflight()
    }

    private fun runPreflight() {
        val contacts = ContactStore.contacts(this)
        val smsContact = contacts.any { it.smsEnabled }
        val callContact = contacts.any { it.callEnabled }
        val pc60 = Pc60StatusStore.load(this)
        val pc60Configured = Pc60StatusStore.savedAddress(this).isNotBlank() || pc60.packetCount > 0
        val pc60Fresh = pc60.lastPacketAt > 0 && System.currentTimeMillis() - pc60.lastPacketAt in 0..15_000L && !pc60.probeOff && !pc60.pulseSearching
        val lastReading = MonitoringState.lastReading(this)
        val watchDataFresh = lastReading > 0 && System.currentTimeMillis() - lastReading <= AppSettings.load(this).staleDataMs

        val required = mutableListOf<Pair<String, Boolean>>()
        required += "SMS izni" to has(Manifest.permission.SEND_SMS)
        required += "Arama izni" to has(Manifest.permission.CALL_PHONE)
        required += "Bildirim izni" to (Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS))
        required += "Bluetooth bağlantı izni" to (Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT))
        required += "Bluetooth tarama izni" to (Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_SCAN))
        required += "SMS gönderilecek kişi" to smsContact
        required += "Aranacak kişi" to callContact

        val lines = mutableListOf<String>()
        required.forEach { (label, ok) -> lines += status(label, ok) }
        lines += info("Son Watch verisi", if (lastReading == 0L) "henüz yok" else formatAge(lastReading) + if (watchDataFresh) " • taze" else " • eski")
        lines += info("Saat son durumu", WatchStatusStore.status(this))
        lines += if (!pc60Configured) "○ PC-60FW: henüz yapılandırılmamış (Watch ile kullanım mümkün)" else status("PC-60FW geçerli veri akışı", pc60Fresh) + " • ${pc60.state}"
        if (pc60Configured && pc60.lastPacketAt > 0) lines += info("PC-60FW son paket", formatAge(pc60.lastPacketAt))
        lines += info("Aktif alarm kaynağı", SourcePriorityCoordinator.activeSourceLabel(this))

        output.text = lines.joinToString("\n") + "\nSaat bağlantısı kontrol ediliyor…"
        val localOk = required.all { it.second }

        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            showSummary(false, listOf("Bluetooth bağlantı izni eksik"))
            return
        }

        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val watchConnected = nodes.isNotEmpty()
            output.append("\n${if (watchConnected) "✓" else "✗"} Galaxy Watch bağlantısı${if (watchConnected) " (${nodes.size})" else " yok"}")
            val missing = required.filterNot { it.second }.map { it.first }.toMutableList()
            if (!watchConnected) missing += "Galaxy Watch bağlantısı"
            if (pc60Configured && !pc60Fresh) missing += "PC-60FW geçerli/taze veri akışı"
            showSummary(localOk && watchConnected && (!pc60Configured || pc60Fresh), missing)
            AlarmTimelineStore.add(this, "QA ÖN KONTROL", if (missing.isEmpty()) "Sistem hazır" else "Eksikler: ${missing.joinToString()}")
        }.addOnFailureListener {
            output.append("\n✗ Saat bağlantısı okunamadı: ${it.javaClass.simpleName}")
            showSummary(false, listOf("Saat bağlantısı okunamadı"))
        }
    }

    private fun showSummary(ready: Boolean, missing: List<String>) {
        summary.text = if (ready) {
            "✓ SİSTEM HAZIR"
        } else {
            "⚠ EKSİKLER VAR (${missing.size})\n" + missing.joinToString("\n") { "• $it" }
        }
    }

    private fun runWatchTest() {
        val cfg = AppSettings.load(this)
        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            output.append("\n✗ Saat testi: Bluetooth bağlantı izni yok")
            return
        }
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                output.append("\n✗ Saat testi: saat bağlı değil")
                return@addOnSuccessListener
            }
            WatchCommandSender(this).sendConfig(cfg)
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, "/health/selftest", "qa-wizard".toByteArray())
            }
            WatchCommandSender(this).requestMeasurement()
            output.append("\n✓ Config gönderildi\n✓ Saat self-test gönderildi\n✓ Manuel ölçüm istendi")
            AlarmTimelineStore.add(this, "QA SAAT TESTİ", "Config + self-test + manuel ölçüm gönderildi")
        }.addOnFailureListener {
            output.append("\n✗ Saat testi başarısız: ${it.javaClass.simpleName}")
        }
    }

    private fun previewAlarmScreen() {
        AlarmTimelineStore.add(this, "QA ALARM EKRANI", "Güvenli alarm ekranı önizlemesi açıldı; SMS/arama otomatik tetiklenmedi")
        startActivity(Intent(this, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_REASON, "QA TESTİ — gerçek sağlık alarmı değildir")
            putExtra(AlarmActivity.EXTRA_SPO2, 88)
            putExtra(AlarmActivity.EXTRA_HR, 132)
            putExtra(AlarmActivity.EXTRA_REMOTE_STATUS, "TEST MODU • otomatik SMS/arama gönderilmedi")
            putExtra(AlarmActivity.EXTRA_ALERT_TS, System.currentTimeMillis())
        })
    }

    private fun confirmRealCommunicationTest() {
        AlertDialog.Builder(this)
            .setTitle("Gerçek iletişim testi")
            .setMessage("Bu test gerçek SMS gönderir ve tanımlı arama kişisini gerçekten arar. Devam edilsin mi?")
            .setNegativeButton("İptal", null)
            .setPositiveButton("Testi başlat") { _, _ -> runRealCommunicationTest() }
            .show()
    }

    private fun runRealCommunicationTest() {
        val contacts = ContactStore.contacts(this)
        val smsTarget = contacts.firstOrNull { it.smsEnabled }
        val smsOk = smsTarget?.let {
            SmsSender(this).send(it.phoneNumber, "SK Health Guardian QA TESTİ - bu gerçek bir test mesajıdır.")
        } ?: false
        if (smsTarget != null) {
            DeliveryLogStore.add(this, "SMS QA TEST", mask(smsTarget.phoneNumber), smsOk, if (smsOk) "modem kuyruğuna alındı" else "başlatılamadı")
        }

        var callOk = false
        var callTarget: String? = null
        for (contact in contacts.filter { it.callEnabled }) {
            callTarget = contact.phoneNumber
            callOk = CallPlacer(this).call(contact.phoneNumber)
            DeliveryLogStore.add(this, "ARAMA QA TEST", mask(contact.phoneNumber), callOk, if (callOk) "arama başlatıldı" else "başlatılamadı; sonraki kişi deneniyor")
            if (callOk) break
        }
        AlarmTimelineStore.add(this, "QA İLETİŞİM TESTİ", "SMS=${if (smsOk) "başlatıldı" else "başarısız/yok"}; Arama=${if (callOk) "başlatıldı ${callTarget?.let(::mask).orEmpty()}" else "başarısız/yok"}")
        output.append("\nGerçek test: SMS=${if (smsOk) "başlatıldı" else "başarısız/yok"}, arama=${if (callOk) "başlatıldı" else "başarısız/yok"}")
    }

    private fun formatAge(timestampMs: Long): String {
        val ageSec = ((System.currentTimeMillis() - timestampMs).coerceAtLeast(0L) / 1000L)
        val clock = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR")).format(Date(timestampMs))
        return when {
            ageSec < 60 -> "$clock • ${ageSec} sn önce"
            ageSec < 3600 -> "$clock • ${ageSec / 60} dk önce"
            else -> "$clock • ${ageSec / 3600} sa önce"
        }
    }

    private fun status(label: String, ok: Boolean) = (if (ok) "✓ " else "✗ ") + label
    private fun info(label: String, value: String) = "• $label: $value"
    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun mask(number: String) = if (number.length <= 4) "****" else "***${number.takeLast(4)}"
}
