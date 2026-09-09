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
            text = "CSV yalnız ölçüm geçmişini; JSON ise ayarlar, kişiler, ölçümler, alarm timeline ve teslimat kayıtlarını içerir. JSON dosyasında telefon numaraları açık metin olarak yer alır; güvenli yerde sakla."
            setPadding(0, 16, 0, 16)
        })
        root.addView(Button(this).apply {
            text = "Ölçüm geçmişini CSV dışa aktar"
            setOnClickListener { createDocument("text/csv", "SKHealthGuardian_measurements.csv", "csv") }
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
        val content = if (pending == "csv") buildCsv() else buildJson().toString(2)
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
        }.onSuccess {
            Toast.makeText(this, "Dışa aktarma tamamlandı", Toast.LENGTH_LONG).show()
            AlarmTimelineStore.add(this, "DIŞA AKTARMA", if (pending == "csv") "Ölçüm CSV oluşturuldu" else "Tam JSON yedeği oluşturuldu")
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

        return JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAtMs", System.currentTimeMillis())
            .put("settings", settings)
            .put("contacts", contacts)
            .put("readings", readings)
            .put("alarmTimeline", AlarmTimelineStore.formatted(this, 500))
            .put("deliveryLog", DeliveryLogStore.formatted(this))
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    companion object {
        private const val REQ_CREATE = 301
    }
}
