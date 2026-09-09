package com.skhealth.guardian.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.skhealth.guardian.shared.AlarmConfig
import com.skhealth.guardian.shared.HealthReading
import org.json.JSONArray
import org.json.JSONObject

class ExportActivity : Activity() {
    private var pending: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 40) }
        root.addView(TextView(this).apply { text = "Veri yönetimi"; textSize = 27f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply {
            text = "Ölçümlerini dışa aktarabilir veya tam uygulama yedeği oluşturabilirsin. JSON yedeği kişiler ve telefon numaralarını açık metin içerir; güvenli yerde sakla."
            textSize = 15f; setPadding(0, 10, 0, 20)
        })

        root.addView(TextView(this).apply { text = "Dışa aktar"; textSize = 20f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 6, 0, 8) })
        root.addView(Button(this).apply { text = "Ölçüm geçmişi (CSV)"; setOnClickListener { createDocument("text/csv", "SKHealthGuardian_measurements.csv", "csv") } })
        root.addView(Button(this).apply { text = "Watch ↔ PC-60FW doğrulama (CSV)"; setOnClickListener { createDocument("text/csv", "SKHealthGuardian_watch_pc60_reliability.csv", "reliability_csv") } })
        root.addView(Button(this).apply { text = "Tam uygulama yedeği (JSON)"; setOnClickListener { createDocument("application/json", "SKHealthGuardian_backup.json", "json") } })

        root.addView(TextView(this).apply { text = "Geri yükle"; textSize = 20f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 24, 0, 8) })
        root.addView(TextView(this).apply {
            text = "Dikkat: geri yükleme, yedekte bulunan ayarlar, kişiler ve geçmiş verilerle mevcut verileri değiştirir. Dosya önce doğrulanır ve işlem başlamadan tekrar onay istenir."
            textSize = 15f; setPadding(0, 0, 0, 10)
        })
        root.addView(Button(this).apply { text = "JSON YEDEĞİNDEN GERİ YÜKLE"; setOnClickListener { chooseBackup() } })
        root.addView(TextView(this).apply {
            text = "Desteklenen formatlar: schema v3 (güncel), v2 ve v1. Eski yedeklerde bulunmayan yapılandırılmış log alanları mevcut logları silmez."
            textSize = 14f; setPadding(0, 16, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
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

    private fun chooseBackup() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }, REQ_RESTORE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == REQ_RESTORE) {
            val parsed = runCatching {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Dosya okunamadı")
                JSONObject(text)
            }.getOrElse {
                Toast.makeText(this, "Yedek okunamadı: ${it.message ?: it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                return
            }
            confirmRestore(parsed)
            return
        }
        if (requestCode != REQ_CREATE) return
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

    private fun confirmRestore(root: JSONObject) {
        val schema = root.optInt("schemaVersion", -1)
        if (schema !in 1..CURRENT_SCHEMA) {
            Toast.makeText(this, "Desteklenmeyen yedek şeması: $schema", Toast.LENGTH_LONG).show()
            return
        }
        if (!root.has("settings") || !root.has("contacts") || !root.has("readings")) {
            Toast.makeText(this, "Geçersiz SK Health Guardian yedeği", Toast.LENGTH_LONG).show()
            return
        }
        val contacts = root.optJSONArray("contacts")?.length() ?: 0
        val readings = root.optJSONArray("readings")?.length() ?: 0
        val matches = root.optJSONArray("reliabilityMatches")?.length() ?: 0
        AlertDialog.Builder(this)
            .setTitle("JSON yedeğini geri yükle")
            .setMessage("Schema v$schema\nKişi: $contacts\nÖlçüm: $readings\nWatch↔PC60 eşleşmesi: $matches\n\nMevcut veriler yedekte bulunan içerikle değiştirilecek. Devam edilsin mi?")
            .setNegativeButton("İptal", null)
            .setPositiveButton("Geri yükle") { _, _ -> restoreJson(root, schema) }
            .show()
    }

    private fun restoreJson(root: JSONObject, schema: Int) {
        runCatching {
            val current = AppSettings.load(this)
            val s = root.getJSONObject("settings")
            val config = AlarmConfig(
                spo2CriticalImmediate = s.optInt("spo2CriticalImmediate", current.spo2CriticalImmediate),
                spo2LowThreshold = s.optInt("spo2LowThreshold", current.spo2LowThreshold),
                spo2ConfirmCount = s.optInt("spo2ConfirmCount", current.spo2ConfirmCount),
                heartRateHighThreshold = s.optInt("heartRateHighThreshold", current.heartRateHighThreshold),
                heartRateHighConfirmCount = s.optInt("heartRateHighConfirmCount", current.heartRateHighConfirmCount),
                heartRateLowEnabled = if (s.has("heartRateLowEnabled")) s.optBoolean("heartRateLowEnabled") else current.heartRateLowEnabled,
                heartRateLowThreshold = s.optInt("heartRateLowThreshold", current.heartRateLowThreshold),
                heartRateLowConfirmCount = s.optInt("heartRateLowConfirmCount", current.heartRateLowConfirmCount),
                staleDataMs = s.optLong("staleDataMs", current.staleDataMs)
            )
            require(config.spo2CriticalImmediate in 50..99 && config.spo2LowThreshold in 51..100 && config.spo2CriticalImmediate < config.spo2LowThreshold) { "SpO₂ ayarları geçersiz" }
            require(config.heartRateHighThreshold in 60..240) { "Yüksek nabız ayarı geçersiz" }
            require(config.heartRateLowThreshold in 20..120) { "Düşük nabız ayarı geçersiz" }
            require(config.staleDataMs in 60_000L..(120L * 60_000L)) { "Veri tazeliği ayarı geçersiz" }

            val contacts = mutableListOf<EmergencyContact>()
            root.getJSONArray("contacts").forEachObject { o ->
                val phone = o.optString("phoneNumber").trim()
                if (phone.isNotBlank()) contacts += EmergencyContact(
                    name = o.optString("name", "Kişi").ifBlank { "Kişi" },
                    phoneNumber = phone,
                    smsEnabled = o.optBoolean("smsEnabled", true),
                    callEnabled = o.optBoolean("callEnabled", false)
                )
            }

            val readings = mutableListOf<HealthReading>()
            root.getJSONArray("readings").forEachObject { o ->
                val ts = o.optLong("timestampMs", 0L)
                if (ts > 0L) readings += HealthReading(
                    id = o.optString("id", "restore-$ts"),
                    timestampMs = ts,
                    spo2 = o.optNullableInt("spo2"),
                    heartRate = o.optNullableInt("heartRate"),
                    valid = o.optBoolean("valid", false),
                    source = o.optString("source", "RESTORE")
                )
            }

            val matches = mutableListOf<SpO2ReliabilityStore.Match>()
            root.optJSONArray("reliabilityMatches")?.forEachObject { o ->
                val ts = o.optLong("timestampMs", 0L)
                val watch = o.optInt("watchSpO2", -1)
                val pc = o.optInt("pc60SpO2", -1)
                if (ts > 0L && watch in 1..100 && pc in 1..100) matches += SpO2ReliabilityStore.Match(ts, watch, pc, watch - pc)
            }

            val timeline = mutableListOf<AlarmTimelineStore.Event>()
            val deliveries = mutableListOf<DeliveryLogStore.Entry>()
            if (schema >= 3) {
                root.optJSONArray("alarmTimelineEvents")?.forEachObject { o ->
                    val ts = o.optLong("timestampMs", 0L)
                    if (ts > 0L) timeline += AlarmTimelineStore.Event(ts, o.optString("category"), o.optString("detail"))
                }
                root.optJSONArray("deliveryLogEntries")?.forEachObject { o ->
                    val ts = o.optLong("timestampMs", 0L)
                    if (ts > 0L) deliveries += DeliveryLogStore.Entry(
                        timestampMs = ts,
                        channel = o.optString("channel"),
                        target = o.optString("target"),
                        ok = o.optBoolean("ok", false),
                        detail = o.optString("detail")
                    )
                }
            }

            AppSettings.save(this, config)
            AppSettings.setEscalationMinutes(this, s.optInt("escalationMinutes", AppSettings.escalationMinutes(this)))
            AppSettings.setWatchMeasurementMinutes(this, s.optInt("watchMeasurementMinutes", AppSettings.watchMeasurementMinutes(this)))
            AppSettings.setWatchConfirmMinutes(this, s.optInt("watchConfirmMinutes", AppSettings.watchConfirmMinutes(this)))
            AppSettings.setWatchRetry1Seconds(this, s.optInt("watchRetry1Seconds", AppSettings.watchRetry1Seconds(this)))
            AppSettings.setWatchRetry2Seconds(this, s.optInt("watchRetry2Seconds", AppSettings.watchRetry2Seconds(this)))
            AppSettings.setPc60AlarmThreshold(this, s.optInt("pc60AlarmThreshold", AppSettings.pc60AlarmThreshold(this)))
            AppSettings.setPc60ConfirmMinutes(this, s.optInt("pc60ConfirmMinutes", AppSettings.pc60ConfirmMinutes(this)))
            AppSettings.setPc60RecoveryThreshold(this, s.optInt("pc60RecoveryThreshold", AppSettings.pc60RecoveryThreshold(this)))
            AppSettings.setPc60StableSeconds(this, s.optInt("pc60StableSeconds", AppSettings.pc60StableSeconds(this)))
            ContactStore.save(this, contacts)
            HistoryStore.replace(this, readings)
            SpO2ReliabilityStore.restore(this, matches)
            if (schema >= 3) {
                AlarmTimelineStore.replace(this, timeline)
                DeliveryLogStore.replace(this, deliveries)
            }
            AlarmTimelineStore.add(this, "GERİ YÜKLEME", "JSON yedeği schema v$schema geri yüklendi; kişi=${contacts.size}, ölçüm=${readings.size}, eşleşme=${matches.size}")
        }.onSuccess {
            Toast.makeText(this, "Yedek başarıyla geri yüklendi", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Geri yükleme başarısız: ${it.message ?: it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
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
            .put("spo2ConfirmCount", cfg.spo2ConfirmCount)
            .put("heartRateHighThreshold", cfg.heartRateHighThreshold)
            .put("heartRateHighConfirmCount", cfg.heartRateHighConfirmCount)
            .put("heartRateLowEnabled", cfg.heartRateLowEnabled)
            .put("heartRateLowThreshold", cfg.heartRateLowThreshold)
            .put("heartRateLowConfirmCount", cfg.heartRateLowConfirmCount)
            .put("staleDataMs", cfg.staleDataMs)
            .put("escalationMinutes", AppSettings.escalationMinutes(this))
            .put("watchMeasurementMinutes", AppSettings.watchMeasurementMinutes(this))
            .put("watchConfirmMinutes", AppSettings.watchConfirmMinutes(this))
            .put("watchRetry1Seconds", AppSettings.watchRetry1Seconds(this))
            .put("watchRetry2Seconds", AppSettings.watchRetry2Seconds(this))
            .put("pc60AlarmThreshold", AppSettings.pc60AlarmThreshold(this))
            .put("pc60ConfirmMinutes", AppSettings.pc60ConfirmMinutes(this))
            .put("pc60RecoveryThreshold", AppSettings.pc60RecoveryThreshold(this))
            .put("pc60StableSeconds", AppSettings.pc60StableSeconds(this))

        val contacts = JSONArray().also { out ->
            ContactStore.contacts(this).forEach { c -> out.put(JSONObject()
                .put("name", c.name).put("phoneNumber", c.phoneNumber)
                .put("smsEnabled", c.smsEnabled).put("callEnabled", c.callEnabled)) }
        }
        val readings = JSONArray().also { out ->
            HistoryStore.recent(this, 1000).forEach { r -> out.put(JSONObject()
                .put("id", r.id).put("timestampMs", r.timestampMs)
                .put("spo2", r.spo2 ?: JSONObject.NULL).put("heartRate", r.heartRate ?: JSONObject.NULL)
                .put("valid", r.valid).put("source", r.source)) }
        }
        val reliability = SpO2ReliabilityStore.summary(this)
        val reliabilitySummary = JSONObject()
            .put("label", reliability.label).put("count", reliability.count)
            .put("meanAbsoluteError", reliability.meanAbsoluteError ?: JSONObject.NULL)
            .put("meanBias", reliability.meanBias ?: JSONObject.NULL)
            .put("lastWatch", reliability.lastWatch ?: JSONObject.NULL)
            .put("lastPc60", reliability.lastPc60 ?: JSONObject.NULL)
            .put("lastDiff", reliability.lastDiff ?: JSONObject.NULL)
            .put("lastMatchAt", reliability.lastMatchAt ?: JSONObject.NULL)
        val reliabilityMatches = JSONArray().also { out ->
            SpO2ReliabilityStore.recentMatches(this).forEach { m -> out.put(JSONObject()
                .put("timestampMs", m.timestampMs).put("watchSpO2", m.watch)
                .put("pc60SpO2", m.pc60).put("diffWatchMinusPc60", m.diff)) }
        }
        val timeline = JSONArray().also { out ->
            AlarmTimelineStore.events(this, 500).forEach { e -> out.put(JSONObject()
                .put("timestampMs", e.timestampMs).put("category", e.category).put("detail", e.detail)) }
        }
        val deliveries = JSONArray().also { out ->
            DeliveryLogStore.entries(this, 300).forEach { e -> out.put(JSONObject()
                .put("timestampMs", e.timestampMs).put("channel", e.channel).put("target", e.target)
                .put("ok", e.ok).put("detail", e.detail)) }
        }

        return JSONObject()
            .put("schemaVersion", CURRENT_SCHEMA)
            .put("exportedAtMs", System.currentTimeMillis())
            .put("settings", settings)
            .put("contacts", contacts)
            .put("readings", readings)
            .put("alarmTimelineEvents", timeline)
            .put("deliveryLogEntries", deliveries)
            .put("reliabilitySummary", reliabilitySummary)
            .put("reliabilityMatches", reliabilityMatches)
    }

    private fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) optJSONObject(i)?.let(block)
    }

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it >= 0 }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    companion object {
        private const val REQ_CREATE = 301
        private const val REQ_RESTORE = 302
        private const val CURRENT_SCHEMA = 3
    }
}
