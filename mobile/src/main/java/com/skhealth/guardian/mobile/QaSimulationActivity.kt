package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.skhealth.guardian.shared.QaScenarioRunner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QaSimulationActivity : Activity() {
    private lateinit var summary: TextView
    private lateinit var resultsRoot: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiStyle.applyBars(this)
        val root = UiStyle.page(this)
        root.addView(UiStyle.detailHeader(this, "QA simülasyon modu", "Alarm motorunu ve gerçek Android veri katmanını güvenli stres testleriyle doğrular."))

        val info = UiStyle.card(this)
        info.addView(UiStyle.text(this, "Güvenli test", 18f, UiStyle.TEXT, true))
        info.addView(UiStyle.text(this, "Otomatik testler gerçek üretim sınıflarını çalıştırır ancak SMS, arama, BLE komutu veya uzak alarm göndermez. Android stres testleri mevcut geçmiş ve timeline verisini geçici olarak yedekleyip test sonunda geri yükler.", 14f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@QaSimulationActivity, 8), 0, 0) })
        root.addView(info)

        val summaryCard = UiStyle.card(this)
        summary = UiStyle.text(this, "Henüz çalıştırılmadı", 20f, UiStyle.BLUE, true)
        summaryCard.addView(summary)
        root.addView(summaryCard, UiStyle.sectionParams(this))

        resultsRoot = UiStyle.card(this)
        resultsRoot.addView(UiStyle.text(this, "Sonuçlar", 18f, UiStyle.TEXT, true))
        root.addView(resultsRoot, UiStyle.sectionParams(this))

        root.addView(UiStyle.button(this, "8 güvenli alarm senaryosunu çalıştır").apply { setOnClickListener { runAll() } })
        root.addView(UiStyle.button(this, "Android stres testi • standart", false).apply { setOnClickListener { runAndroidStress(false) } })
        root.addView(UiStyle.button(this, "Android stres testi • AĞIR", false).apply { setOnClickListener { runAndroidStress(true) } })
        root.addView(UiStyle.button(this, "Örnek alarm ekranını aç", false).apply { setOnClickListener { previewAlarm() } })
        root.addView(UiStyle.button(this, "QA ön kontrole dön", false).apply { setOnClickListener { finish() } })

        setContentView(ScrollView(this).apply { setBackgroundColor(UiStyle.BG); addView(root) })
        runAll()
    }

    private fun clearResults() {
        while (resultsRoot.childCount > 1) resultsRoot.removeViewAt(1)
    }

    private fun runAll() {
        val results = QaScenarioRunner.runAll(AppSettings.load(this))
        clearResults()
        results.forEachIndexed { index, result ->
            if (index > 0) resultsRoot.addView(UiStyle.divider(this))
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, UiStyle.dp(this@QaSimulationActivity, 12), 0, UiStyle.dp(this@QaSimulationActivity, 12))
            }
            line.addView(UiStyle.text(this, (if (result.passed) "✓ " else "✗ ") + result.title, 15.5f, if (result.passed) UiStyle.GREEN else UiStyle.RED, true))
            line.addView(UiStyle.text(this, "Beklenen: ${result.expected}", 13f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@QaSimulationActivity, 5), 0, 0) })
            line.addView(UiStyle.text(this, "Gerçek: ${result.actual}", 13f, UiStyle.TEXT).apply { setPadding(0, UiStyle.dp(this@QaSimulationActivity, 3), 0, 0) })
            line.addView(UiStyle.text(this, result.detail, 12.5f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@QaSimulationActivity, 3), 0, 0) })
            resultsRoot.addView(line)
        }
        val passed = results.count { it.passed }
        val allOk = passed == results.size
        summary.setTextColor(if (allOk) UiStyle.GREEN else UiStyle.AMBER)
        summary.text = if (allOk) "✓ TÜM SENARYOLAR BAŞARILI\n$passed/${results.size}" else "⚠ QA HATASI VAR\n$passed/${results.size} başarılı"
        val failed = results.filterNot { it.passed }.joinToString { it.id }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("tr", "TR")).format(Date())
        AlarmTimelineStore.add(this, "QA SİMÜLASYON", "$stamp • $passed/${results.size} başarılı" + if (failed.isBlank()) "" else " • başarısız=$failed")
    }

    private fun runAndroidStress(heavy: Boolean) {
        summary.setTextColor(UiStyle.BLUE)
        summary.text = if (heavy) "AĞIR ANDROID STRES TESTİ ÇALIŞIYOR…" else "ANDROID STRES TESTİ ÇALIŞIYOR…"
        clearResults()
        resultsRoot.addView(UiStyle.text(this, "Test gerçek SharedPreferences/HistoryStore/TimelineStore üzerinde çalışıyor. Uygulama arayüzü donmasın diye arka planda yürütülüyor.", 13f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@QaSimulationActivity, 12), 0, 0) })

        Thread {
            val report = AndroidStressRunner.run(applicationContext, heavy)
            runOnUiThread {
                clearResults()
                report.results.forEachIndexed { index, result ->
                    if (index > 0) resultsRoot.addView(UiStyle.divider(this))
                    val line = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, UiStyle.dp(this@QaSimulationActivity, 12), 0, UiStyle.dp(this@QaSimulationActivity, 12))
                    }
                    line.addView(UiStyle.text(this, (if (result.passed) "✓ " else "✗ ") + result.title, 15.5f, if (result.passed) UiStyle.GREEN else UiStyle.RED, true))
                    line.addView(UiStyle.text(this, "${result.operations} işlem • ${result.elapsedMs} ms", 13f, UiStyle.TEXT).apply { setPadding(0, UiStyle.dp(this@QaSimulationActivity, 5), 0, 0) })
                    line.addView(UiStyle.text(this, result.detail, 12.5f, UiStyle.MUTED).apply { setPadding(0, UiStyle.dp(this@QaSimulationActivity, 3), 0, 0) })
                    resultsRoot.addView(line)
                }
                summary.setTextColor(if (report.passed) UiStyle.GREEN else UiStyle.RED)
                summary.text = (if (report.passed) "✓ ANDROID STRES TESTİ BAŞARILI" else "✗ ANDROID STRES TESTİ HATALI") +
                    "\n${report.totalOperations} işlem • ${report.totalElapsedMs} ms"
                AlarmTimelineStore.add(this, "QA ANDROID STRES", "heavy=$heavy • passed=${report.passed} • ops=${report.totalOperations} • ms=${report.totalElapsedMs}")
            }
        }.start()
    }

    private fun previewAlarm() {
        startActivity(Intent(this, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_REASON, "QA SİMÜLASYONU — gerçek sağlık alarmı değildir")
            putExtra(AlarmActivity.EXTRA_SPO2, 79)
            putExtra(AlarmActivity.EXTRA_HR, 131)
            putExtra(AlarmActivity.EXTRA_REMOTE_STATUS, "TEST MODU • SMS/arama gönderilmedi")
            putExtra(AlarmActivity.EXTRA_ALERT_TS, System.currentTimeMillis())
        })
    }
}
