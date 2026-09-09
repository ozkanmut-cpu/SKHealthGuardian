# SK Health Guardian — V0.6 Reliability

Galaxy Watch8 + Android safety/wellness monitoring system. Tasker, Gadgetbridge, Health Connect and ntfy are not required in the alarm chain.

## Architecture

- `wear`: Galaxy Watch8 sensor acquisition, local watch alarm, reboot recovery, heartbeat, battery status, sensor watchdog, offline reading queue and phone transport.
- `mobile`: receives readings, evaluates alarms, full-screen local alarm, SMS/call dispatch, settings, contacts, history, graphs, system tests, export/backup and watchdog.
- `shared`: thresholds, models and deterministic alarm engine.

## Alarm defaults

- SpO₂ every 5 minutes on a strict wall-clock schedule.
- SpO₂ `<80%`: immediate alarm on first valid reading.
- SpO₂ `80–89%`: confirmation after 2 minutes; two valid low readings required.
- Failed confirmation: reconnect + one final attempt 1 minute later.
- Heart rate every 5 minutes.
- HR `>130 bpm`: confirmation after 2 minutes; failed confirmation gets one final attempt 1 minute later.
- Low-HR alarm exists but is disabled by default.
- Phone stale-data alarm default: 10 minutes without a watch reading.
- Optional unanswered-alarm escalation is disabled by default (`0` minutes) and can be enabled from phone settings.

## Reliability / safety features

- Wear OS health foreground service + `START_STICKY`.
- Watch boot recovery only after required health permissions exist.
- Strict wall-clock scheduler and mutex protection against manual/scheduled overlap.
- Sensor reconnect/retry loops and watch-side sensor watchdog.
- Local watch alarm independent of phone connectivity.
- Store-and-forward queue for up to 500 readings while disconnected.
- Stable reading UUIDs prevent replay duplicates.
- Old replayed readings are stored in history but do not trigger fresh emergency SMS/calls.
- Persistent alert deduplication/cooldown; significant deterioration can bypass cooldown.
- Phone stale-data watchdog.
- One-minute watch heartbeat independent of the sensor measurement loop.
- Watch and phone low-battery warnings with recovery hysteresis.
- Settings are synchronized phone → watch and the watch returns a `CONFIG_OK` acknowledgement with the applied thresholds.
- SMS uses Android sent-result callbacks. Failed sends are retried automatically after 30 seconds and then 90 seconds, with retry deduplication.
- Call failover tries the next call-enabled contact when call initiation fails.
- Optional unanswered-alarm escalation can repeat SMS and call attempts after a user-selected delay.
- Alarm acknowledgement/silence cancels escalation behavior.
- Persistent alarm timeline and masked SMS/call delivery logs.

## Phone UI

- Alarm thresholds + stale-data timeout + optional escalation delay.
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

`.github/workflows/android-build.yml` runs shared alarm unit tests and builds both mobile and wear debug APKs. CI uses the mock sensor gateway unless the proprietary Samsung AAR is supplied outside the repository.

## Remaining physical Watch8 validation

Software-side work that does not require the physical watch is implemented in V0.6. Remaining critical work requires the actual Galaxy Watch8:

1. Add the official Samsung Health Sensor SDK AAR and compile the real gateway.
2. Resolve any exact Samsung v1.4.1 API signature differences if necessary.
3. Enable Health Sensor Service Developer Mode and install the real Wear APK.
4. Verify `SPO2_ON_DEMAND` works repeatedly while the display is off and the health foreground service is running.
5. Run multi-hour 5-minute sampling plus reboot, disconnect/reconnect, offline queue, heartbeat, stale-data and battery tests.
6. Run the real end-to-end alarm chain: watch alarm → phone → SMS sent result/retry → call/failover → silence/escalation.
7. Compare Watch8 SpO₂ against a known-good fingertip pulse oximeter, especially around low readings.

This is a wellness/safety alert system, not a certified medical device.
