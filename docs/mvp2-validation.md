# MVP 2 implementation and Pixel 8 validation

MVP 1's vertical stabilization was physically evaluated by the owner on a Pixel 8
and improved reading in shaky conditions. MVP 2 extends that working engine. These
new features need their own physical A/B evaluation; JVM tests cannot establish
reading comfort, horizontal direction, or actual display latency.

## Baseline and verification

Starting point: `f836859` on `main`, including the existing settings/profile work.
Before editing, `./gradlew test assembleDebug --offline --no-watch-fs` passed:
38 tests in each debug/release variant and a successful debug build. The existing
vertical defaults, sign, quaternion reference follow, smoothing, dead zone, gain,
and engine clamp were preserved. A numerical trace was captured from the unchanged
engine and added as `VerticalRegressionTest` before extending the math.

Development branch: `feat/mvp2-adaptive-stabilization`. All development commits are
local; nothing is pushed or merged. The pre-existing untracked `CodeOverview.md`
is not part of this work.

Final verification commands:

```bash
./gradlew test assembleDebug lint --offline --no-watch-fs
```

75 tests pass per variant (150 executions), including all 38 existing tests, the
captured vertical trace, horizontal motion, envelopes, shake/adaptation, quaternion
prediction, 60/120 Hz cadence/transitions, overscan geometry, shared enabled state,
and profile migration. Debug build passes. Lint has zero errors and six advisories
about the unchanged target SDK/dependency versions. The first offline lint attempt
needed an uncached lint dependency; `./gradlew test assembleDebug lint --no-watch-fs`
fetched it and passed. Subsequent offline verification also passes.

## Architecture and important files

```text
AndroidSensorProvider: fused orientation + gyro + optional linear acceleration
  → StabilizationEngine: reference, angle filters, motion envelopes
  → frame callback: shake score, adaptive strength, quaternion prediction
  → fixed viewport overscan limits
  → one Compose transform publication per VSYNC
  → graphicsLayer: translationX/Y and scale; no paragraph recomposition
```

- `stabilization/Quaternion.kt`, `StabilizationEngine.kt`: original vertical math
  plus horizontal extraction and frame-time adaptation/prediction.
- `stabilization/MotionEnvelope.kt`, `AdaptiveStrength.kt`,
  `OrientationPredictor.kt`: pure Kotlin, deterministic motion models.
- `stabilization/StabilizationConfig.kt`: all experimental tuning, including mode.
- `sensor/AndroidSensorProvider.kt`: 5 ms requested sampling, no batching, no logging;
  stops all listeners when the reader pauses. Missing acceleration leaves gyro-based
  shake available and produces an explicit diagnostic status.
- `render/FrameTiming.kt`: cadence measurement and duplicate frame rejection.
- `render/OverscanGeometry.kt`, `StabilizedContent.kt`: viewport coverage and layer rendering.
- `ui/ReadingController.kt`, `ReadingTestScreen.kt`: frame bridge, lifecycle,
  reading controls, and throttled diagnostics; `Mvp2TuningControls.kt` and
  `TuningDialog.kt` expose the full tuning configuration.
- `settings/ReadingSettingsStore.kt`, `ReadingSettings.kt`, `ProfileJson.kt`:
  persisted settings, shared enabled state, and version 2 profiles.
- `quicksettings/StabilizationTileService.kt` and `AndroidManifest.xml`: real,
  system-bound Quick Settings tile with the platform binding permission.

Sensor callbacks, controls, and frame reads stay serialized on the main thread.
Sensor callbacks update plain model state; they never publish Compose state or
create coroutines. The frame loop runs only while the reader is resumed. Display
state is read inside `graphicsLayer`; diagnostics have their own composition scope
and publish every 200 ms by default. No runtime dependency was added.

## Exact motion algorithms

Let `B` be the display quarter-turn basis, `Q = normalize(sensorQuaternion) * B`,
and `R` the moving reference. The existing normalized shortest-arc follow remains:

```text
R ← follow(R, Q, 1 − exp(−dt / referenceTimeConstantSeconds))
D = inverse(R) * Q
pitch = atan2(2(wx + yz), 1 − 2(x² + y²))
horizontal = asin(clamp(2(wy − zx), −1, 1))
```

The horizontal value is the relative display Y-axis angle, not compass heading.
Both angles use the existing exponential smoothing time. For each smoothed angle:
subtract its continuous dead zone (`sign(a) * max(abs(a) − deadZone, 0)`), multiply
by 1000 px/rad, its effective gain and direction, then apply its independent clamp.
Vertical direction remains −1. Horizontal defaults to +1 and has an invert switch.
Slow movement recenters with the same reference; rapid movement produces a temporary
response. `rotationZ` remains zero. OFF rebases ongoing orientation samples and
exponentially returns both outputs to zero with the original 80 ms time constant.

Linear acceleration uses `hypot(hypot(ax, ay), az)` for an overflow-resistant norm.
The normalized target is `clamp((magnitude − noiseFloor) / (fullScale − noiseFloor),
0, 1)`. The envelope follows with `1 − exp(−dt / tau)`: 15 ms attack, 250 ms release.
Gyroscope magnitude uses the same envelope with its own noise floor/full-scale
thresholds. Invalid/out-of-order samples are ignored. Missing streams decay after
the 250 ms freshness timeout. There is no acceleration integration or position estimate.

```text
shakeScore = clamp(0.6 * rotationalShake + 0.4 * bumpIntensity, 0, 1)
adaptiveTarget = minimum + shakeScore * (maximum − minimum)
```

The adaptive multiplier follows its target using a second envelope: 80 ms attack
and 600 ms release. Both time constants are configurable. Manual mode returns
multiplier 1 exactly; Adaptive defaults to 0.25–1.5. Effective gains are base gain
multiplied by that value. Stronger adaptation can never bypass the translation clamps.
Weights need not sum to one; the combined result is clamped. With default weights,
a saturated gyro-only signal contributes 0.6, an acceleration-only signal 0.4.

## Prediction and frame timing

Compose's Android frame clock is VSYNC driven. `withFrameNanos` supplies a logical
frame timestamp to `FrameTiming`; duplicate/reversed timestamps are ignored. Actual
frame intervals are measured with a 100 ms diagnostic EMA, supporting 60 Hz, 120 Hz,
and transitions without selecting a refresh rate or hard-coding a frame duration.
Diagnostics distinguish observed render FPS from Android's reported display Hz.

The frame-clock epoch is not assumed equal to sensor time. Cadence uses differences
between frame timestamps. Sensor age and engine time use `elapsedRealtimeNanos`,
matching `SensorEvent.timestamp`. See Android's
[frame clock contract](https://developer.android.com/reference/kotlin/androidx/compose/runtime/MonotonicFrameClock).

Prediction starts from the latest fused orientation for every frame:

```text
lead = min(measuredFramePeriod, configuredPredictionLead)
       (configured lead alone until cadence is available)
horizon = clamp(fusedSampleAge + lead, 0, maximumPredictionHorizon)
Qpredicted = Qcurrent * inverse(B) * gyroDeltaQuaternion(horizon) * B
```

The angular velocity norm is limited to 4 rad/s by default, with component limiting
before the norm to keep even extreme finite inputs bounded. Gyro noise below the
configured floor produces no prediction. A gyro older than 50 ms, a future timestamp,
or stale orientation disables prediction. Default lead is 12 ms; total horizon is
capped at 20 ms. At 120 Hz, the measured period can shorten the lead to about 8.3 ms.

The relative angular difference between predicted and current fused orientation is
added to the existing smoothed angles before the dead zones/gains/clamps. Pitch
increments wrap across ±π. Predicted values never enter the reference or sensor
filter. There is no accumulated gyro orientation and no long-term prediction drift.
This is an estimate of presentation lead, not measured photon latency or an Android
presentation deadline. Turning prediction off restores the original filtered path.

## Overscan

Zoom is fixed for a given reader viewport and tuning:

```text
neededScale = max(baseScale, 1 + 2*horizontalClamp/width, 1 + 2*verticalClamp/height)
actualScale = min(neededScale, max(baseScale, maximumOverscanScale))
visibleLimitX = min(horizontalClamp, width*(actualScale − 1)/2)
visibleLimitY = min(verticalClamp, height*(actualScale − 1)/2)
```

Default base is the existing 1.08×; maximum is 1.30×. A 1080×1200 px reader needs
about 1.133× to cover the full ±60/±80 px clamps. If a small reader viewport reaches
the zoom cap, visible translation is further limited to available overscan. The
engine's filter/tuning remains unchanged; diagnostics show final visible translation
and these limits. This geometry correction is the deliberate difference from MVP 1,
whose 1.08× scale could expose edges at its full clamp. No shake sample changes zoom.
Changing orientation, opening diagnostics, or changing clamps can change the viewport
or its fixed scale. Keep layout and overscan settings equal during A/B trials.

## Defaults

All values persist and are editable in the reading UI or **Tune settings**.

| Setting | Default |
| --- | --- |
| Mode / prediction | Manual / OFF |
| Vertical / horizontal gain | 0.6 / 0.4 |
| Vertical / horizontal direction | −1 / +1 |
| Vertical / horizontal clamp | ±80 / ±60 px |
| Vertical / horizontal dead zone | 0.0015 / 0.0015 rad |
| Pixel conversion | 1000 px/rad |
| Reference / smoothing / OFF return | 0.45 / 0.018 / 0.08 s |
| Acceleration noise / full scale | 0.15 / 3.0 m/s² |
| Gyro noise / full scale | 0.02 / 1.5 rad/s |
| Motion envelope attack / release | 0.015 / 0.25 s |
| Gyro / acceleration weight | 0.6 / 0.4 |
| Adaptive minimum / maximum | 0.25 / 1.5 |
| Adaptive attack / release | 0.08 / 0.6 s |
| Prediction lead / total maximum | 12 / 20 ms |
| Prediction gyro freshness / speed cap | 50 ms / 4 rad/s |
| Base / maximum overscan | 1.08 / 1.30× |
| Sensor freshness / sampling request | 250 ms / 5000 µs (200 Hz) |
| Diagnostic interval | 200 ms (5 Hz) |

Existing persisted vertical settings load unchanged; missing MVP 2 keys receive
defaults. Version 1 profiles remain readable and become vertical Manual configurations
(horizontal gain 0, prediction OFF). Version 2 exports include all 35 configuration
fields, typed mode/boolean values, and units; old app versions cannot read version 2.
Profile selection, named snapshots, local text, and export/import remain available.

## Exact Pixel 8 installation

From the repository root, use Android Studio's configured SDK tools or put
`<Android SDK>/platform-tools` on your shell PATH. Then:

```bash
git switch feat/mvp2-adaptive-stabilization
./gradlew test assembleDebug lint --offline --no-watch-fs
adb devices -l
```

Copy the Pixel 8 identifier with status `device`, then replace the placeholder:

```bash
PIXEL_SERIAL='PASTE_PIXEL_8_IDENTIFIER_HERE'
adb -s "$PIXEL_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$PIXEL_SERIAL" shell am start -W -n com.steadyscreen/.MainActivity
```

`install -r` replaces the debug app and preserves its settings, text, and profiles
when signed with the same debug key. Expect `Success` and launch `Status: ok`.
On the current development machine the adb executable is in the Android SDK's
`platform-tools` directory; use Android Studio's SDK Location to find it. The
wireless device identifier can change, so read it again instead of reusing an old one.

If wireless discovery has disconnected: phone **Settings → System → Developer
options → Wireless debugging** ON, and keep the development machine and phone on
the same network. In Android Studio choose **Pair Devices Using Wi-Fi**, then use
the QR code or pairing code. Alternatively:

```bash
adb pair PHONE_IP:PAIRING_PORT
# Enter the code shown under “Pair device with pairing code”.
adb connect PHONE_IP:DEBUGGING_PORT
adb devices -l
```

The pairing port and debugging port are different; use each value from the relevant
phone screen. Then repeat installation. No sensor permission prompt or accessibility
permission is required. Instructions follow Android's
[wireless ADB workflow](https://developer.android.com/tools/adb#wireless).

## Add the Quick Settings tile on Pixel 8

1. Install and open SteadyScreen once.
2. Swipe down from the top twice to fully expand Quick Settings.
3. Tap the **Edit** pencil (placement depends on Android version).
4. Find **SteadyScreen** among the available app tiles; drag it into the active tiles.
5. Leave Edit and tap the tile. It shows **Reader on** or **Reader off**.
6. Return to SteadyScreen and confirm its ON/OFF switch matches. Change the switch,
   reopen Quick Settings, and confirm the tile follows.
7. Go Home, tap the tile with no reader open, then reopen the reader. Repeat after
   activity recreation, removing it from Recents, and a phone restart.

The tile uses normal (non-active) Android tile listening: each panel opening reads
persisted state, and a preference listener updates it while it is listening. The
app observes the same `reading_settings.enabled` string in the same process.
Toggling writes only that key; tuning edits preserve the latest enabled value even
if their UI snapshot predates a tile click. There is no independent tile boolean,
database, permanent service, or background sensor acquisition. See Android's
[Quick Settings tile lifecycle](https://developer.android.com/develop/ui/views/quicksettings-tiles).
The tile controls this app's reader only; other apps are unaffected.

## Physical A/B procedure

Save the current validated tuning as a profile first. Keep the same text, viewing
distance, reader layout, clamps, and overscan across each trial. Hide diagnostics
after checking streams to give the reader more space. Compare these four setups:

| Trial | Settings |
| --- | --- |
| OFF | Stabilization OFF; retain the same overscan/layout |
| MVP1-like vertical | ON, Manual, horizontal gain 0, prediction OFF; retain validated vertical tuning |
| Two-axis Manual | ON, Manual, horizontal gain 0.4 initially, prediction OFF |
| Adaptive + prediction | ON, Adaptive, horizontal gain 0.4, prediction ON; start at documented defaults |

**Tune settings → Use vertical Manual comparison** selects the second setup without
resetting the validated vertical tuning. Change horizontal gain back for the last
two setups. Save separate named profiles for reproducible comparisons.

Progress through desk, controlled hand pitch/yaw, walking, train/bus, then a bumpy
car ride **as a passenger**. Never perform interactive tests while driving. At each
stage alternate the four trials several times (vary order) for 30–60 seconds each.
Record reading ease, line loss, swimming, lag, overshoot, and discomfort. Stop if
uncomfortable. Visible content motion alone is not success.

At the desk, final X/Y and bump/shake should settle near zero; adaptive gain should
settle near its minimum. Controlled rapid pitch should retain the validated vertical
sign. Controlled yaw should give temporary horizontal response, then recenter when
held. Validate horizontal direction separately in portrait and landscape. Test OFF
while moving and confirm a smooth return. Verify clamps and visible overscan limits.

Watch RV Hz, render FPS, and display Hz during active movement. Verify operation at
60 Hz with **Settings → Display & touch → Smooth Display** disabled and at the
active higher rate when enabled (names may vary). Let Android select its active
rate; no app code forces 120 Hz. Check a refresh transition, background/resume,
rotation, tile toggles, and persistence. A displayed FPS estimate is not evidence
of low sensor-to-photon latency; judge lag physically or use separate frame traces.

## First tuning adjustment by symptom

| Observation | First adjustment |
| --- | --- |
| Horizontal direction wrong | Toggle **Invert horizontal direction (−1)** and repeat controlled yaw |
| Horizontal motion excessive | Reduce horizontal gain; then its clamp if needed |
| Compensation lags | Check sensor/render rates; reduce smoothing slightly or enable/increase prediction lead in small steps |
| Prediction overshoots | Reduce prediction lead/maximum horizon or angular-speed limit; compare prediction OFF |
| Adaptive onset too slow | Reduce adaptive attack time; then motion-envelope attack if the shake score itself is delayed |
| Adaptive gain pumps | Increase adaptive release and/or motion release; narrow the adaptive min/max range |
| Screen edges appear | Check actual scale/visible limits and viewport after resizing; increase zoom cap cautiously or reduce clamps; report a coverage defect if limits are obeyed |
| Overscan zoom excessive | Reduce clamps first, then maximum zoom; lowering base alone cannot reduce geometry-required zoom |
| Text swims at rest | Increase relevant dead zone/noise floor; compare prediction OFF |

## Limitations and deferred work

MVP 2 has not been physically validated by this implementation session. Real tile
binding, Android lifecycle, sensor delivery, 60/120 Hz presentation and reading
comfort still require the checks above. The math approximates small relative
rotations; large combined rotations can couple the angles. Linear-only movement
changes adaptive intensity, not a position estimate. Requested sensor rate is not
guaranteed. Gyro-only operation gives a lower default combined score. Prediction
uses constant angular velocity over a short horizon; sudden reversals can overshoot.
Small viewports may reduce visible compensation to keep zoom bounded. In-app reading
remains English-only. No raw sensor data is persisted.

MVP 3 remains deferred: no system-wide/arbitrary-app stabilization, accessibility
or magnification service, MediaProjection, roll compensation, camera/face/eye tracking,
ML, inertial position tracking, root/AOSP changes, backend, cloud, accounts, analytics,
or database. Review and physically evaluate this branch before pushing or merging.
