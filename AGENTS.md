# AGENTS.md

# SteadyScreen

SteadyScreen is an Android quality-of-life application that uses the phone's IMU to reduce perceived screen jitter while reading in shaky environments such as cars, buses, trains, or while walking.

The project is experimental. The first goal is not to build a complete product. The first goal is to determine whether IMU-driven counter-motion can measurably improve the reading experience on a physical Android device.

The primary test device is a Google Pixel 8.

---

## 1. Core Product Idea

The phone moves because of vibration or a bump.

The IMU detects the movement.

SteadyScreen estimates the short-term unwanted motion and moves rendered content in the opposite direction.

Conceptually:

```text
physical phone motion
        ↓
IMU sensor data
        ↓
motion estimation
        ↓
filtering / stabilization
        ↓
inverse visual transform
        ↓
more stable perceived text
```

The initial MVP stabilizes only content rendered inside SteadyScreen itself.

System-wide stabilization is explicitly deferred until the in-app algorithm has been proven useful.

---

## 2. MVP 1 Objective

The first MVP must answer:

> Can counter-moving text driven by the Pixel 8 IMU noticeably reduce perceived reading jitter during a bumpy ride?

The MVP is complete only when the application can be installed on a physical Android phone and tested interactively.

For MVP 1, implement only vertical stabilization.

Do not expand scope until the vertical prototype works.

---

## 3. Target Platform

Use:

- Android
- Kotlin
- Jetpack Compose
- Gradle Kotlin DSL
- Android 14+ as the primary runtime target
- Pixel 8 as the primary physical test device

Use modern Android APIs and idiomatic Kotlin.

Prefer Android SDK / Jetpack functionality over unnecessary third-party dependencies.

---

## 4. Required Sensors

Primary sensors:

```kotlin
Sensor.TYPE_GAME_ROTATION_VECTOR
Sensor.TYPE_GYROSCOPE
```

Optional later sensor:

```kotlin
Sensor.TYPE_LINEAR_ACCELERATION
```

For MVP 1, the rotation vector and gyroscope are sufficient.

Use the game rotation vector as the main orientation estimate.

Use the gyroscope for:

- high-frequency angular velocity
- optional short-term prediction
- debugging / instrumentation

Do not manually integrate the gyroscope into an absolute orientation when Android already provides a fused rotation vector.

Do not double-integrate accelerometer values into position in MVP 1.

---

## 5. Stabilization Principle

Maintain:

```text
Qcurrent
Qreference
```

where:

- `Qcurrent` is the current orientation.
- `Qreference` is a slowly changing baseline orientation.

Conceptually:

```text
Qdelta = inverse(Qreference) * Qcurrent
```

Extract the relative pitch component needed for vertical compensation.

Map pitch to inverse vertical translation.

Conceptually:

```text
device pitches upward
        ↓
screen content moves downward
```

The exact sign must be validated on the physical test device.

Do not assume mathematical axis conventions automatically correspond to the desired perceived direction.

---

## 6. Dynamic Reference Frame

The reference orientation must slowly follow the current orientation.

Slow intentional movement should therefore recenter naturally.

Rapid movement should produce compensation.

The desired behavior is approximately:

```text
slow movement / posture change
        ↓
reference follows
        ↓
little sustained compensation

rapid vibration / bump
        ↓
reference cannot follow immediately
        ↓
temporary compensation
```

The implementation should behave like a high-pass or band-pass stabilizer rather than an absolute world lock.

---

## 7. MVP 1 Filtering Pipeline

Use a simple, understandable filtering pipeline.

Conceptually:

```text
rotation vector
    ↓
relative pitch
    ↓
high-pass behavior / moving reference
    ↓
optional low-pass smoothing
    ↓
dead zone
    ↓
gain
    ↓
clamp
    ↓
vertical translation
```

Requirements:

- stationary phone should produce approximately zero transform
- slow movement should recenter toward zero
- rapid pitch jitter should produce opposite visual motion
- tiny sensor noise should not make the text swim
- extreme movement should not send the UI off-screen
- disabling stabilization should smoothly return the transform to zero

Prefer a simple filter that can be tested over a complex Kalman filter.

Do not add complex sensor fusion unless the simpler approach has been shown insufficient.

---

## 8. Configuration

Experimental constants must live in a configuration object, not be scattered through the code.

Create something conceptually similar to:

```kotlin
data class StabilizationConfig(
    val gain: Float,
    val maxVerticalTranslationPx: Float,
    val deadZone: Float,
    val referenceFollowRate: Float,
    val smoothingFactor: Float,
    val overscanScale: Float
)
```

Names and exact representation may differ where appropriate.

Suggested starting values only:

```text
vertical clamp: ±80 px
overscan scale: 1.05–1.10
gain: approximately 0.6
```

These are experimental values.

Do not treat them as final.

---

## 9. Architecture

Keep sensor acquisition, stabilization math, rendering, and UI controls separate.

Suggested structure:

```text
app/src/main/java/com/steadyscreen/

├── sensor/
│   ├── SensorProvider.kt
│   ├── AndroidSensorProvider.kt
│   └── SensorSample.kt
│
├── stabilization/
│   ├── StabilizationEngine.kt
│   ├── StabilizationConfig.kt
│   ├── StabilizationTransform.kt
│   └── MotionFilter.kt
│
├── render/
│   └── StabilizedContent.kt
│
├── ui/
│   ├── MainScreen.kt
│   ├── ReadingTestScreen.kt
│   └── DebugScreen.kt
│
└── MainActivity.kt
```

The exact structure may be simplified if that produces cleaner code.

Do not create unnecessary abstractions merely to match this tree.

The important separation is:

```text
Sensor acquisition
        ↓
Stabilization engine
        ↓
Stabilization transform
        ↓
Compose rendering
```

The stabilization engine must not depend on Compose.

---

## 10. Core Output Model

Use a central transform model.

For example:

```kotlin
data class StabilizationTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotationZ: Float = 0f,
    val confidence: Float = 1f
)
```

MVP 1 only needs to actively use:

```text
translationY
```

Keep the structure extensible enough for later horizontal and rotational stabilization without implementing those features now.

---

## 11. Rendering

Use Jetpack Compose.

Create a reading test containing multiple paragraphs of readable text.

Apply vertical stabilization to the reading content using `graphicsLayer`.

Conceptually:

```kotlin
Modifier.graphicsLayer {
    translationY = transform.translationY
    scaleX = overscanScale
    scaleY = overscanScale
}
```

The overscan exists to reduce visible blank edges while the content moves.

Do not over-engineer edge filling for MVP 1.

---

## 12. MVP UI

The application should contain at least:

### Main / Reading Test

- stabilization ON/OFF
- gain slider
- several paragraphs of text
- live stabilized rendering

### Debug Information

Show at least:

- current relative pitch
- gyroscope X
- gyroscope Y
- gyroscope Z
- raw vertical compensation
- filtered vertical compensation
- approximate sensor update rate

The debug display must not cause unnecessary full-screen recompositions at sensor frequency.

Throttle or structure debug updates appropriately.

---

## 13. Sensor Sampling

Aim for approximately:

```text
100–200 Hz sensor sampling
```

Do not require rates above Android's normal practical high-rate sensor limits for MVP 1.

Rendering should follow the display frame rate rather than attempting to render once per sensor event.

Avoid expensive work and unnecessary allocations in sensor callbacks.

---

## 14. Lifecycle

Sensor listeners must be registered and unregistered correctly.

Do not leave sensors active unnecessarily.

Account for:

- activity lifecycle
- app backgrounding
- screen leaving the reading test
- configuration / orientation changes where applicable

When stabilization becomes unavailable, return smoothly to a zero transform.

---

## 15. Performance

MVP targets:

- responsive sensor processing
- smooth 60 Hz rendering
- 120 Hz compatibility where available
- no network dependency
- no server
- no database
- no persistent sensor logging

Do not prematurely optimize, but avoid obviously wasteful operations in high-frequency paths.

---

## 16. Privacy

MVP 1 must work entirely locally.

Do not add:

- analytics
- telemetry
- cloud services
- accounts
- advertising
- persistent IMU storage

Sensor data should be processed in memory and discarded.

---

## 17. Tests

The stabilization logic must be testable independently of Android UI code.

Add unit tests for at least:

### Stationary input

Expected:

```text
transform ≈ zero
```

### Slow intentional movement

Expected:

```text
temporary compensation
then reference follows
transform returns toward zero
```

### Fast pitch impulse

Expected:

```text
opposing vertical compensation
then smooth decay/recentering
```

### Dead zone

Expected:

```text
tiny input produces zero or negligible transform
```

### Gain

Expected:

```text
changing gain proportionally changes compensation
```

### Clamp

Expected:

```text
output never exceeds configured maximum
```

### Reset

Expected:

```text
engine state returns to neutral
```

Use synthetic deterministic inputs.

Do not rely only on instrumentation tests where plain unit tests are possible.

---

## 18. Physical Validation

Automated tests cannot determine whether the feature actually improves reading.

Physical-device validation is mandatory.

Test progressively:

1. phone stationary on a desk
2. controlled hand pitching
3. walking
4. train / bus
5. bumpy car ride as a passenger

Never perform interactive testing while driving.

Compare stabilization ON against OFF.

Do not claim that the stabilization is successful until it has been physically tested.

---

## 19. Explicit MVP 1 Non-Goals

Do NOT implement any of the following unless explicitly requested:

- horizontal stabilization
- rotational screen compensation
- accelerometer-derived translation
- adaptive shake strength
- camera input
- face tracking
- eye tracking
- machine learning
- MediaProjection
- screen recording
- AccessibilityService
- system-wide stabilization
- Quick Settings tile
- root support
- AOSP modifications
- custom ROM support
- backend
- database
- authentication
- analytics
- cloud synchronization
- complex Kalman filters

Keep MVP 1 deliberately small.

---

## 20. Future Milestones

Only after MVP 1 has been physically evaluated:

### MVP 2

Potential additions:

- horizontal stabilization
- linear-acceleration shake detection
- adaptive stabilization strength
- improved dead-zone tuning
- predictive rendering
- frame timing instrumentation
- Quick Settings control

### MVP 3

Research system-wide stabilization.

First experiment:

```text
AccessibilityService
        ↓
MagnificationController
        ↓
small fullscreen overscan zoom
        ↓
IMU-driven viewport movement
```

Treat system-wide stabilization as research.

Do not assume Android's accessibility magnification API can update with sufficiently low latency.

---

# Development Rules

## 21. General Engineering Rules

1. Read this entire file before editing.
2. Inspect the existing repository before creating files.
3. Prefer the smallest implementation that proves the current hypothesis.
4. Do not silently expand scope.
5. Keep stabilization math independent from Android UI code.
6. Prefer deterministic, testable algorithms.
7. Avoid unnecessary dependencies.
8. Avoid unnecessary abstractions.
9. Keep experimental constants configurable.
10. Comment why non-obvious math exists rather than narrating obvious syntax.
11. Do not replace working code without a concrete reason.
12. Do not claim physical behavior that has not been tested on hardware.
13. Fix compilation and test failures caused by your changes before declaring completion.

---

## 22. Repository Initialization

If the repository contains only a README or otherwise lacks an Android project:

- initialize the Android project in the repository
- use Kotlin
- use Jetpack Compose
- use Gradle Kotlin DSL
- create an appropriate Android `.gitignore` if one does not already exist
- do not delete an existing README without a reason
- preserve existing repository metadata

Do not create a nested Git repository.

The Android project root should be the existing Git repository root unless there is a strong reason otherwise.

---

## 23. Git Workflow

Treat `main` as protected.

For substantial development work:

1. inspect the current branch and repository status
2. ensure the working tree state is understood
3. create an appropriately named feature branch before editing
4. implement the requested work
5. run relevant tests and builds
6. create logical commits for coherent completed changes

For the first MVP, an appropriate branch name is:

```text
feat/mvp1-vertical-stabilizer
```

Use meaningful commit messages, preferably Conventional Commit style.

Examples:

```text
chore(android): initialize Compose project
feat(sensor): add IMU sensor provider
feat(stabilization): add vertical stabilization engine
feat(ui): add stabilized reading test
test(stabilization): add synthetic motion tests
```

Do not create arbitrary micro-commits for every edited file.

Do not combine the entire project into one giant commit when the work naturally separates into coherent stages.

---

## 24. Git Safety Boundaries

You MAY autonomously:

- inspect Git status and history
- create a feature branch
- stage files
- create local commits
- create meaningful commit messages

You MUST NOT autonomously:

- push commits to a remote unless the user explicitly requested or authorized pushing
- merge into `main`
- push directly to `main`
- force-push
- rewrite published history
- delete remote branches
- discard unrelated user changes
- reset or clean the working tree destructively without explicit authorization

If pre-existing unrelated changes are present, preserve them.

Do not silently include unrelated changes in your commits.

---

## 25. Build and Verification

Before declaring the MVP task complete:

1. run unit tests
2. run the appropriate Gradle build
3. run lint if configured and practical
4. fix failures caused by the implementation
5. inspect `git status`
6. ensure intended changes are committed
7. ensure no build outputs or local machine configuration were committed

The repository must not include:

- `local.properties`
- Gradle build output
- IDE caches
- secrets
- SDK paths
- generated temporary files

---

## 26. Completion Report

When finishing a task, report concisely:

- branch name
- commits created
- architecture implemented
- important files added or changed
- tests/build commands run
- whether they passed
- parameters that should be tuned first
- anything that still requires physical Pixel 8 validation
- any known limitation or deliberately deferred work

Do not proceed to later MVPs merely because MVP 1 compiles.

The next milestone must be driven by the physical results of MVP 1.

---

# Definition of Done — MVP 1

MVP 1 is technically complete when:

- the Android project builds
- the application launches
- game rotation vector data is received
- gyroscope data is received
- a reading test exists
- stabilization can be enabled and disabled
- stabilization gain can be adjusted live
- relative pitch produces inverse vertical content translation
- slow movement recenters naturally
- rapid pitch motion produces visible temporary counter-motion
- output is dead-zoned and clamped
- debug information is visible
- stabilization logic has unit tests
- tests pass
- Gradle build passes
- the work is committed on the feature branch
- nothing has been pushed or merged unless explicitly requested

MVP 1 is product-validated only after testing on the Pixel 8 in a real moving environment.
