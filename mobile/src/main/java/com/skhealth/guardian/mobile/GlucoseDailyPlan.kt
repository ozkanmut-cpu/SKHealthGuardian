package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.BloodGlucoseReading
import com.skhealth.guardian.shared.GlucoseScheduleEngine

/**
 * Derived daily-plan view for glucose checkpoints.
 *
 * Completion is never persisted separately: it is recalculated from the current
 * medication-derived plan plus readings explicitly confirmed as Orko's. This
 * prevents stale completion flags when ownership is corrected later.
 */
object GlucoseDailyPlan {
    enum class State {
        UPCOMING,
        DUE,
        OVERDUE,
        COMPLETED
    }

    data class SlotStatus(
        val checkpoint: GlucoseScheduleEngine.Checkpoint,
        val targetAtMs: Long,
        val windowStartMs: Long,
        val windowEndMs: Long,
        val state: State,
        val completedByReadingId: String? = null,
        val completedAtMs: Long? = null,
        val completedValueMgDl: Int? = null
    )

    fun statuses(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): List<SlotStatus> {
        val planned = GlucoseScheduleCoordinator.plannedCheckpoints(context)
        val confirmed = BloodGlucoseStore.confirmedOrko(context, limit = 2_000)
        return planned.map { checkpoint -> statusFor(checkpoint, confirmed, nowMs) }
    }

    fun statusForReading(
        context: Context,
        reading: BloodGlucoseReading,
        nowMs: Long = System.currentTimeMillis()
    ): SlotStatus? {
        if (!reading.belongsToOrko) return null
        val planned = GlucoseScheduleCoordinator.plannedCheckpoints(context)
        val match = GlucoseScheduleEngine.matchReading(reading.measuredAtMs, planned) ?: return null
        return statuses(context, nowMs).firstOrNull { it.checkpoint == match.checkpoint }
    }

    fun nextActionable(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): SlotStatus? = statuses(context, nowMs)
        .firstOrNull { it.state != State.COMPLETED }

    private fun statusFor(
        checkpoint: GlucoseScheduleEngine.PlannedCheckpoint,
        confirmed: List<BloodGlucoseReading>,
        nowMs: Long
    ): SlotStatus {
        val matchingReading = confirmed
            .asSequence()
            .filter { checkpoint.contains(it.measuredAtMs) }
            .minWithOrNull(
                compareBy<BloodGlucoseReading> {
                    kotlin.math.abs(it.measuredAtMs - checkpoint.targetAtMs)
                }.thenBy { it.measuredAtMs }
            )

        val state = when {
            matchingReading != null -> State.COMPLETED
            nowMs < checkpoint.windowStartMs -> State.UPCOMING
            nowMs <= checkpoint.windowEndMs -> State.DUE
            else -> State.OVERDUE
        }

        return SlotStatus(
            checkpoint = checkpoint.checkpoint,
            targetAtMs = checkpoint.targetAtMs,
            windowStartMs = checkpoint.windowStartMs,
            windowEndMs = checkpoint.windowEndMs,
            state = state,
            completedByReadingId = matchingReading?.id,
            completedAtMs = matchingReading?.measuredAtMs,
            completedValueMgDl = matchingReading?.valueMgDl
        )
    }
}
