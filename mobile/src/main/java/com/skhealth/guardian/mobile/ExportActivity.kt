package com.skhealth.guardian.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class ExportActivity : Activity() {
    private var pending: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply { text = "Yedekle / dışa aktar"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Ölçüm CSV'si ölçüm geçmişini, doğrulama CSV'si Watch↔PC-60FW eşleşmelerini; JSON ise ayarlar, kişiler, ölçümler, alarm timeline, teslimat kayıtları ve saat doğrulama verilerini içerir. JSON dosyasında telefon numaraları açık metin olarak yer alır; güvenli yerde sakla."
            setPadding(0, 16, 0, 16)
        })
        root.addView(Button(this).apply {
            text = "Ölçüm geçmişini CSV dışa aktar"
            setOnClickListener { createDocument("text/csv", "SKHealthGuardian_measurements.csv", "csv") }
        })
        root.addView(Button(this).apply {
            text = "Saat doğrulama verisini CSV dışa aktar"
            setOnClickListener { createDocument("text/csv", "SKHealthGuardian_watch_pc60_reliability.csv", "reliability_csv") }
        })
        root.addView(Button(this).apply {
            text = "Tam JSON yedeği oluştur"
            setOnClickListener { createDocument("application/json", "SKHealthGuardian_backup.json", "json") }
        })
        setContentView(root)
    }

    private fun createDocument(type: String, name: String, kind: String) {
        pending = kind
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type
            putExtra(Intent.EXTRA_TITLE, name)
        }
        startActivityForResult(intent, REQ_CREATE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CREATE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val content = when (pending) {
            "csv" -> buildCsv()
            "reliability_csv" -> buildReliabilityCsv()
            else -> buildJson().toString(2)
        }
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
        }.onSuccess {
            Toast.makeText(this, "Dışa aktarma tamamlandı", Toast.LENGTH_LONG).show()
            val detail = when (pending) {
                "csv" -> "Ölçüm CSV oluşturuldu"
                "reliability_csv" -> "Watch-PC60 doğrulama CSV oluşturuldu"
                else -> "Tam JSON yedeği oluşturuldu"
            }
            AlarmTimelineStore.add(this, "DIŞA AKTARMA", detail)
        }.onFailure {
            Toast.makeText(this, "Dışa aktarma başarısız: ${it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildCsv(): String {
        val rows = HistoryStore.recent(this, 1000)
        return buildString {
            appendLine("id,timestamp_ms,spo2,heart_rate,valid,source")
            rows.forEach { r ->
                append(csv(r.id)).append(',')
                append(r.timestampMs).append(',')
                append(r.spo2 ?: "").append(',')
                append(r.heartRate ?: "").append(',')
                append(r.valid).append(',')
                append(csv(r.source)).append('\n')
            }
        }
    }

    private fun buildReliabilityCsv(): String {
        val rows = SpO2ReliabilityStore.recentMatches(this)
        return buildString {
            appendLine("timestamp_ms,watch_spo2,pc60_spo2,diff_watch_minus_pc60")
            rows.forEach { m ->
                append(m.timestampMs).append(',')
                append(m.watch).append(',')
                append(m.pc60).append(',')
                append(m.diff).append('\n')
            }
        }
    }

    private fun buildJson(): JSONObject {
        val cfg = AppSettings.load(this)
        val settings = JSONObject()
            .put("spo2CriticalImmediate", cfg.spo2CriticalImmediate)
            .put("spo2LowThreshold", cfg.spo2LowThreshold)
            .put("heartRateHighThreshold", cfg.heartRateHighThreshold)
            .put("heartRateLowEnabled", cfg.heartRateLowEnabled)
            .put("heartRateLowThreshold", cfg.heartRateLowThreshold)
            .put("staleDataMs", cfg.staleDataMs)
            .put("escalationMinutes", AppSettings.escalationMinutes(this))

        val contacts = JSONArray()
        ContactStore.contacts(this).forEach { c ->
            contacts.put(JSONObject()
                .put("name", c.name)
                .put("phoneNumber", c.phoneNumber)
                .put("smsEnabled", c.smsEnabled)
                .put("callEnabled", c.callEnabled))
        }

        val readings = JSONArray()
        HistoryStore.recent(this, 1000).forEach { r ->
            readings.put(JSONObject()
                .put("id", r.id)
                .put("timestampMs", r.timestampMs)
                .put("spo2", r.spo2 ?: JSONObject.NULL)
                .put("heartRate", r.heartRate ?: JSONObject.NULL)
                .put("valid", r.valid)
                .put("source", r.source))
        }

        val reliability = SpO2ReliabilityStore.summary(this)
        val reliabilitySummary = JSONObject()
            .put("label", reliability.label)
            .put("count", reliability.count)
            .put("meanAbsoluteError", reliability.meanAbsoluteError ?: JSONObject.NULL)
            .put("meanBias", reliability.meanBias ?: JSONObject.NULL)
            .put("lastWatch", reliability.lastWatch ?: JSONObject.NULL)
            .put("lastPc60", reliability.lastPc60 ?: JSONObject.NULL)
            .put("lastDiff", reliability.lastDiff ?: JSONObject.NULL)
            .put("lastMatchAt", reliability.lastMatchAt ?: JSONObject.NULL)

        val reliabilityMatches = JSONArray()
        SpO2ReliabilityStore.recentMatches(this).forEach { m ->
            reliabilityMatches.put(JSONObject()
                .put("timestampMs", m.timestampMs)
                .put("watchSpO2", m.watch)
                .put("pc60SpO2", m.pc60)
                .put("diffWatchMinusPc60", m.diff))
        }

        return JSONObject()
            .put("schemaVersion", 2)
            .put("exportedAtMs", System.currentTimeMillis())
            .put("settings", settings)
            .put("contacts", contacts)
            .put("readings", readings)
            .put("alarmTimeline", AlarmTimelineStore.formatted(this, 500))
            .put("deliveryLog", DeliveryLogStore.formatted(this))
            .put("reliabilitySummary", reliabilitySummary)
            .put("reliabilityMatches", reliabilityMatches)
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    companion object {
        private const val REQ_CREATE = 301
    }
}
