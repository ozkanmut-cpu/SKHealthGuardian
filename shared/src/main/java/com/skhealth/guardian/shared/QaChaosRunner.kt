package com.skhealth.guardian.shared

import kotlin.random.Random

data class QaChaosReport(
    val seed: Int,
    val operations: Int,
    val passed: Boolean,
    val violations: List<String>,
    val acceptedReadings: Int,
    val duplicateReadingsRejected: Int,
    val remoteActionsAllowed: Int,
    val remoteActionsSuppressedAfterAck: Int,
    val processRestarts: Int,
    val sourceFlaps: Int
)

/**
 * Deterministic whole-chain chaos model. It intentionally does not perform Android side effects.
 * Instead it combines the same production decision rules that guard replay, ACK, escalation,
 * retry and source priority behavior and verifies cross-component invariants under randomized
 * event ordering.
 */
object QaChaosRunner {
    fun run(
        operations: Int = 500_000,
        seed: Int = 20260909
    ): QaChaosReport {
        val random = Random(seed)
        val violations = mutableListOf<String>()
        val seenReadingIds = HashSet<String>()
        val sensorGate = MonotonicTimestampGate()

        var ackWatermark = 0L
        var latestAlertTs = 0L
        var nextAlertTs = 1_000_000L
        var latestSensorTs = 1_000_000L
        var acceptedReadings = 0
        var duplicateRejected = 0
        var remoteAllowed = 0
        var remoteSuppressed = 0
        var processRestarts = 0
        var sourceFlaps = 0
        var lastPc60Authority: Boolean? = null

        repeat(operations.coerceAtLeast(1)) { index ->
            when (random.nextInt(10)) {
                0, 1 -> {
                    // Reading delivery: sometimes replay an existing id.
                    val replay = seenReadingIds.isNotEmpty() && random.nextInt(5) == 0
                    val id = if (replay) "r-${random.nextInt(maxOf(1, acceptedReadings))}" else "r-$acceptedReadings"
                    if (seenReadingIds.add(id)) acceptedReadings++ else duplicateRejected++
                }

                2 -> {
                    // Sensor packets deliberately arrive out of order around reconnects.
                    val candidate = if (random.nextBoolean()) {
                        latestSensorTs + random.nextLong(0L, 5L)
                    } else {
                        (latestSensorTs - random.nextLong(1L, 50L)).coerceAtLeast(1L)
                    }
                    val accepted = sensorGate.accept(candidate)
                    if (accepted) latestSensorTs = maxOf(latestSensorTs, candidate)
                    if (sensorGate.lastAccepted() < latestSensorTs) {
                        violations += "sensor watermark moved backwards at op=$index"
                    }
                }

                3 -> {
                    // New alarm.
                    nextAlertTs += random.nextLong(1L, 10L)
                    latestAlertTs = nextAlertTs
                    if (!RemoteDeliveryGate.shouldDeliver(ackWatermark, latestAlertTs)) {
                        violations += "newer alert suppressed by older ACK at op=$index"
                    }
                }

                4 -> {
                    // ACK may be delayed/replayed. Production store is monotonic, so model uses max.
                    val candidate = when {
                        latestAlertTs == 0L -> random.nextLong(0L, nextAlertTs + 1L)
                        random.nextBoolean() -> latestAlertTs
                        else -> random.nextLong(0L, latestAlertTs + 1L)
                    }
                    ackWatermark = maxOf(ackWatermark, candidate)
                }

                5, 6 -> {
                    // SMS retry / escalation / call-failover step for a current or stale alarm.
                    val targetAlert = when {
                        latestAlertTs == 0L -> 0L
                        random.nextInt(4) == 0 -> (latestAlertTs - random.nextLong(0L, 50L)).coerceAtLeast(1L)
                        else -> latestAlertTs
                    }
                    val allowed = RemoteDeliveryGate.shouldDeliver(ackWatermark, targetAlert)
                    val escalates = EscalationGate.shouldEscalate(ackWatermark, targetAlert)
                    if (targetAlert > 0L && allowed != escalates) {
                        violations += "remote/escalation gate disagreement at op=$index alert=$targetAlert ack=$ackWatermark"
                    }
                    if (allowed) {
                        remoteAllowed++
                        if (targetAlert > 0L && ackWatermark >= targetAlert) {
                            violations += "remote action allowed after ACK at op=$index"
                        }
                    } else {
                        remoteSuppressed++
                    }
                }

                7 -> {
                    // PC-60 freshness / validity flap.
                    val now = latestSensorTs + random.nextLong(0L, 20_000L)
                    val lastPacket = latestSensorTs
                    val probeOff = random.nextInt(10) == 0
                    val searching = random.nextInt(10) == 0
                    val pi = if (random.nextInt(12) == 0) 0.0 else 1.2
                    val spo2 = if (random.nextInt(15) == 0) null else 96
                    val current = SourcePriorityPolicy.isPc60Spo2Authoritative(
                        nowMs = now,
                        lastPacketAt = lastPacket,
                        spo2 = spo2,
                        perfusionIndex = pi,
                        probeOff = probeOff,
                        pulseSearching = searching
                    )
                    if (lastPc60Authority != null && lastPc60Authority != current) sourceFlaps++
                    lastPc60Authority = current
                    val age = now - lastPacket
                    if (current && (age !in 0..SourcePriorityPolicy.DEFAULT_PC60_FRESH_MS || probeOff || searching || pi <= 0.0 || spo2 !in 1..100)) {
                        violations += "invalid PC60 authority at op=$index"
                    }
                }

                8 -> {
                    // Process/service death: ephemeral state disappears, persisted watermarks must not.
                    processRestarts++
                    val beforeAck = ackWatermark
                    val beforeSensor = sensorGate.lastAccepted()
                    // The model intentionally keeps persisted state and resets no durable watermark.
                    if (ackWatermark != beforeAck || sensorGate.lastAccepted() != beforeSensor) {
                        violations += "durable state changed across restart at op=$index"
                    }
                }

                else -> {
                    // Explicit invariant sweep.
                    if (latestAlertTs > 0L && ackWatermark >= latestAlertTs) {
                        if (RemoteDeliveryGate.shouldDeliver(ackWatermark, latestAlertTs)) {
                            violations += "ACKed latest alert became deliverable at op=$index"
                        }
                        if (EscalationGate.shouldEscalate(ackWatermark, latestAlertTs)) {
                            violations += "ACKed latest alert became escalatable at op=$index"
                        }
                    }
                }
            }

            if (violations.size >= 50) return@repeat
        }

        return QaChaosReport(
            seed = seed,
            operations = operations.coerceAtLeast(1),
            passed = violations.isEmpty(),
            violations = violations.take(50),
            acceptedReadings = acceptedReadings,
            duplicateReadingsRejected = duplicateRejected,
            remoteActionsAllowed = remoteAllowed,
            remoteActionsSuppressedAfterAck = remoteSuppressed,
            processRestarts = processRestarts,
            sourceFlaps = sourceFlaps
        )
    }
}
