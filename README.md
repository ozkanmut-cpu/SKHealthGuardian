# SK Health Guardian — Galaxy Watch8 + Android

Tasker, Gadgetbridge, Health Connect and ntfy are not required in the alarm chain.

## Architecture

- `wear`: Galaxy Watch8 sensor acquisition, local watch alarm, reboot recovery, sensor watchdog, offline reading queue and phone transport.
- `mobile`: receives readings, evaluates alarms, full-screen local alarm, SMS/call dispatch, settings, contacts, history, self-test and watchdog.
- `shared`: thresholds, models and deterministic alarm engine.

## Current alarm defaults

- Normal SpO₂ sampling: every 5 minutes on a strict wall-clock schedule.
- SpO₂ `<80%`: immediate alarm on the first valid reading.
- SpO₂ `80–89%`: second measurement after 2 minutes; two valid low readings confirm the alarm.
- If the confirmation measurement fails technically: reconnect, then one final attempt 1 minute later.
- Heart rate: sampled every 5 minutes.
- Heart rate `>130 bpm`: confirm after 2 minutes; if technical confirmation fails, one final attempt 1 minute later.
- Low-heart-rate alarm exists but is disabled by default.
- Phone stale-data alarm default: 10 minutes without a watch reading.

## Reliability features

- Wear OS health foreground service and `START_STICKY`.
- Watch boot recovery.
- Strict 5-minute wall-clock scheduler without drift.
- Mutex protection against scheduled/manual measurement overlap.
- Sensor reconnect and retry loops.
- Watch-side sensor watchdog: stale valid sensor data triggers reconnect + recovery measurement and local sensor-failure alarm if recovery fails.
- Local watch alarm remains independent of phone connectivity.
- Store-and-forward: up to 500 watch readings are queued locally while the phone is disconnected and replayed after reconnect.
- Stable reading UUIDs prevent replay duplicates in phone history.
- Persistent alert deduplication/cooldown prevents duplicate SMS/calls after reconnect/replay; significant deterioration bypasses cooldown.
- Phone-side stale-data watchdog.
- Measurement history (last 1000 readings).

## Phone UI

- Alarm threshold settings.
- Settings are automatically synchronized to the watch and persisted there.
- Emergency contact editor with per-contact SMS/call flags.
- Emergency contacts are encrypted with an Android Keystore AES-GCM key; legacy plaintext data is migrated automatically.
- Real SMS test button.
- Real call test button.
- Full-screen critical alarm over the lock screen with current SpO₂/HR, alarm reason, remote-alert status, silence, re-measure and primary-contact call controls.
- Phone/watch self-test.
- System-health screen for SMS, call, notification, Bluetooth, full-screen-intent and battery-optimization status plus last watch data/self-test state.
- Persistent masked SMS/call attempt log.

## Samsung Health Sensor SDK

Samsung's proprietary AAR is intentionally **not committed to this repository**.

Download Samsung Health Sensor SDK v1.4.1 from Samsung Developer and place:

`wear/libs/samsung-health-sensor-api.aar`

The Gradle build auto-detects it. Without the AAR, the project compiles against the mock sensor gateway. With the AAR, `src/samsung/.../SamsungSensorGateway.kt` is selected.

For development, Galaxy Watch Health Sensor Service Developer Mode is required unless the application has Samsung partner approval.

## Android permissions / deployment notes

The phone uses `SEND_SMS` and `CALL_PHONE`. `SEND_SMS` is hard-restricted on Android; private sideload deployment must ensure the installer/device grants or allowlists it. The app does not automatically dial emergency-service numbers.

The phone also requests notification, Bluetooth, full-screen-intent and foreground-service capabilities required by the monitoring/alarm path.

## GitHub Actions

`.github/workflows/android-build.yml` builds the project on pushes/PRs and uploads mobile + wear debug APK artifacts. CI uses the mock sensor gateway unless the proprietary Samsung AAR is supplied outside the repository.

## Remaining physical-device validation

The software-side reliability pass is implemented. The remaining critical step requires the actual Galaxy Watch8:

1. Add the official Samsung AAR and compile the real gateway.
2. Resolve any exact SDK signature differences against Samsung v1.4.1 if necessary.
3. Enable Health Sensor Service Developer Mode on Watch8 and install the Wear APK.
4. Verify `SPO2_ON_DEMAND` works repeatedly while the display is off and the health foreground service is running.
5. Run multi-hour 5-minute sampling, reboot, disconnect/reconnect, offline-queue, stale-data, full-screen alarm, SMS and call tests.
6. Compare Watch8 SpO₂ readings against a known-good fingertip pulse oximeter, especially around low readings.

This project is a wellness/safety alert system, not a certified medical device.
