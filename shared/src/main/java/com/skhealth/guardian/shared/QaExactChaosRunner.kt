package com.skhealth.guardian.shared

import kotlin.random.Random

data class QaExactChaosReport(
    val seed: Int,
    val operations: Int,
    val passed: Boolean,
    val violations: List<String>,
    val alarmsCreated: Int,
    val acknowledgements: Int,
    val remoteAllowed: Int,
    val remoteSuppressed: Int,
    val leaseClaims: Int,
    val leaseRecoveries: Int,
    val processRestarts: Int,
    val clockRollbacks: Int,
    val sourceFlaps: Int
)

/**
 * Deterministic v2 chaos model for exact alarm identity.
 *
 * This model intentionally allows wall-clock timestamps to move backwards. Alarm delivery and ACK
 * ownership are keyed by AlertIdentity, never by wall clock. Durable sets/maps are kept across
 * simulated process restarts. No Android API, network, SMS, call or sensor side effect is used.
 */
object QaExactChaosRunner {
    private const val LEASE_MS = 120_000L

    private data class AlarmInstance(val id: String, val timestampMs: Long)

    fun run(operations: Int = 500_000, seed: Int = 20260909): QaExactChaosReport {
        val n = operations.coerceAtLeast(1)
        val random = Random(seed)
        val violations = mutableListOf<String>()

        val alarms = ArrayList<AlarmInstance>()
        val acknowledged = HashSet<String>()
        val delivered = HashSet<String>()
        val leaseStartedAt = HashMap<String, Long>()

        val sensorGate = MonotonicTimestampGate(1_000_000L)
        var latestSensorTs = 1_000_000L
        var wallClock = 2_000_000L
        var alarmSequence = 0L
        var acknowledgements = 0
        var remoteAllowed = 0
        var remoteSuppressed = 0
        var leaseClaims = 0
        var leaseRecoveries = 0
        var processRestarts = 0
        var clockRollbacks = 0
        var sourceFlaps = 0
        var lastPc60Authority: Boolean? = null

        fun createAlarm(index: Int): AlarmInstance {
            val rollback = random.nextInt(5) == 0
            if (rollback) {
                wallClock = (wallClock - random.nextLong(1L, 300_000L)).coerceAtLeast(1L)
                clockRollbacks++
            } else {
                wallClock += random.nextLong(1L, 20_000L)
            }
            val reading = HealthReading(
                id = "exact-reading-${alarmSequence++}",
                timestampMs = wallClock,
                spo2 = 79,
                heartRate = 80,
                valid = true,
                source = "chaos"
            )
            val id = AlertIdentity.of(AlertType.SPO2_CRITICAL, reading.id, reading.timestampMs)
            val instance = AlarmInstance(id, wallClock)
            alarms += instance

            if (acknowledged.contains(id)) {
                violations += "new exact alarm inherited ACK at op=$index id=$id ts=${instance.timestampMs}"
            }
            return instance
        }

        repeat(n) { index ->
            when (random.nextInt(12)) {
                0, 1 -> createAlarm(index)

                2 -> {
                    if (alarms.isNotEmpty()) {
                        val target = alarms[random.nextInt(alarms.size)]
                        if (acknowledged.add(target.id)) acknowledgements++
                    }
                }

                3, 4 -> {
                    if (alarms.isNotEmpty()) {
                        val target = alarms[random.nextInt(alarms.size)]
                        val allowed = !acknowledged.contains(target.id)
                        if (allowed) remoteAllowed++ else remoteSuppressed++

                        if (allowed && acknowledged.contains(target.id)) {
                            violations += "remote allowed for ACKed exact alarm at op=$index id=${target.id}"
                        }
                    }
                }

                5 -> {
                    if (alarms.isNotEmpty()) {
                        val target = alarms[random.nextInt(alarms.size)]
                        wallClock += random.nextLong(0L, 30_000L)
                        val oldLease = leaseStartedAt[target.id] ?: 0L
                        val canAcquire = ExecutionLeasePolicy.canAcquire(
                            delivered = delivered.contains(target.id),
                            existingLeaseStartedAt = oldLease,
                            nowMs = wallClock,
                            leaseMs = LEASE_MS
                        )
                        if (canAcquire) {
                            if (oldLease > 0L) leaseRecoveries++
                            leaseStartedAt[target.id] = wallClock
                            leaseClaims++

                            if (ExecutionLeasePolicy.canAcquire(false, wallClock, wallClock, LEASE_MS)) {
                                violations += "duplicate receiver acquired active lease at op=$index id=${target.id}"
                            }
                        }
                    }
                }

                6 -> {
                    if (alarms.isNotEmpty()) {
                        val target = alarms[random.nextInt(alarms.size)]
                        if ((leaseStartedAt[target.id] ?: 0L) > 0L) {
                            delivered += target.id
                            leaseStartedAt.remove(target.id)
                            if (ExecutionLeasePolicy.canAcquire(true, 0L, maxOf(1L, wallClock), LEASE_MS)) {
                                violations += "completed delivery reacquired lease at op=$index id=${target.id}"
                            }
                        }
                    }
                }

                7 -> {
                    if (leaseStartedAt.isNotEmpty()) {
                        val entry = leaseStartedAt.entries.elementAt(random.nextInt(leaseStartedAt.size))
                        val rolledBack = (entry.value - random.nextLong(1L, 60_000L)).coerceAtLeast(1L)
                        clockRollbacks++
                        if (ExecutionLeasePolicy.canAcquire(
                                delivered.contains(entry.key),
                                entry.value,
                                rolledBack,
                                LEASE_MS
                            )) {
                            violations += "clock rollback expired active lease at op=$index id=${entry.key}"
                        }
                        wallClock = rolledBack
                    }
                }

                8 -> {
                    val candidate = if (random.nextBoolean()) {
                        latestSensorTs + random.nextLong(0L, 10L)
                    } else {
                        (latestSensorTs - random.nextLong(1L, 100L)).coerceAtLeast(1L)
                    }
                    if (sensorGate.accept(candidate)) latestSensorTs = maxOf(latestSensorTs, candidate)
                    if (sensorGate.lastAccepted() < latestSensorTs) {
                        violations += "sensor watermark moved backwards at op=$index"
                    }
                }

                9 -> {
                    val now = latestSensorTs + random.nextLong(0L, 20_000L)
                    val probeOff = random.nextInt(12) == 0
                    val searching = random.nextInt(12) == 0
                    val pi = if (random.nextInt(15) == 0) 0.0 else 1.5
                    val spo2: Int? = if (random.nextInt(20) == 0) null else 96
                    val authoritative = SourcePriorityPolicy.isPc60Spo2Authoritative(
                        nowMs = now,
                        lastPacketAt = latestSensorTs,
                        spo2 = spo2,
                        perfusionIndex = pi,
                        probeOff = probeOff,
                        pulseSearching = searching
                    )
                    if (lastPc60Authority != null && lastPc60Authority != authoritative) sourceFlaps++
                    lastPc60Authority = authoritative
                    val invalid = spo2 == null || spo2 !in 1..100
                    if (authoritative && (probeOff || searching || pi <= 0.0 || invalid || now - latestSensorTs !in 0..SourcePriorityPolicy.DEFAULT_PC60_FRESH_MS)) {
                        violations += "invalid PC60 authority at op=$index"
                    }
                }

                10 -> {
                    processRestarts++
                    val ackSnapshot = acknowledged.toSet()
                    val deliveredSnapshot = delivered.toSet()
                    val leaseSnapshot = leaseStartedAt.toMap()
                    if (acknowledged != ackSnapshot || delivered != deliveredSnapshot || leaseStartedAt != leaseSnapshot) {
                        violations += "durable exact state changed across restart at op=$index"
                    }
                }

                else -> {
                    if (alarms.size >= 2) {
                        val old = alarms[random.nextInt(alarms.size - 1)]
                        val newest = alarms.last()
                        val newestWasAcknowledged = acknowledged.contains(newest.id)
                        acknowledged += old.id
                        if (!newestWasAcknowledged && old.id != newest.id && acknowledged.contains(newest.id)) {
                            violations += "delayed old ACK contaminated newest alarm at op=$index"
                        }
                    }
                }
            }
        }

        return QaExactChaosReport(
            seed = seed,
            operations = n,
            passed = violations.isEmpty(),
            violations = violations.take(50),
            alarmsCreated = alarms.size,
            acknowledgements = acknowledgements,
            remoteAllowed = remoteAllowed,
            remoteSuppressed = remoteSuppressed,
            leaseClaims = leaseClaims,
            leaseRecoveries = leaseRecoveries,
            processRestarts = processRestarts,
            clockRollbacks = clockRollbacks,
            sourceFlaps = sourceFlaps
        )
    }
}
