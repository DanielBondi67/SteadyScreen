# SteadyScreen

Experimental Android reading stabilization using the phone's IMU. MVP 1's vertical
prototype was physically tested on a Pixel 8 with promising results. MVP 2 extends
that baseline with two-axis compensation, bump detection, adaptive strength,
VSYNC timing, short-horizon prediction, overscan coverage, and a Quick Settings tile.
MVP 2 still needs physical evaluation. Stabilization applies only inside this app.

See [MVP 2 implementation and Pixel 8 validation](docs/mvp2-validation.md) for the
algorithms, all defaults, limitations, exact installation and tile setup, and A/B
procedure. Development rules are in [AGENTS.md](AGENTS.md).

## Build and run

Use Android Studio's Android SDK with Platform 36 and a compatible JDK (verification
used JDK 21). The project targets Android 14+ (minimum SDK 34, target/compile SDK 36).
Keep SDK paths in your local, ignored `local.properties` or development environment.

```bash
./gradlew test assembleDebug lint --offline --no-watch-fs
adb devices -l
```

On a first build, omit `--offline` to download required build dependencies. With the
Pixel 8 connected by USB or Android Studio wireless debugging, copy its identifier:

```bash
PIXEL_SERIAL='PASTE_PIXEL_8_IDENTIFIER_HERE'
adb -s "$PIXEL_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$PIXEL_SERIAL" shell am start -W -n com.steadyscreen/.MainActivity
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Replacement installation with
the same signing key preserves data. Android Studio Run also works. No special
sensor permission, accessibility service, server, account, or network is needed by
the app. See the [wireless connection steps](docs/mvp2-validation.md#exact-pixel-8-installation)
if the phone no longer appears in `adb devices`.

## Reading and controls

The original reading sample and custom reading material remain available. Use
**Reading text** to paste text, **Use text** to apply it, or **Use original sample**
to restore the bundled story. Applied text persists locally (100,000-character
limit); unapplied dialog edits are temporary. Custom paragraphs scroll lazily.

The reader exposes ON/OFF, Manual/Adaptive mode, independent vertical/horizontal
gain, and prediction ON/OFF. **Tune settings** contains all other parameters, profiles,
and **Use vertical Manual comparison**. The latter sets horizontal gain to zero,
Manual mode, and prediction OFF while preserving your vertical tuning. **Reset
defaults** resets tuning while preserving ON/OFF, reading text, and diagnostics.

Diagnostics are independently throttled to 5 Hz and scroll within a bounded panel.
They show relative pitch/horizontal angle, gyro and acceleration XYZ, acceleration
magnitude, bump/rotational/combined shake, adaptive multiplier/effective gains,
raw/final X/Y, sensor rate, render FPS/display Hz, prediction lead/effective horizon,
and base/actual overscan plus visible limits. Hide diagnostics for more reading space.

To add the tile: fully expand Pixel Quick Settings, tap **Edit**, drag **SteadyScreen**
into the active tiles, and exit Edit. It toggles the same persisted enabled state as
the reader and works with the activity closed. **Reader on** does not stabilize
other applications or start background sensors.

## Architecture

```text
AndroidSensorProvider (~200 Hz requested, no batching)
  → StabilizationEngine (pure Kotlin orientation/filter/envelope state)
  → VSYNC frame callback (adaptive gain + bounded quaternion prediction)
  → OverscanGeometry (fixed viewport coverage)
  → Compose graphicsLayer (one transform per frame)
```

The original vertical reference/filter remains intact. Horizontal compensation uses
the relative quaternion's display Y-axis angle with independent gain, direction,
dead zone, and clamp. Linear acceleration detects vibration only; no position is
integrated. Adaptive gain and prediction can be disabled for a vertical baseline
comparison. Roll compensation is not implemented.

Sensors, model processing, and frames are serialized on the main thread. Samples
never publish Compose state or launch per-sample coroutines. Frame state is read in
`graphicsLayer` without recomposing paragraphs. Frame cadence is measured rather
than fixed at 60/120 Hz. Sensors and the frame loop stop when the reader pauses;
resuming establishes a new reference. OFF retains foreground diagnostics while
smoothly returning the transform to zero.

## Local settings and profiles

Settings and applied reading material save automatically using separate app-private
SharedPreferences files. Only changed keys are written with `Editor.apply()`; slider
changes do not rewrite reading text. Enabled state is authoritative in those same
preferences for both the UI and tile. No raw IMU samples, analytics, or network data
are stored. Backup/device transfer remains disabled.

In **Tune settings**, save a named snapshot with notes using **Save as new**, apply it
through **Select profile**, and use **Update profile** to replace/rename it. Live edits
mark the selected profile **(modified)** without changing its saved snapshot. Deleting
a profile asks for confirmation and keeps current tuning. Profiles survive restarts.
Names must be unique (80 characters), notes allow 2,000 characters, and the library
holds up to 100 profiles. Unreadable libraries are reported before any explicit reset.

**Copy JSON** or **Export file** exports current tuning. **Import file** or **Paste JSON**
validates a document and previews its fields; **Save and apply** creates a new profile
without overwriting a same-name profile. ON/OFF, text, and diagnostic visibility are
not profile fields. Android's document picker needs no broad storage permission.

Version 2 exports all 35 tuning fields, units, mode, and prediction state. Imports
reject malformed types, nonfinite values, unsupported versions, invalid invariants,
and values outside the tuning controls. Version 1 profiles remain readable and become
vertical Manual profiles with prediction disabled. The original
[version 1 example](docs/example-tuning-profile.json) is retained for migration testing;
[version 2 defaults](docs/example-mvp2-profile.json) illustrate the new format. Old
app versions cannot read version 2 exports/libraries. Export important profiles
before uninstalling or clearing app data.

## Verification and next step

Baseline: 38 JVM tests per variant and the debug build passed before edits. MVP 2:
75 tests per variant, debug APK build, and lint pass (zero lint errors; six existing
SDK/dependency-update advisories). See [verification details](docs/mvp2-validation.md#baseline-and-verification).

Physical comparison: OFF → vertical Manual → two-axis Manual → Adaptive + prediction.
Keep text/layout/overscan equal. Progress from a desk to controlled hand motion,
walking, public transport, and car travel as a passenger. Never test while driving.
Use the [detailed tuning and validation guide](docs/mvp2-validation.md#physical-ab-procedure).
No system-wide stabilization or other MVP 3 work is implemented.
