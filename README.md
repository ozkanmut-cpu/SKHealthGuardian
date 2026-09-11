# Orko Takip — V0.7 Reliability

Orko Takip is a Galaxy Watch + Android + PC-60FW safety/wellness monitoring system. Tasker, Gadgetbridge, Health Connect and ntfy are not required in the critical alarm chain.

## Architecture

- `wear`: Galaxy Watch sensor acquisition, local watch alarm, reboot recovery, heartbeat, battery status, sensor watchdog, offline reading queue and phone transport.
- `mobile`: receives readings, evaluates alarms, full-screen local alarm, PC-60FW BLE monitoring, SMS/call dispatch, settings, contacts, history, graphs, system tests, export/backup and watchdog.
- `shared`: thresholds, models, deterministic alarm engine, source-priority policies, exact-ID reliability policies and deterministic stress/chaos models.

## Alarm defaults

- Watch SpO₂ normal sampling target: every 5 minutes.
- Watch SpO₂ `<80%`: immediate alarm on the first valid reading.
- Watch SpO₂ `80–89%`: two valid low readings required.
- Heart rate `>130 bpm`: two valid high readings required.
- Low-HR alarm exists but is disabled by default.
- Phone stale-data alarm default: 10 minutes without a watch reading.
- PC-60FW is authoritative for a measurement channel while it has valid and fresh data.
- PC-60FW low SpO₂ default threshold: `≤85%`.
- PC-60FW low SpO₂ is allowed 2 minutes to recover while the probe remains valid; recovery above the configured threshold must remain stable before the alarm is cancelled.
- Probe-off, pulse-searching or invalid PC-60FW data resets the low-SpO₂ confirmation session.
- Optional unanswered-alarm escalation is disabled by default (`0` minutes) and can be enabled from phone settings.
- Pending escalations restored after reboot are expired when the originating alarm is older than the configurable restore-age limit (default 60 minutes).

All thresholds and operational timing values exposed by the app are configurable from the phone settings screen.

## Alarm episode model

The alarm engine uses episode/latch semantics rather than generating a fresh alert for every abnormal sample.

- Continuous critical SpO₂ produces one critical episode until a normal recovery reading is observed.
- A confirmed LOW SpO₂ episode may escalate once to CRITICAL if the value deteriorates below the immediate-critical threshold.
- A CRITICAL episode does not downgrade into a second LOW alert while SpO₂ remains abnormal.
- Continuous high-HR or enabled low-HR conditions produce one alarm per episode.
- Recovery resets the episode and allows a later abnormal condition to create a new alarm.
- Watch alarm-engine episode state is persisted across service/process restart and restored only when the alarm configuration and active source-priority signature still match.

## Exact alarm identity and ACK protocol

Alarm ownership is based on stable exact IDs, **not timestamps**.

- Measurement alarms derive identity from `AlertType + HealthReading.id`.
- `HealthReading.id` is a stable UUID transported from watch to phone and retained across replay/offline queue delivery.
- Technical/fallback alarms can use a typed timestamp identity when no measurement UUID exists.
- ACKs received from the phone or watch are stored as exact IDs in a bounded durable store.
- A delayed ACK for an older alarm cannot silence a newer alarm, even if the wall clock moved backwards and the newer alarm has an earlier timestamp.
- Wear → phone ACK v2 messages include a persistent sequence number, exact alarm ID, alert timestamp and reason.
- Exact ACK is rechecked before initial SMS, before each call attempt, immediately before `TelecomManager.placeCall()`, before escalation scheduling and after escalation arming.
- Exact ACK persistence uses synchronous durable writes for the safety-critical acknowledgement path.
- Legacy timestamp ACK behavior is retained only for migration/backward compatibility.

This allows scenarios such as `20:00 alarm → ACK → clock rollback → 19:50 new alarm` without the old ACK contaminating the new alarm.

## Remote delivery reliability

- Initial SMS/call delivery stops as soon as the matching exact alarm is acknowledged.
- SMS uses Android SENT/DELIVERED callbacks.
- Multipart SMS callback `PendingIntent` identity is URI-based using full `messageId + attempt + part + status`; it does not depend on hash request codes.
- Failed SMS sends are retried automatically with durable atomic retry claims so parallel callbacks cannot schedule the same retry more than once.
- SMS retries carry the exact alarm ID and are suppressed if that exact alarm is acknowledged before retry execution.
- Call failover tries the next call-enabled contact when call initiation fails.
- Calls recheck acknowledgement immediately before the actual telecom call is placed.
- Optional unanswered-alarm escalation repeats SMS and call attempts after a user-selected delay.

### Crash-safe escalation lease

Escalation execution uses a persistent short lease/claim:

1. A receiver must atomically acquire the escalation identity before remote delivery.
2. Parallel duplicate receivers lose the active lease and cannot send duplicate SMS/calls.
3. After claiming, the receiver schedules a recovery alarm for the lease deadline before starting remote delivery.
4. If the process dies during SMS/call delivery, the lease eventually expires and the recovery receiver can retry.
5. Successful delivery writes a durable completion marker, removes the lease and cancels the recovery escalation.
6. Completed escalation identities cannot acquire a new lease.
7. Wall-clock rollback does not prematurely expire an active lease.

Sequential and reboot-delivered duplicates are therefore suppressed without permanently losing an escalation when the process crashes mid-flight.

## Reliability / safety features

- Wear OS health foreground service + `START_STICKY`.
- Watch boot recovery only after required health permissions exist.
- Strict scheduling and mutex protection against manual/scheduled overlap.
- Sensor reconnect/retry loops and watch-side sensor watchdog.
- Local watch alarm independent of phone connectivity.
- Store-and-forward queue for up to 500 readings while disconnected.
- Stable reading UUIDs prevent replay duplicates.
- Old replayed readings are stored in history but do not trigger fresh emergency SMS/calls.
- Persistent alert deduplication/cooldown with exact-ID replay suppression.
- A newly acknowledged/resolved alarm episode does not block a genuinely new exact alarm merely because it occurs inside the type cooldown window.
- Phone stale-data watchdog.
- One-minute watch heartbeat independent of the sensor measurement loop.
- Watch and phone low-battery warnings with recovery hysteresis.
- Settings are synchronized phone → watch and the watch returns a `CONFIG_OK` acknowledgement with the applied thresholds.
- Persistent alarm timeline and masked SMS/call delivery logs.
- Corrupt exact escalation persistence entries are cleaned during reboot restore instead of remaining stuck indefinitely.

## QA and chaos testing

The repository includes deterministic unit, stress and chaos coverage in addition to normal alarm tests.

- Core alarm scenarios cover immediate critical SpO₂, confirmed low SpO₂, recovery/reset, high HR, invalid readings and PC-60FW behavior.
- Episode stress runs hundreds of thousands of readings through alarm/recovery cycles.
- Android persistence stress covers history, timeline, replay deduplication, SMS retry claims, bounded ACK-ID persistence and escalation lease claims with parallel workers.
- Legacy whole-chain chaos retains timestamp-model migration coverage.
- Exact-ID chaos intentionally introduces wall-clock rollback, delayed ACKs, process restarts, lease expiry/recovery, duplicate receiver attempts, sensor replay and PC-60FW source flaps.
- Exact-ID chaos is exercised across multiple deterministic seeds totaling millions of events.

The exact-ID chaos invariant is: **alarm identity is never inferred from wall-clock ordering**.

## Phone UI

- Alarm thresholds + stale-data timeout + optional escalation delay.
- Configurable reboot restore maximum age for old pending escalations.
- Galaxy Watch sampling/retry timings.
- PC-60FW alarm/recovery/stabilization timings and thresholds.
- Emergency contact editor with per-contact SMS/call flags.
- Emergency contacts encrypted with Android Keystore AES-GCM; legacy plaintext data migrates automatically.
- Full-screen lock-screen alarm with SpO₂/HR, reason, delivery status, silence, re-measure and call controls.
- Real SMS test and real call test.
- Full system-test screen: permission/contact preflight, watch connection, config ACK request, watch self-test and manual measurement; destructive SMS/call test requires confirmation.
- System-health screen: permissions, full-screen-intent, battery optimization, recent reading, watch heartbeat, watch battery, phone battery and latest watch status/ACK.
- Measurement history (last 1000 readings).
- Measurement graphs for SpO₂ and heart rate.
- Alarm event timeline.
- CSV measurement export.
- JSON backup containing settings, contacts, readings, alarm timeline and delivery log. JSON export contains phone numbers in plaintext and should be stored securely.

## Samsung Health Sensor SDK

Samsung's proprietary AAR is intentionally **not committed to this repository**.

Place the official Samsung Health Sensor SDK AAR at:

`wear/libs/samsung-health-sensor-api.aar`

The Gradle build auto-detects it. Without the AAR, CI compiles against the mock gateway. With the AAR, `src/samsung/.../SamsungSensorGateway.kt` is selected.

## Android permissions / deployment

The phone uses `SEND_SMS` and `CALL_PHONE`. `SEND_SMS` is hard-restricted on Android, so private sideload deployment must ensure the installer/device grants or allowlists it. The app does not automatically dial emergency-service numbers.

Watch setup requests granular heart-rate, oxygen-saturation, background-health and notification permissions before starting monitoring.

## CI

`.github/workflows/android-build.yml` runs shared alarm/unit/stress/chaos tests and builds both mobile and wear debug APKs. CI uses the mock sensor gateway unless the proprietary Samsung AAR is supplied outside the repository.

## Remaining physical-device validation

Software-side work that does not require the physical devices is largely implemented. Remaining critical work requires the actual Galaxy Watch and PC-60FW:

1. Add the official Samsung Health Sensor SDK AAR and compile the real gateway.
2. Resolve any exact Samsung SDK API signature differences if necessary.
3. Enable the required Samsung developer mode and install the real Wear APK.
4. Verify on-demand/periodic SpO₂ works repeatedly while the display is off and the health foreground service is running.
5. Pair the PC-60FW and validate real BLE packet parsing, reconnect, probe-off/pulse-searching and freshness behavior.
6. Run multi-hour sampling plus reboot, disconnect/reconnect, offline queue, heartbeat, stale-data and battery tests.
7. Run the real end-to-end alarm chain: watch/PC-60FW → phone → SMS SENT result/retry → call/failover → silence/ACK → escalation/recovery lease.
8. Compare Watch SpO₂ against the PC-60FW/another known-good fingertip pulse oximeter, especially around low readings.
9. Run controlled acceptance tests for ACK during SMS dispatch, ACK immediately before call placement, process kill during escalation and reboot with pending escalation.

This is a wellness/safety alert system, not a certified medical device.
