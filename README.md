# SteadyScreen
An application designed to stabilize your screen during bumpy rides for a focused reading experience.

MVP 1 is an experimental **in-app, vertical-only** reading test for the Google Pixel 8.
It uses the game rotation vector for orientation and the gyroscope for diagnostics.
It works offline, requests no runtime permissions, and does not store sensor samples.
Physical improvement has **not** been validated; building and passing tests cannot establish reading comfort.

## Build and install

Requirements: JDK 17 or 21 (21 used during development), Android SDK Platform 36,
Build Tools 35.0.0, and Android Platform Tools. Android 14 / API 34 is the minimum;
compile and target SDK are 36. Dependency versions are pinned in the Gradle files.
The Gradle 8.13 wrapper verifies its distribution with the published SHA-256 checksum.
Initial dependency/SDK downloads require internet; running the application does not.

Open this repository root in Android Studio and select a compatible Gradle JDK.
Install the SDK packages through SDK Manager. Let Android Studio create ignored
`local.properties`, or set `ANDROID_HOME` to your installed SDK location.

```bash
./gradlew test
./gradlew assembleDebug
./gradlew lint
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Reports: `app/build/reports/tests/` and `app/build/reports/lint-results-debug.html`.

Development verification (2026-09-05): `./gradlew test assembleDebug lint` passed
with JDK 21. All 17 deterministic tests passed in both debug and release variants.
The debug APK's signature was also verified. After launcher/backup resource updates,
`./gradlew assembleDebug lint` passed again. Lint reports no errors; newer-version
advisories remain for the pinned Gradle, Compose, Activity, and Lifecycle versions.
Final checks also included `./gradlew assembleDebug lint --rerun-tasks --no-watch-fs`
and `./gradlew lint --no-watch-fs`. No device launch or live sensor test was performed
during this verification.

On the Pixel 8, enable Developer options by tapping **Settings → About phone → Build
number** seven times. Enable **Settings → System → Developer options → USB debugging**.
Connect by USB, unlock the phone, and accept its computer authorization prompt.
With SDK `platform-tools` on your PATH, run from the repository root:

```bash
adb devices
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n com.steadyscreen/.MainActivity
```

Replace `DEVICE_SERIAL` with the identifier listed with status `device` by `adb devices`.
Alternatively select the Pixel 8 in Android Studio and click Run, or copy the APK to
the phone and install it through Android's package installer. No sensor permission
prompt or special accessibility setting is needed.

## Implementation

```text
AndroidSensorProvider (requested 200 Hz, no batching)
  → StabilizationEngine (pure Kotlin, sensor timestamps)
  → ReadingController (display-frame transform, 5 Hz diagnostics)
  → StabilizedContent (Compose graphicsLayer)
```

Important code is under `app/src/main/java/com/steadyscreen/`:

- `sensor/AndroidSensorProvider.kt`: acquires both sensors, releases listeners,
  and exposes in-memory gyro values in Android's device axes, in radians/second.
- `stabilization/Quaternion.kt` and `StabilizationEngine.kt`: orientation math and filters.
- `stabilization/StabilizationConfig.kt`: all experimental filter/sampling defaults (adjustable live).
- `stabilization/StabilizationTransform.kt`: central output; X and Z rotation stay zero.
- `ui/ReadingController.kt`: sensor freshness, display-frame publication, diagnostics.
- `ui/ReadingTestScreen.kt`: controls, paragraphs, debug panel, lifecycle binding.
- `ui/TuningDialog.kt`: live controls for every configuration value and recreation saver.
- `render/StabilizedContent.kt`: clipped, overscanned reading layer.

Samples, controls, and frame reads run serially on the main thread. Sensor callbacks
perform only a small amount of math and do not write Compose state. Transform state
is read inside `graphicsLayer`, so frames do not recompose or lay out the paragraphs.
Only the debug panel reads the diagnostic state at 5 Hz. Sensors run only while the
reading screen is resumed; navigating away, backgrounding, or destroying the activity
unregisters both listeners. OFF keeps sensors active for comparison/diagnostics while
the reading screen is visible. Controls survive configuration changes; orientation
state starts from a new baseline after a lifecycle restart.

The engine normalizes each game rotation quaternion and maps the display's X axis
for all four display rotations. The reference follows current orientation using
shortest-arc normalized linear interpolation with coefficient `1 - exp(-dt / 0.45)`.
It extracts X-axis pitch from `inverse(Qreference) * Qcurrent`, exponentially smooths
that angle, subtracts a continuous dead zone, converts radians to pixels, applies gain
and direction, then clamps the result. This is a simple approximate band-pass response,
not absolute world locking. The gyroscope is never integrated into orientation.

OFF or missing/stale sensor streams cause exponential decay of the displayed transform
to zero. Both streams must be fresh to compensate. A rotation-vector gap longer than
the timeout, display rotation change, or lifecycle restart establishes a new reference.
Invalid and out-of-order orientation samples are ignored. No prediction is implemented.

Diagnostics show relative pitch in degrees, gyro XYZ in rad/s, raw pitch-to-pixel
compensation before smoothing/dead zone/clamp, actual final frame translation in pixels,
rotation-vector update frequency, enabled state, and stream availability. Raw motion
can be nonzero while OFF; final translation should settle to zero. RV Hz is the observed
orientation rate, not display refresh rate. Gyroscope timestamps are checked separately
for freshness.

## Default tuning

All defaults below are in `StabilizationConfig.kt`. **Tune settings** exposes every value
live, with units, a direction switch, and **Reset defaults**. Gain remains available on
the reading screen too. Settings survive rotation but reset on a fresh launch.
Sampling period changes re-register active sensors; changes while paused take effect
on resume. The sampling control requests 100–200 Hz, and diagnostics stay throttled
to 1–10 Hz. Changing other values preserves the current reference orientation.

Toggle and config values are read during composition before publication to the
controller. This ensures edits in the nested control layout update the engine;
reading state only inside `SideEffect` previously missed that invalidation. See
[Compose side effects](https://developer.android.com/develop/ui/compose/side-effects).

| Parameter | Default | Purpose |
| --- | --- | --- |
| `gain` | 0.6 | Live strength, slider range 0–2 |
| `maxVerticalTranslationPx` | 80 px | Symmetric final clamp |
| `pixelsPerRadian` | 1000 px/rad | Experimental angular-to-screen conversion |
| `compensationDirection` | -1 | Positive relative pitch gives negative screen Y |
| `deadZoneRadians` | 0.0015 rad (≈0.086°) | Suppress small motion after smoothing |
| `referenceTimeConstantSeconds` | 0.45 s | Baseline follow time |
| `smoothingTimeConstantSeconds` | 0.018 s | Noise smoothing; 0 disables smoothing |
| `returnTimeConstantSeconds` | 0.08 s | OFF/unavailable return to neutral |
| `overscanScale` | 1.08 | Constant scale in both ON and OFF |
| `sensorTimeoutSeconds` | 0.25 s | Stream freshness cutoff |
| `sensorSamplingPeriodUs` | 5000 µs | Request 200 Hz, device-dependent |
| `debugIntervalNanos` | 200,000,000 ns | Publish diagnostics at 5 Hz |

Android screen Y is positive downward. **The physical compensation direction must be
checked on the Pixel 8.** Quaternion sign conventions do not establish which direction
is perceptually helpful. Pixel mapping also depends on viewing distance and posture.

| Observation | First adjustment |
| --- | --- |
| Too weak | Increase live gain; if needed increase `pixelsPerRadian`. Check whether output is already clamped. |
| Too strong | Reduce live gain. Reduce the clamp if excursions are uncomfortable. |
| Delayed / trailing | Reduce `smoothingTimeConstantSeconds`; check observed sensor rate. A shorter reference time also shortens the residual tail but weakens compensation. |
| Jittery / swimming at rest | Increase dead zone slightly, then smoothing if needed; smoothing adds lag. |
| Moves in the wrong direction | Use **Tune settings → Reverse direction (+1)** and repeat the comparison. |
| Holds an offset too long | Reduce `referenceTimeConstantSeconds` so posture changes recenter faster. |

## Physical validation

Use the same paragraph, comfortable viewing distance, and similar motion for repeated
ON/OFF trials. Keep gain at 0.6 initially. Overscan remains constant so switching does
not change text size. Do not infer success just because the text visibly moves.

1. **Desk:** wait for “Both sensors active.” Verify nonzero RV Hz and small gyro readings.
   After settling, final Y should be zero or negligible and the text should not swim.
2. **Controlled hand pitch:** gently tilt around the display's horizontal axis. Rapid
   pitch should produce a short vertical response. Hold the new angle; it should
   recenter over roughly 1–2 seconds. Slow movements should give smaller responses.
   Determine which direction improves legibility; reverse the configured direction
   and compare if the default increases apparent motion.
3. **ON/OFF and gain:** switch OFF during visible compensation; it should return to
   neutral over a few tenths of a second. Larger gain should increase motion until
   the configured clamp is reached. Try gain 0 to confirm motion stops. UI controls and diagnostics themselves must remain fixed.
4. **Lifecycle/rotation:** background and resume the app; expect a fresh neutral
   baseline. Rotate portrait/landscape and repeat controlled pitch. Check both streams
   become active again. Change sampling to 10 ms and verify streams resume; rotate
   and verify tuning values remain selected. Reset defaults and verify gain returns
   to 0.6 and overscan to 1.08. A phone without the required sensors should show an unavailable
   status and retain a usable, unstabilized reading test.
5. **Walking, then train/bus, then bumpy car ride as a passenger:** repeat comparable
   ON/OFF trials and note reading ease, ability to hold your place, lag, and discomfort.
   Stop if uncomfortable. Never interactively test while driving.

Record observations manually outside the app if useful: context, gain/direction,
sensor rate, perceived improvement/worsening, and whether output hits its clamp.
No physical validation result is claimed by this repository.

## Tests and limitations

`app/src/test/java/com/steadyscreen/stabilization/StabilizationEngineTest.kt` uses
synthetic quaternions and timestamps to cover stationary input, slow movement,
pitch impulses/recentering, noise, live gain, both clamps, reset, smooth OFF,
stale/unavailable data, quaternion sign equivalence, invalid timestamps/data,
all display rotations, rate consistency, and configurable direction.

These JVM tests do not exercise real sensor delivery, the activity lifecycle on hardware,
display latency, or human reading comfort. Installation/launch and both sensor streams
must still be verified on the physical device. Sampling is requested, not guaranteed;
there is no prediction or measured latency budget. The math assumes small relative
rotations; large combined rotations can mix into extracted pitch, although output is
always clamped. Position-only shaking cannot be estimated from orientation. Overscan
reduces exposed edges but may not cover the full 80 px excursion; clipping and margins
are intentionally simple. Text is English-only, and settings reset after a fresh launch.

No later milestone is implemented. Horizontal compensation, adaptive strength,
accelerometer work, prediction, timing instrumentation, and Quick Settings remain
deferred until MVP 1 is physically evaluated. System-wide stabilization, accessibility,
camera/eye tracking, backend, analytics, and storage are out of scope.

Platform references: [Android sensor behavior and sampling limits](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview),
[AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes),
[Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping).
