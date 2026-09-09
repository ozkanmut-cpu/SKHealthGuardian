# SK Health Guardian — Watch8 + Android

Tasker, Gadgetbridge and ntfy are not required. The project has three modules:

- `wear`: Galaxy Watch8 sensor acquisition, local watch alarm, reboot recovery, phone transport.
- `mobile`: receives readings, applies alarm logic, local phone alarm, SMS and phone call.
- `shared`: thresholds, models and deterministic alarm engine with unit tests.

## Current defaults

- SpO₂ normal acquisition: every 5 minutes.
- SpO₂ `< 80%`: immediate critical alarm on first valid reading.
- SpO₂ `< 90%`: 2 consecutive valid readings before alarm.
- SpO₂ measurement failure: technical retries remain 30 sec and 60 sec. A valid SpO₂ from 80–89% is re-measured after 2 minutes for alarm confirmation; if that confirmation cannot be measured, one final attempt is made 1 minute later. SpO₂ below 80% still alarms immediately on the first valid reading.
- Heart rate: sampled every 5 minutes. The Samsung processed HR tracker is opened only long enough to obtain a valid reading, then its listener is removed; there is no continuous HR monitoring.
- Heart rate `> 130 bpm`: the first high reading triggers confirmation after 2 minutes. If that confirmation cannot be measured, one final attempt is made 1 minute later. Alarm requires 2 valid consecutive high readings; a normal confirmation cancels/resets the sequence.
- Low heart-rate alarm exists but is disabled by default.

All thresholds are centralized in `shared/.../Models.kt` and should later be exposed in the phone settings UI.

## Samsung Health Sensor SDK

Samsung's proprietary AAR is intentionally not redistributed in this ZIP. Download **Samsung Health Sensor SDK v1.4.1** from Samsung Developer and copy:

`wear/libs/samsung-health-sensor-api.aar`

The Gradle build auto-detects the AAR. Without it, the project uses a build-safe mock sensor gateway. With it, `src/samsung/.../SamsungSensorGateway.kt` is compiled.

Galaxy Watch Health Sensor Service Developer Mode must be enabled for development unless the app receives Samsung partner approval.

## Important SDK behavior

Samsung documents `HEART_RATE_CONTINUOUS` as the processed HR tracker and `SPO2_ON_DEMAND` as processed blood oxygen. This project does not leave HR running continuously: every 5 minutes it briefly opens the HR tracker, accepts a valid sample, removes the listener, and then performs the SpO₂ on-demand measurement. Trackers are kept sequential to avoid simultaneous sensor sessions.

The physical Watch8 must still verify that `SPO2_ON_DEMAND` succeeds from our health foreground service with the display off.

## Android 16 permissions

Wear module requests:
- `READ_HEART_RATE`
- `READ_OXYGEN_SATURATION`
- `READ_HEALTH_DATA_IN_BACKGROUND`
- `FOREGROUND_SERVICE_HEALTH`
- `RECEIVE_BOOT_COMPLETED`
- `WAKE_LOCK`

## SMS / phone calls

The phone module uses:
- `SEND_SMS` via `SmsManager`
- `CALL_PHONE` via `TelecomManager.placeCall()`

`SEND_SMS` is a hard-restricted Android permission. For private sideload deployment, the installer/device must allowlist/grant it; normal Play Store distribution has additional policy constraints. The app must never dial emergency numbers automatically.

Emergency contacts are intentionally empty in `ContactStore.kt`; add numbers only through a future settings UI (preferred) or temporarily in code for lab testing.

## Reliability layers already represented

- Watch foreground health service
- `START_STICKY`
- boot receiver
- wake lock only around SpO₂ session
- sensor reconnect loop
- SpO₂ retry loop
- high-HR confirmation (2 min, then 1 min later only if measurement failed)
- low-SpO₂ 80–89% confirmation (2 min, then 1 min later only if measurement failed)
- local watch alarm independent of phone
- phone receives timestamped measurements over Wear Data Layer
- phone-side alarm engine, SMS and call dispatchers

## Next implementation pass after the watch arrives

1. Copy Samsung AAR and compile against the real SDK.
2. Resolve any exact API signature differences from Samsung's v1.4.1 AAR/sample app.
3. Install Wear APK on Watch8 with Health Sensor Service Developer Mode.
4. Validate screen-off 5-minute SpO₂ for at least several hours.
5. Add settings UI, encrypted contact storage, SMS/call test button, persistent history and stale-data watchdog.
6. Validate alert behavior against a known-good fingertip pulse oximeter.

## V0.5 additions
- Phone settings screen for SpO2 critical/low, high HR, and stale-data timeout.
- Emergency contact editor with per-contact SMS/call flags.
- Real SMS test and real call test buttons.
- Phone watchdog foreground service raises DATA_STALE when watch readings stop.
- Persistent local measurement history (last 1000 readings).
- Phone/watch self-test: phone pings watch, watch reconnects sensor and performs a short HR probe, then reports status.
- Boot receiver restarts the phone watchdog and watch monitoring service.

Note: Samsung Health Sensor SDK AAR is intentionally not bundled. Put samsung-health-sensor-api.aar in wear/libs/ for Samsung builds.
