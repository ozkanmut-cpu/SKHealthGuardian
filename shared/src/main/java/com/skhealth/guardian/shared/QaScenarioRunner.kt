package com.skhealth.guardian.shared

data class QaScenarioResult(
    val id: String,
    val title: String,
    val passed: Boolean,
    val expected: String,
    val actual: String,
    val detail: String
)

object QaScenarioRunner {
    fun runAll(config: AlarmConfig = AlarmConfig()): List<QaScenarioResult> = listOf(
        immediateCritical(config),
        lowSpo2Confirmation(config),
        lowSpo2Recovery(config),
        highHrConfirmation(config),
        invalidReadingIgnored(config),
        pc60LowPersists(),
        pc60Recovers(),
        pc60HighHrPersists(config)
    )

    private fun immediateCritical(config: AlarmConfig): QaScenarioResult {
        val engine = AlarmEngine(config)
        val value = (config.spo2CriticalImmediate - 1).coerceAtLeast(1)
        val alerts = engine.evaluate(HealthReading(timestampMs = 1_000L, spo2 = value, heartRate = 80))
        val ok = alerts.any { it.type == AlertType.SPO2_CRITICAL }
        return result("spo2-critical", "SpO₂ kritik ilk ölçüm", ok, "İlk geçerli ölçümde kritik alarm", describe(alerts), "SpO₂=$value")
    }

    private fun lowSpo2Confirmation(config: AlarmConfig): QaScenarioResult {
        val engine = AlarmEngine(config)
        val value = maxOf(config.spo2CriticalImmediate, config.spo2LowThreshold - 1)
        var last = emptyList<AlertEvent>()
        repeat(config.spo2ConfirmCount.coerceAtLeast(1)) { index ->
            last = engine.evaluate(HealthReading(timestampMs = 10_000L + index * 120_000L, spo2 = value, heartRate = 80))
        }
        val ok = last.any { it.type == AlertType.SPO2_LOW_CONFIRMED }
        return result("spo2-confirm", "SpO₂ düşük doğrulama", ok, "${config.spo2ConfirmCount} düşük geçerli ölçümden sonra alarm", describe(last), "SpO₂=$value")
    }

    private fun lowSpo2Recovery(config: AlarmConfig): QaScenarioResult {
        val engine = AlarmEngine(config)
        val low = maxOf(config.spo2CriticalImmediate, config.spo2LowThreshold - 1)
        engine.evaluate(HealthReading(timestampMs = 1_000L, spo2 = low, heartRate = 80))
        val recovered = engine.evaluate(HealthReading(timestampMs = 121_000L, spo2 = config.spo2LowThreshold, heartRate = 80))
        val after = engine.evaluate(HealthReading(timestampMs = 241_000L, spo2 = low, heartRate = 80))
        val ok = recovered.isEmpty() && after.none { it.type == AlertType.SPO2_LOW_CONFIRMED }
        return result("spo2-recovery", "SpO₂ toparlanma sayaç sıfırlama", ok, "Normal ölçüm düşük sayaçlarını sıfırlar", "toparlanma=${describe(recovered)}, sonraki=${describe(after)}", "$low → ${config.spo2LowThreshold} → $low")
    }

    private fun highHrConfirmation(config: AlarmConfig): QaScenarioResult {
        val engine = AlarmEngine(config)
        val hr = config.heartRateHighThreshold + 1
        var last = emptyList<AlertEvent>()
        repeat(config.heartRateHighConfirmCount.coerceAtLeast(1)) { index ->
            last = engine.evaluate(HealthReading(timestampMs = 20_000L + index * 120_000L, spo2 = 97, heartRate = hr))
        }
        val ok = last.any { it.type == AlertType.HEART_RATE_HIGH_CONFIRMED }
        return result("hr-high", "Yüksek nabız doğrulama", ok, "${config.heartRateHighConfirmCount} yüksek ölçümden sonra alarm", describe(last), "HR=$hr")
    }

    private fun invalidReadingIgnored(config: AlarmConfig): QaScenarioResult {
        val engine = AlarmEngine(config)
        val alerts = engine.evaluate(HealthReading(timestampMs = 30_000L, spo2 = 60, heartRate = 200, valid = false))
        return result("invalid-ignore", "Geçersiz sensör verisi", alerts.isEmpty(), "Alarm üretilmez", describe(alerts), "valid=false")
    }

    private fun pc60LowPersists(): QaScenarioResult {
        val policy = Pc60AlarmPolicy(alarmThreshold = 85, confirmDelayMs = 120_000L, recoveryThreshold = 85, recoveryStableMs = 10_000L)
        val first = policy.evaluate(pc(1_000L, 84, 78))
        val second = policy.evaluate(pc(121_000L, 84, 78))
        val ok = first == Pc60Decision.NONE && second == Pc60Decision.ALARM
        return result("pc60-low", "PC-60FW düşük SpO₂ 2 dk", ok, "2 dakika düşük kalırsa alarm", "$first → $second", "84% sabit")
    }

    private fun pc60Recovers(): QaScenarioResult {
        val policy = Pc60AlarmPolicy(alarmThreshold = 85, confirmDelayMs = 120_000L, recoveryThreshold = 85, recoveryStableMs = 10_000L)
        val first = policy.evaluate(pc(1_000L, 84, 78))
        val recoveryStart = policy.evaluate(pc(60_000L, 86, 78))
        val recovered = policy.evaluate(pc(71_000L, 86, 78))
        val ok = first == Pc60Decision.NONE && recoveryStart == Pc60Decision.NONE && recovered == Pc60Decision.RECOVERED
        return result("pc60-recover", "PC-60FW toparlanma", ok, ">85% en az 10 sn sabitse alarm iptal", "$first → $recoveryStart → $recovered", "84% → 86%")
    }

    private fun pc60HighHrPersists(config: AlarmConfig): QaScenarioResult {
        val count = config.heartRateHighConfirmCount.coerceAtLeast(1)
        val policy = Pc60HeartRatePolicy(
            highThreshold = config.heartRateHighThreshold,
            highConfirmCount = count,
            lowEnabled = config.heartRateLowEnabled,
            lowThreshold = config.heartRateLowThreshold,
            lowConfirmCount = config.heartRateLowConfirmCount,
            confirmIntervalMs = 120_000L
        )
        var decision = Pc60HrDecision.NONE
        repeat(count) { index -> decision = policy.evaluate(pc(1_000L + index * 120_000L, 97, config.heartRateHighThreshold + 1)) }
        val ok = decision == Pc60HrDecision.HIGH_ALARM
        return result("pc60-hr-high", "PC-60FW yüksek nabız doğrulama", ok, "$count zaman aralıklı yüksek ölçümden sonra alarm", decision.name, "HR=${config.heartRateHighThreshold + 1}")
    }

    private fun pc(ts: Long, spo2: Int, hr: Int) = Pc60Sample(ts, spo2, hr, 4.0, probeOff = false, pulseSearching = false)

    private fun describe(alerts: List<AlertEvent>): String = if (alerts.isEmpty()) "alarm yok" else alerts.joinToString { it.type.name }

    private fun result(id: String, title: String, passed: Boolean, expected: String, actual: String, detail: String) =
        QaScenarioResult(id, title, passed, expected, actual, detail)
}
