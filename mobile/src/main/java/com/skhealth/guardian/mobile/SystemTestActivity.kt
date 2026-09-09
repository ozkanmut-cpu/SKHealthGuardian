package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Wearable

class SystemTestActivity : Activity() {
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply { text = "Tam sistem testi"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Ön kontrol; izinleri, kişi yapılandırmasını ve saat bağlantısını kontrol eder. Saat bağlıysa config ACK, self-test ve manuel ölçüm komutları gönderilir."
            setPadding(0, 16, 0, 16)
        })
        output = TextView(this).apply { textSize = 16f }
        root.addView(output)
        root.addView(Button(this).apply {
            text = "Ön kontrolü çalıştır"
            setOnClickListener { runPreflight() }
        })
        root.addView(Button(this).apply {
            text = "Gerçek SMS + arama testi"
            setOnClickListener { confirmRealCommunicationTest() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        runPreflight()
    }

    private fun runPreflight() {
        val contacts = ContactStore.contacts(this)
        val smsContact = contacts.any { it.smsEnabled }
        val callContact = contacts.any { it.callEnabled }
        val cfg = AppSettings.load(this)
        val lines = mutableListOf<String>()
        lines += status("SMS izni", has(Manifest.permission.SEND_SMS))
        lines += status("Arama izni", has(Manifest.permission.CALL_PHONE))
        lines += status("Bildirim izni", Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS))
        lines += status("Bluetooth izni", Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT))
        lines += status("SMS kişisi", smsContact)
        lines += status("Arama kişisi", callContact)
        output.text = lines.joinToString("\n") + "\nSaat bağlantısı kontrol ediliyor…"

        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) return
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                output.append("\n✗ Saat bağlı değil")
                AlarmTimelineStore.add(this, "SİSTEM TESTİ", "Ön kontrol tamamlandı; saat bağlı değil")
                return@addOnSuccessListener
            }
            output.append("\n✓ Saat bağlı (${nodes.size})")
            WatchCommandSender(this).sendConfig(cfg)
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, "/health/selftest", "full-test".toByteArray())
            }
            WatchCommandSender(this).requestMeasurement()
            output.append("\n✓ Config ACK isteği gönderildi\n✓ Saat self-test gönderildi\n✓ Manuel ölçüm komutu gönderildi")
            AlarmTimelineStore.add(this, "SİSTEM TESTİ", "Config + self-test + manuel ölçüm komutları saate gönderildi")
        }.addOnFailureListener {
            output.append("\n✗ Saat bağlantısı okunamadı: ${it.javaClass.simpleName}")
        }
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
            SmsSender(this).send(it.phoneNumber, "SK Health Guardian TAM SİSTEM TESTİ - bu gerçek bir test mesajıdır.")
        } ?: false
        if (smsTarget != null) {
            DeliveryLogStore.add(this, "SMS TAM TEST", mask(smsTarget.phoneNumber), smsOk, if (smsOk) "modem kuyruğuna alındı" else "başlatılamadı")
        }

        var callOk = false
        var callTarget: String? = null
        for (contact in contacts.filter { it.callEnabled }) {
            callTarget = contact.phoneNumber
            callOk = CallPlacer(this).call(contact.phoneNumber)
            DeliveryLogStore.add(this, "ARAMA TAM TEST", mask(contact.phoneNumber), callOk, if (callOk) "arama başlatıldı" else "başlatılamadı; sonraki kişi deneniyor")
            if (callOk) break
        }
        AlarmTimelineStore.add(
            this,
            "TAM İLETİŞİM TESTİ",
            "SMS=${if (smsOk) "başlatıldı" else "başarısız/yok"}; Arama=${if (callOk) "başlatıldı ${callTarget?.let(::mask).orEmpty()}" else "başarısız/yok"}"
        )
        output.append("\n\nGerçek test: SMS=${if (smsOk) "başlatıldı" else "başarısız/yok"}, arama=${if (callOk) "başlatıldı" else "başarısız/yok"}")
    }

    private fun status(label: String, ok: Boolean) = (if (ok) "✓ " else "✗ ") + label
    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun mask(number: String) = if (number.length <= 4) "****" else "***${number.takeLast(4)}"
}
