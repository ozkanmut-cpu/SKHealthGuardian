package com.skhealth.guardian.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    private lateinit var summaryIcon: SkIconView
    private var preflightGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)
        val root = UiStyle.page(this)
        root.addView(UiStyle.detailHeader(this, "QA ön kontrol", "Telefon, Galaxy Watch, acil kişiler ve PC-60FW zincirini doğrular. Gerçek SMS/arama yalnızca sen başlatırsan çalışır."))

        val summaryCard = UiStyle.card(this)
        val summaryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        summaryIcon = UiStyle.icon(this, SkIcon.SYNC, 30, UiStyle.BLUE, null)
        summaryRow.addView(summaryIcon, LinearLayout.LayoutParams(UiStyle.dp(this, 36), UiStyle.dp(this, 36)))
        summary = UiStyle.text(this, "Kontroller çalıştırılıyor…", 20f, UiStyle.BLUE, true).apply { setPadding(UiStyle.dp(this@SystemTestActivity, 10), 0, 0, 0) }
        summaryRow.addView(summary, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        summaryCard.addView(summaryRow)
        root.addView(summaryCard)

        val detailsCard = UiStyle.card(this)
        detailsCard.addView(UiStyle.iconLabel(this, SkIcon.EVENTS, "Kontrol ayrıntıları", UiStyle.BLUE, UiStyle.TEXT, 22, 18f, true))
        output = UiStyle.text(this, "", 15f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@SystemTestActivity, 10), 0, 0) }
        detailsCard.addView(output)
        root.addView(detailsCard, UiStyle.sectionParams(this))

        root.addView(UiStyle.iconButton(this, SkIcon.REFRESH, "Kontrolleri yeniden çalıştır", true).apply { setOnClickListener { runPreflight() } })
        root.addView(UiStyle.iconButton(this, SkIcon.WATCH, "Galaxy Watch self-test + ölçüm").apply { setOnClickListener { runWatchTest() } })
        root.addView(UiStyle.iconButton(this, SkIcon.OXIMETER, "PC-60FW durumunu aç").apply { setOnClickListener { startActivity(Intent(this@SystemTestActivity, Pc60Activity::class.java)) } })
        root.addView(UiStyle.iconButton(this, SkIcon.STATUS_ALERT, "Alarm ekranını güvenli test et", false, UiStyle.AMBER).apply { setOnClickListener { previewAlarmScreen() } })
        root.addView(UiStyle.iconButton(this, SkIcon.CONTACTS, "Gerçek SMS + arama testi", false, UiStyle.RED).apply { setOnClickListener { confirmRealCommunicationTest() } })
        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
        runPreflight()
    }

    override fun onResume() { super.onResume(); if (::output.isInitialized) runPreflight() }

    private fun runPreflight() {
        val generation = ++preflightGeneration
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
        required += "Arama durum izni" to has(Manifest.permission.READ_PHONE_STATE)
        required += "Bildirim izni" to (Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS))
        required += "Bluetooth bağlantı izni" to (Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT))
        required += "Bluetooth tarama izni" to (Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_SCAN))
        required += "SMS gönderilecek kişi" to smsContact
        required += "Aranacak kişi" to callContact

        val baseLines = mutableListOf<String>()
        required.forEach { (label, ok) -> baseLines += status(label, ok) }
        baseLines += if (!pc60Configured) "○ PC-60FW isteğe bağlı • henüz yapılandırılmamış" else status("PC-60FW geçerli veri akışı", pc60Fresh) + " • ${pc60.state}"
        if (pc60Configured && pc60.lastPacketAt > 0) baseLines += info("PC-60FW son paket", formatAge(pc60.lastPacketAt))
        val source = SourcePriorityCoordinator.activeSourceLabel(this)
        baseLines += info("Alarm kaynağı", if (source == "Aktif kaynak yok") "Yok" else source)
        output.text = baseLines.joinToString("\n") + "\n• Galaxy Watch kontrol ediliyor…"
        val localOk = required.all { it.second }

        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            if (generation != preflightGeneration) return
            output.text = (baseLines + "✗ Galaxy Watch: Bluetooth bağlantı izni yok").joinToString("\n")
            showSummary(false, listOf("Bluetooth bağlantı izni eksik"), required.count { it.second }, required.size + 1)
            return
        }

        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (generation != preflightGeneration) return@addOnSuccessListener
            val watchConnected = nodes.isNotEmpty()
            val finalLines = baseLines.toMutableList()
            finalLines += if (watchConnected) {
                val dataText = if (lastReading == 0L) "henüz veri yok" else formatAge(lastReading) + if (watchDataFresh) " • taze" else " • eski"
                "✓ Galaxy Watch bağlı (${nodes.size}) • $dataText"
            } else "✗ Galaxy Watch bağlı değil"
            output.text = finalLines.joinToString("\n")
            val missing = required.filterNot { it.second }.map { it.first }.toMutableList()
            if (!watchConnected) missing += "Galaxy Watch bağlantısı"
            if (pc60Configured && !pc60Fresh) missing += "PC-60FW geçerli/taze veri akışı"
            val total = required.size + 1 + if (pc60Configured) 1 else 0
            val passed = total - missing.size
            showSummary(localOk && watchConnected && (!pc60Configured || pc60Fresh), missing, passed, total)
            AlarmTimelineStore.add(this, "QA ÖN KONTROL", if (missing.isEmpty()) "Sistem hazır" else "Eksikler: ${missing.joinToString()}")
        }.addOnFailureListener {
            if (generation != preflightGeneration) return@addOnFailureListener
            output.text = (baseLines + "✗ Galaxy Watch bağlantısı okunamadı: ${it.javaClass.simpleName}").joinToString("\n")
            showSummary(false, listOf("Saat bağlantısı okunamadı"), required.count { it.second }, required.size + 1)
        }
    }

    private fun showSummary(ready: Boolean, missing: List<String>, passed: Int, total: Int) {
        summary.setTextColor(if (ready) UiStyle.GREEN else UiStyle.AMBER)
        summaryIcon.icon = if (ready) SkIcon.STATUS_OK else SkIcon.STATUS_WARNING
        summaryIcon.tint = if (ready) UiStyle.GREEN else UiStyle.AMBER
        summaryIcon.invalidate()
        summary.text = if (ready) "SİSTEM HAZIR\n$passed/$total kontrol başarılı" else "EKSİKLER VAR\n$passed/$total kontrol başarılı" + if (missing.isEmpty()) "" else "\n${missing.joinToString("\n") { "• $it" }}"
    }

    private fun runWatchTest() {
        val cfg = AppSettings.load(this)
        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) { output.append("\n✗ Saat testi: Bluetooth bağlantı izni yok"); return }
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) { output.append("\n✗ Saat testi: saat bağlı değil"); return@addOnSuccessListener }
            WatchCommandSender(this).sendConfig(cfg)
            nodes.forEach { node -> Wearable.getMessageClient(this).sendMessage(node.id, "/health/selftest", "qa-wizard".toByteArray()) }
            WatchCommandSender(this).requestMeasurement()
            output.append("\n✓ Config gönderildi\n✓ Saat self-test gönderildi\n✓ Manuel ölçüm istendi")
            AlarmTimelineStore.add(this, "QA SAAT TESTİ", "Config + self-test + manuel ölçüm gönderildi")
        }.addOnFailureListener { output.append("\n✗ Saat testi başarısız: ${it.javaClass.simpleName}") }
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
        AlertDialog.Builder(this).setTitle("Gerçek iletişim testi").setMessage("Bu test gerçek SMS gönderir ve tanımlı arama kişisini gerçekten arar. Devam edilsin mi?").setNegativeButton("İptal", null).setPositiveButton("Testi başlat") { _, _ -> runRealCommunicationTest() }.show()
    }

    private fun runRealCommunicationTest() {
        val contacts = ContactStore.contacts(this)
        val smsTarget = contacts.firstOrNull { it.smsEnabled }
        val smsOk = smsTarget?.let { SmsSender(this).send(it.phoneNumber, "SK Health Guardian QA TESTİ - bu gerçek bir test mesajıdır.") } ?: false
        if (smsTarget != null) DeliveryLogStore.add(this, "SMS QA TEST", mask(smsTarget.phoneNumber), smsOk, if (smsOk) "modem kuyruğuna alındı" else "başlatılamadı")
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
        return when { ageSec < 60 -> "$clock • ${ageSec} sn önce"; ageSec < 3600 -> "$clock • ${ageSec / 60} dk önce"; else -> "$clock • ${ageSec / 3600} sa önce" }
    }

    private fun status(label: String, ok: Boolean) = (if (ok) "✓ " else "✗ ") + label
    private fun info(label: String, value: String) = "• $label: $value"
    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun mask(number: String) = if (number.length <= 4) "****" else "***${number.takeLast(4)}"
}
