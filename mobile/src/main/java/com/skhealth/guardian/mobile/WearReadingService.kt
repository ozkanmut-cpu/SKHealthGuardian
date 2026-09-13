package com.skhealth.guardian.mobile

import android.app.NotificationManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.skhealth.guardian.shared.AckReceiptPolicy
import com.skhealth.guardian.shared.AlarmEngine
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.AlertIdentity
import com.skhealth.guardian.shared.AlertType
import com.skhealth.guardian.shared.ExactAlertResourcePolicy
import com.skhealth.guardian.shared.HealthReading
import com.skhealth.guardian.shared.TechnicalAlertReplayPolicy
import com.skhealth.guardian.shared.TechnicalAlertWireCodec
import com.skhealth.guardian.shared.TechnicalConnectivityCoalescingPolicy
import com.skhealth.guardian.shared.WatchSpO2AlarmPolicy
import com.skhealth.guardian.shared.WatchSpO2AlarmSnapshot
import com.skhealth.guardian.shared.WatchSpO2Decision

class WearReadingService : WearableListenerService() {
    private var engine: AlarmEngine? = null
    private var activeConfig: com.skhealth.guardian.shared.AlarmConfig? = null
    private var activeStateSignature: String? = null
    private var lastSpo2Pc60: Boolean? = null
    private var lastHrPc60: Boolean? = null
    private val watchSpO2Policy = WatchSpO2AlarmPolicy()

    override fun onCreate() {
        super.onCreate()
        restoreWatchSpO2Policy()
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/health/status") {
            WatchStatusStore.mark(this, String(event.data))
            return
        }
        if (event.path == "/health/heartbeat") {
            val p = String(event.data).split('|')
            val battery = p.getOrNull(1)?.toIntOrNull() ?: -1
            WatchHeartbeatStore.mark(this, battery)
            BatteryAlertHelper.update(this, "watch", "Saat", battery)
            return
        }
        if (event.path == "/health/alarm_ack") {
            val raw = String(event.data)
            val receiptPayload = AckReceiptPolicy.receiptForAck(raw) ?: return
            if (raw.startsWith("v2|")) {
                val p = raw.split('|', limit = 5)
                val alertId = p.getOrNull(2).orEmpty()
                val alertTs = p.getOrNull(3)?.toLongOrNull() ?: 0L
                val reason = p.getOrNull(4).orEmpty().ifBlank { "Saat üzerinden alarm susturuldu" }
                val exactTag = ExactAlertResourcePolicy.notificationTag(alertId) ?: return

                if (AlertAcknowledgementStore.isAcknowledged(this, exactTag)) {
                    sendAckReceipt(event.sourceNodeId, receiptPayload)
                    return
                }

                val persisted = AlertAcknowledgementStore.acknowledge(this, exactTag)
                if (!persisted) {
                    AlarmTimelineStore.add(this, "ACK KAYIT HATASI", "Saatten gelen alarm susturma kalıcı kaydedilemedi; escalation aktif tutuldu", System.currentTimeMillis())
                    return
                }
                sendAckReceipt(event.sourceNodeId, receiptPayload)
                EscalationScheduler.cancel(this, exactTag, alertTs)
                getSystemService(NotificationManager::class.java).cancel(exactTag, AlarmActivity.CRITICAL_NOTIFICATION_ID)
                AlarmTimelineStore.add(this, "ALARM SAATTEN SUSTURULDU", reason, if (alertTs > 0L) alertTs else System.currentTimeMillis())
                return
            }

            val p = raw.split('|', limit = 2)
            val alertTs = p.getOrNull(0)?.toLongOrNull() ?: return
            val reason = p.getOrNull(1).orEmpty().ifBlank { "Saat üzerinden alarm susturuldu" }

            if (AlertAcknowledgementStore.lastAcknowledgedAt(this) >= alertTs) {
                sendAckReceipt(event.sourceNodeId, receiptPayload)
                return
            }

            val persisted = AlertAcknowledgementStore.acknowledge(this, alertTs)
            if (!persisted) {
                AlarmTimelineStore.add(this, "ACK KAYIT HATASI", "Saatten gelen legacy alarm susturma kalıcı kaydedilemedi; escalation aktif tutuldu", System.currentTimeMillis())
                return
            }
            sendAckReceipt(event.sourceNodeId, receiptPayload)
            EscalationScheduler.cancel(this, alertTs)
            getSystemService(NotificationManager::class.java).cancel(AlarmActivity.CRITICAL_NOTIFICATION_ID)
            AlarmTimelineStore.add(this, "ALARM SAATTEN SUSTURULDU", reason, alertTs)
            return
        }
        if (event.path == "/health/alert") {
            val alert = TechnicalAlertWireCodec.decode(String(event.data)) ?: return
            val now = System.currentTimeMillis()
            val lastReading = MonitoringState.lastReading(this)
            val maxTechnicalAgeMs = maxOf(AppSettings.load(this).staleDataMs, MIN_LIVE_REPLAY_AGE_MS)
            if (!TechnicalAlertReplayPolicy.shouldAccept(
                    nowMs = now,
                    alertTimestampMs = alert.timestampMs,
                    lastValidReadingMs = lastReading,
                    maxAgeMs = maxTechnicalAgeMs
                )
            ) {
                AlarmTimelineStore.add(
                    this,
                    "ESKİ TEKNİK ALARM YOK SAYILDI",
                    "Saatten gecikmeli gelen ${alert.type.name} alarmından sonra geçerli veri görüldüğü veya alarm çok eski olduğu için uzak uyarı başlatılmadı",
                    now
                )
                return
            }

            val alertId = AlertIdentity.of(alert)
            if (AlertAcknowledgementStore.isAcknowledged(this, alertId)) return

            if (alert.type == AlertType.SENSOR_FAILURE) {
                val lastHeartbeat = WatchHeartbeatStore.timestamp(this)
                val staleAlertedFor = MonitoringState.lastStaleAlertedFor(this)
                val disconnectAlertedFor = WatchHeartbeatStore.lastDisconnectAlertedFor(this)
                val suppress = TechnicalConnectivityCoalescingPolicy.suppressSensorFailure(
                    lastReadingMs = lastReading,
                    lastHeartbeatMs = lastHeartbeat,
                    staleAlertedForReadingMs = staleAlertedFor,
                    disconnectAlertedForHeartbeatMs = disconnectAlertedFor
                )
                TechnicalIncidentStore.markSensorFailureForReading(this, lastReading)
                if (suppress) {
                    AlarmTimelineStore.add(
                        this,
                        "TEKNİK ALARM BİRLEŞTİRİLDİ",
                        "Saat sensör arızası aynı aktif teknik olayın devamı olduğu için ikinci SMS/arama gönderilmedi",
                        alert.timestampMs
                    )
                    return
                }
            }

            val recent = HistoryStore.formatted(this, 4).lines().filter { it.isNotBlank() }
            AlertDispatcher(this).dispatch(alert, recent, null)
            return
        }
        if (event.path != "/health/reading") return

        val receivedAt = System.currentTimeMillis()
        val p = String(event.data).split('|')
        val reading = when {
            p.size >= 6 -> HealthReading(
                id = p[0],
                timestampMs = p[1].toLongOrNull() ?: receivedAt,
                spo2 = p[2].toIntOrNull(),
                heartRate = p[3].toIntOrNull(),
                valid = p[4].toBooleanStrictOrNull() ?: false,
                source = p[5]
            )
            p.size >= 5 -> HealthReading(
                timestampMs = p[0].toLongOrNull() ?: receivedAt,
                spo2 = p[1].toIntOrNull(),
                heartRate = p[2].toIntOrNull(),
                valid = p[3].toBooleanStrictOrNull() ?: false,
                source = p[4]
            )
            else -> return
        }

        if (!HistoryStore.addIfAbsent(this, reading)) return
        MonitoringState.markReading(this, receivedAt)
        SpO2ReliabilityStore.onWatchReading(this, reading.id, reading.timestampMs, reading.spo2, reading.valid, reading.heartRate)

        val cfg = AppSettings.load(this)
        val maxLiveAgeMs = maxOf(cfg.staleDataMs, MIN_LIVE_REPLAY_AGE_MS)
        val ageMs = (receivedAt - reading.timestampMs).coerceAtLeast(0L)
        if (ageMs > maxLiveAgeMs) return

        val spo2Pc60 = SourcePriorityCoordinator.isPc60Spo2Authoritative(this, receivedAt)
        val hrPc60 = SourcePriorityCoordinator.isPc60HeartRateAuthoritative(this, receivedAt)
        val stateSignature = AlarmEngineStateStore.signature(cfg, spo2Pc60, hrPc60)

        if (lastSpo2Pc60 != null && (lastSpo2Pc60 != spo2Pc60 || lastHrPc60 != hrPc60)) {
            engine = null
            activeConfig = null
            activeStateSignature = null
            AlarmEngineStateStore.clear(this)
        }
        lastSpo2Pc60 = spo2Pc60
        lastHrPc60 = hrPc60

        if (spo2Pc60) {
            watchSpO2Policy.reset()
            persistWatchSpO2Policy()
        }

        if (spo2Pc60 || hrPc60) {
            val detail = when {
                spo2Pc60 && hrPc60 -> "Watch ölçümü kaydedildi; SpO₂ ve nabız alarm kararı PC-60FW'ye bırakıldı"
                spo2Pc60 -> "Watch ölçümü kaydedildi; SpO₂ alarm kararı PC-60FW'de, nabız Galaxy Watch'ta"
                else -> "Watch ölçümü kaydedildi; nabız alarm kararı PC-60FW'de, SpO₂ Galaxy Watch'ta"
            }
            AlarmTimelineStore.add(this, "KAYNAK ÖNCELİĞİ", detail)
        }

        val recent = HistoryStore.formatted(this, 4).lines().filter { it.isNotBlank() }
        if (!spo2Pc60) dispatchWatchSpO2Decision(reading, recent)

        val alarmReading = reading.copy(
            spo2 = null,
            heartRate = if (hrPc60) null else reading.heartRate
        )
        if (alarmReading.heartRate == null) return

        val e = if (engine == null || activeConfig != cfg || activeStateSignature != stateSignature) {
            activeConfig = cfg
            activeStateSignature = stateSignature
            AlarmEngine(cfg).also { fresh ->
                AlarmEngineStateStore.load(this, stateSignature)?.let(fresh::restore)
                engine = fresh
            }
        } else engine!!

        val alerts = e.evaluate(alarmReading)
        AlarmEngineStateStore.save(this, stateSignature, e.snapshot())
        alerts.forEach { AlertDispatcher(this).dispatch(it, recent, alarmReading) }
    }

    private fun dispatchWatchSpO2Decision(reading: HealthReading, recent: List<String>) {
        val decision = watchSpO2Policy.evaluate(reading.timestampMs, reading.spo2, reading.valid)
        persistWatchSpO2Policy()
        val alert = when (decision) {
            WatchSpO2Decision.ALARM_IMMEDIATE -> AlertEvent(
                AlertType.SPO2_CRITICAL,
                reading.timestampMs,
                reading,
                "Saat SpO₂ kritik: %${reading.spo2}"
            )
            WatchSpO2Decision.ALARM_EARLY -> AlertEvent(
                AlertType.SPO2_LOW_CONFIRMED,
                reading.timestampMs,
                reading,
                "Saat SpO₂ 3 dakika boyunca %85 altından toparlanmadı: %${reading.spo2}"
            )
            WatchSpO2Decision.RECOVERED, WatchSpO2Decision.NONE -> null
        }
        if (alert != null) AlertDispatcher(this).dispatch(alert, recent, reading)
    }

    private fun restoreWatchSpO2Policy() {
        val prefs = getSharedPreferences(WATCH_SPO2_POLICY_PREFS, MODE_PRIVATE)
        val since = if (prefs.contains(KEY_OBSERVATION_SINCE)) prefs.getLong(KEY_OBSERVATION_SINCE, 0L) else null
        val latched = prefs.getBoolean(KEY_ALARM_LATCHED, false)
        watchSpO2Policy.restore(WatchSpO2AlarmSnapshot(since, latched))
    }

    private fun persistWatchSpO2Policy() {
        val snapshot = watchSpO2Policy.snapshot()
        val observationSince = snapshot.observationSince
        val editor = getSharedPreferences(WATCH_SPO2_POLICY_PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ALARM_LATCHED, snapshot.alarmLatched)
        if (observationSince == null) editor.remove(KEY_OBSERVATION_SINCE)
        else editor.putLong(KEY_OBSERVATION_SINCE, observationSince)
        editor.apply()
    }

    private fun sendAckReceipt(nodeId: String, receiptPayload: String) {
        if (nodeId.isBlank() || receiptPayload.isBlank()) return
        Wearable.getMessageClient(this)
            .sendMessage(nodeId, "/health/alarm_ack_result", receiptPayload.toByteArray())
    }

    companion object {
        private const val MIN_LIVE_REPLAY_AGE_MS = 10 * 60_000L
        private const val WATCH_SPO2_POLICY_PREFS = "watch_spo2_policy"
        private const val KEY_OBSERVATION_SINCE = "observation_since"
        private const val KEY_ALARM_LATCHED = "alarm_latched"
    }
}
