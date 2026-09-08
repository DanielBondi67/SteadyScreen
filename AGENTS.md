Read `AGENTS.md` completely before making any changes.

MVP 1 of SteadyScreen has already been implemented and physically tested on a Pixel 8. The practical results were promising: the vertical IMU-driven counter-motion produced noticeable real-world stabilization.

We are now implementing MVP 2.

Do not redesign the project from scratch. Treat the existing MVP 1 implementation and its physical behavior as the working baseline. Extend it carefully and preserve the existing vertical stabilization behavior.

## Git workflow

Before editing anything:

1. Inspect:

    * `git status`
    * current branch
    * recent Git history
    * repository structure
    * existing stabilization architecture
    * existing tests
    * existing tuning/configuration values

2. Run the existing tests and debug build before making changes to establish a baseline.

3. Create and switch to:

```text
feat/mvp2-adaptive-stabilization
```

If that branch already exists, inspect its state instead of blindly creating another.

Do not work directly on `main`.

You may autonomously create local commits.

Do NOT:

* push
* merge into `main`
* push directly to `main`
* force-push
* rewrite published history
* delete remote branches
* discard unrelated user changes

I will physically test and review the branch before it is pushed/merged.

---

# MVP 2 objective

Extend SteadyScreen from the successful vertical/manual prototype into a two-dimensional, adaptive, low-latency stabilizer.

Implement exactly these MVP 2 features:

1. horizontal stabilization
2. accelerometer bump detection
3. adaptive stabilization strength
4. predictive rendering
5. overscan zoom suitable for two-axis movement
6. 60/120 Hz VSYNC/frame synchronization
7. Android Quick Settings toggle

Do NOT implement MVP 3/system-wide stabilization.

---

# Phase 0 — Understand and preserve MVP 1

Before adding features:

* inspect the current quaternion/orientation implementation
* understand how `Qcurrent` and `Qreference` are maintained
* understand how pitch becomes vertical screen translation
* identify the current filter, dead zone, gain, clamp, and smoothing behavior
* identify how Compose receives stabilization state
* identify current sensor sampling behavior
* identify current overscan implementation
* run all existing tests
* run the existing debug build

Preserve the physically validated vertical compensation sign and baseline tuning unless a change is required for correctness.

Do not rewrite working stabilization math merely because an alternative looks cleaner.

If a refactor is necessary, add regression coverage first.

---

# Phase 1 — Horizontal stabilization

Extend the existing relative-orientation calculation to produce horizontal stabilization.

Conceptually:

```text
Qdelta = inverse(Qreference) * Qcurrent
```

The existing vertical path uses the appropriate relative pitch component.

Add the corresponding relative horizontal/yaw component and map it to inverse `translationX`.

Requirements:

* horizontal stabilization must use the same dynamic-reference concept as vertical stabilization
* slow horizontal device movement must recenter naturally
* rapid horizontal jitter must produce temporary counter-motion
* horizontal dead zone must exist
* horizontal gain must be independently configurable
* horizontal output must be independently clamped
* horizontal sign must be easy to invert during physical testing
* vertical behavior must remain unchanged
* expose horizontal raw/final values in debug information

Do not assume horizontal gain should equal vertical gain.

Do not add visual roll compensation.

Add deterministic tests for:

* stationary horizontal orientation
* slow horizontal movement
* rapid horizontal impulse
* gain
* dead zone
* clamp

Build and test before proceeding.

---

# Phase 2 — Linear-accelerometer bump detection

Add:

```kotlin
Sensor.TYPE_LINEAR_ACCELERATION
```

Do NOT integrate acceleration into position.

The accelerometer is only for:

* bump detection
* vibration intensity
* adaptive-strength input

Create a simple bump/vibration metric.

A reasonable starting model is:

```text
magnitude = sqrt(ax² + ay² + az²)
```

followed by appropriate:

* noise floor
* normalization
* smoothing
* attack/release or envelope behavior
* clamping

Produce a normalized value such as:

```text
0.0 = calm
1.0 = strong vibration/bump
```

Requirements:

* stationary noise must remain near zero
* short acceleration spikes must react quickly
* the signal must decay smoothly
* sustained vibration must produce a stable elevated value
* extreme samples must remain bounded
* implementation must be independently unit-testable

Add appropriate debug output.

Build and test before proceeding.

---

# Phase 3 — Shake score and adaptive strength

Create a general shake score combining rotational and translational motion.

Conceptually:

```text
rotationalShake = filtered gyroscope magnitude
linearShake     = accelerometer bump/vibration metric

shakeScore =
    gyroWeight * rotationalShake
  + accelWeight * linearShake
```

Normalize/clamp the result to approximately:

```text
0.0 .. 1.0
```

Keep thresholds and weights configurable.

Then implement Adaptive stabilization mode.

Desired behavior:

```text
calm
    → low effective stabilization

moderate vibration
    → medium stabilization

strong vibration
    → stronger stabilization
```

Use smooth attack/release dynamics.

The gain should increase relatively quickly when shaking begins and decay more slowly when motion stops.

Avoid rapid gain pumping.

A reasonable model is:

```text
effectiveVerticalGain =
    baseVerticalGain * adaptiveMultiplier

effectiveHorizontalGain =
    baseHorizontalGain * adaptiveMultiplier
```

Requirements:

* Manual mode must remain available
* Adaptive mode must be user-selectable
* adaptive multiplier must be bounded
* current shake score must be visible in debug UI
* current adaptive multiplier/effective gain must be visible
* adaptive gain must never bypass the existing translation clamps

Add tests for:

* calm state
* sudden vibration onset
* sustained moderate vibration
* strong vibration
* release to calm
* min/max bounds
* finite output
* stable behavior without oscillatory gain changes

Build and test before proceeding.

---

# Phase 4 — Decouple sensors from rendered frames

The MVP should no longer publish visual transforms directly at sensor-event frequency.

Target architecture:

```text
IMU ~100–200 Hz
       ↓
stabilization/model state
       ↓
latest orientation + gyro state
       ↓
VSYNC/frame callback
       ↓
predict transform for frame
       ↓
one published visual transform
       ↓
Compose graphicsLayer
```

Use an Android frame timing mechanism suitable for the project architecture, preferably `Choreographer`/VSYNC-based timing.

Requirements:

* support active 60 Hz rendering
* support active 120 Hz rendering
* do not hard-code 16.67 ms or 8.33 ms as permanent assumptions
* derive/measure actual frame cadence
* tolerate refresh-rate changes
* preferably publish one transform per frame
* avoid multiple unnecessary Compose state publications during a single frame
* sensor processing must remain independent from Compose
* expose approximate render frame rate in debug information

Do not force the display into 120 Hz merely to satisfy the MVP.

The stabilizer must operate correctly at whichever supported rate Android is currently using.

Build and test before proceeding.

---

# Phase 5 — Predictive rendering

Use the latest gyroscope angular velocity to compensate for sensor/render/display latency.

Conceptually:

```text
predictedOrientation =
    currentOrientation
    + angularVelocity * predictionHorizon
```

Prefer quaternion/angular extrapolation if it integrates cleanly with the current quaternion implementation.

A simpler pitch/yaw extrapolation is acceptable if it is isolated, mathematically coherent, and tested.

Requirements:

* prediction can be enabled/disabled live
* prediction horizon is configurable
* prediction horizon is clamped
* prediction must not accumulate long-term drift
* prediction starts from the current fused state rather than from a separately integrated gyro orientation
* stale gyroscope data must disable or reduce prediction
* extreme angular velocities must remain bounded
* prediction should target the frame/display timing rather than blindly assume a single fixed delay

Use approximately:

```text
8–20 ms
```

only as an initial experimental tuning range.

Expose:

* prediction enabled
* configured horizon
* effective horizon if different
* frame rate/timing information

Add tests for:

* zero angular velocity
* constant angular velocity
* prediction disabled
* stale gyro input
* horizon clamp
* extreme angular velocity

Build and test before proceeding.

---

# Phase 6 — Two-axis overscan

The previous vertical stabilizer could rely primarily on vertical overscan.

Now both:

```text
translationX
translationY
```

can expose edges.

Ensure the Compose rendering layer provides enough overscan in both directions.

The simplest acceptable implementation is a configurable fixed scale such as:

```text
~1.05–1.10
```

If the existing architecture makes dynamic overscan simple and stable, it may be implemented.

However, do NOT create distracting zoom pumping.

If dynamic overscan is used:

* update it slowly
* bound it
* never change scale at sensor frequency
* do not directly map shakeScore to rapidly changing scale
* keep a reasonable minimum overscan

Prefer a stable fixed overscan over a clever but visibly unstable implementation.

Expose the overscan scale for debugging/tuning.

Build and test before proceeding.

---

# Phase 7 — Quick Settings tile

Implement a real Android Quick Settings tile with `TileService`.

The tile should toggle the same authoritative stabilization-enabled state used by the app UI.

Architecture should effectively be:

```text
                  shared enabled state
                  /                 \
                 /                   \
        Compose application       TileService
```

Do NOT maintain independent UI/tile booleans.

Use a simple persisted Android-appropriate settings mechanism.

No database is necessary.

Requirements:

* tapping the tile toggles stabilization enabled/disabled
* tile displays active/inactive state correctly
* app UI reflects changes made from the tile
* tile reflects changes made from inside the app
* state survives activity recreation
* tile works even when MainActivity is not currently open
* do not create a permanently running background service just for the tile
* document how the user adds the SteadyScreen tile to Pixel Quick Settings

Remember:

MVP 2 is still an in-app stabilizer.

The Quick Settings tile is control infrastructure for the application and future system-wide work. Do NOT begin system-wide stabilization.

Add tests for shared-state logic where practical.

Use instrumentation/framework tests only where ordinary JVM testing is inappropriate.

Build and test before proceeding.

---

# UI / debug requirements

Preserve the existing reading-test UI.

Add enough controls for physical A/B testing.

At minimum expose:

* stabilization ON/OFF
* Manual / Adaptive mode
* vertical gain
* horizontal gain
* prediction ON/OFF

Expose prediction horizon and overscan scale if this can be done without cluttering the main reading experience; otherwise place those in debug/configuration controls.

Debug information should include at least:

```text
relative pitch
relative horizontal/yaw value

gyroscope X/Y/Z

linear acceleration X/Y/Z or magnitude
bump intensity

rotational shake
combined shake score
adaptive multiplier / effective gain

raw X compensation
raw Y compensation

final translationX
final translationY

sensor update rate
render update rate / frame rate

prediction enabled
prediction horizon

overscan scale

stabilization enabled
Manual/Adaptive mode
```

Do not force the entire screen to recompose at 100–200 Hz for debug values.

Throttle or isolate debug-state publication.

---

# Configuration

Extend the existing `StabilizationConfig` or equivalent.

Do not scatter experimental constants around the codebase.

New configurable concepts should include as appropriate:

* vertical gain
* horizontal gain
* vertical clamp
* horizontal clamp
* adaptive enabled/mode
* adaptive min/max multiplier
* adaptive attack
* adaptive release
* gyroscope shake weight
* accelerometer shake weight
* accelerometer noise floor
* prediction enabled
* prediction horizon
* prediction maximum horizon
* overscan scale

Use the existing configuration naming/style rather than introducing a parallel configuration system.

---

# Important non-goals

Do NOT implement:

* AccessibilityService
* Android magnification stabilization
* arbitrary-app/system-wide stabilization
* MediaProjection
* screen recording
* camera
* face tracking
* eye tracking
* ML
* accelerometer double integration
* inertial absolute position tracking
* root support
* AOSP modifications
* custom ROM functionality
* backend
* accounts
* database
* analytics
* cloud services

Do not begin MVP 3.

---

# Performance requirements

Preserve the low-latency behavior that made MVP 1 promising.

Avoid:

* allocations in hot sensor callbacks where practical
* logging every IMU sample
* starting coroutines per sensor sample
* publishing Compose state per sensor sample
* blocking the main/UI thread
* large rolling collections where an EMA/envelope can achieve the same result

Target:

```text
sensor sampling: ~100–200 Hz
visual transform publishing: once per display frame
display operation: 60 or 120 Hz as active
network: none
raw sensor persistence: none
```

---

# Regression requirements

MVP 1 is a proven baseline.

All existing MVP 1 tests must remain passing.

Specifically verify that after MVP 2:

* vertical stabilization still operates
* slow vertical movement still recenters
* vertical sign has not accidentally changed
* vertical clamp/dead-zone/gain still work
* disabling stabilization remains smooth
* stationary text does not become noticeably more unstable because of prediction/adaptive logic

Add regression tests where necessary.

---

# Final verification

Before declaring MVP 2 technically complete, run the appropriate equivalents of:

```bash
./gradlew test
./gradlew assembleDebug
```

Also run:

```bash
./gradlew lint
```

if lint is configured and practical.

Fix implementation-caused failures before completion.

Inspect:

```bash
git status
git log --oneline --decorate
```

Ensure:

* all intended source changes are committed
* no generated build outputs are committed
* no `local.properties`
* no SDK paths
* no raw sensor logs
* no unrelated files

---

# Commits

Create logical local commits as coherent stages become complete.

A reasonable history could resemble:

```text
feat(stabilization): add horizontal compensation
feat(sensor): add linear acceleration bump detection
feat(stabilization): add adaptive strength control
feat(render): synchronize transforms to display frames
feat(render): add predictive stabilization
feat(render): support two-axis overscan
feat(quicksettings): add stabilization tile
test(stabilization): cover mvp2 motion behavior
```

This is not mandatory grouping.

Follow the actual implementation.

Do not produce meaningless micro-commits.

Do not put the entire MVP into one giant commit if the work naturally separates.

Do not push.

---

# Completion report

When finished, give me:

1. branch name
2. local commits created
3. baseline test/build result before the changes
4. architecture changes
5. important files added/modified
6. exact horizontal stabilization algorithm
7. exact bump-detection algorithm
8. shake-score calculation
9. adaptive-strength calculation
10. attack/release behavior
11. prediction algorithm
12. prediction timing source
13. frame/VSYNC synchronization architecture
14. how 60 Hz and 120 Hz are handled
15. overscan strategy
16. Quick Settings architecture
17. default configuration/tuning values
18. final test commands/results
19. final build results
20. exact steps to install/run the debug APK on my Pixel 8
21. exact steps to add the SteadyScreen Quick Settings tile
22. physical test plan comparing:

    * OFF
    * MVP1-like manual vertical mode
    * two-axis manual mode
    * adaptive + prediction mode
23. which parameter to tune if:

    * horizontal motion is reversed
    * horizontal compensation is excessive
    * stabilization visibly lags
    * prediction overshoots
    * adaptive mode reacts too slowly
    * adaptive mode pumps
    * edges become visible
    * overscan zoom is excessive
24. known limitations
25. explicitly deferred MVP 3 work

Stop after MVP 2.

Do not push or merge anything.
