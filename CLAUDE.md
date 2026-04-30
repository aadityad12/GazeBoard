# GazeBoard — Claude Code Context

## Project Summary

GazeBoard is a real-time, fully on-device eye-gaze-controlled AAC (Augmentative and Alternative Communication) board for people with ALS, locked-in syndrome, or severe motor disabilities. The user looks at one of 6 large phrase cells for ~1.5 seconds; the app detects gaze via a two-stage pipeline — Android FaceDetector crops the eye region, then the **EyeGaze model** (`qualcomm/EyeGaze`) estimates pitch and yaw angles on the Hexagon NPU via LiteRT's CompiledModel API. The resulting gaze angle is mapped to the 6-cell grid and the phrase is spoken via Android TTS. No internet. No cloud. Built for the **Qualcomm × LiteRT Developer Hackathon, April 30–May 1, 2026**.

---

## Judging Rubric

### Stage One — Pass/Fail Eligibility Gates (BOTH must pass)
| Gate | Requirement |
|------|-------------|
| Theme & API Fit | App fits hackathon theme AND runs on Samsung Galaxy S25 Ultra |
| LiteRT Integration | Uses **LiteRT/LiteRT-LM `CompiledModel` API** — NOT the old `Interpreter` API |

**If either gate fails, the submission is disqualified regardless of score.**

### Stage Two — Scored (100 points total)
| Category | Points | What matters |
|----------|--------|--------------|
| Technological Implementation | **40** | Performance (low latency), Efficiency (energy/resource use), Optimization (evidence of model/code optimization for SM8750) |
| Application Use-Case & Innovation | **25** | Problem Solving, Creativity/Uniqueness, User Experience |
| Deployment & Accessibility | **20** | Ease of Install, Usability/Stability during demo |
| Presentation & Documentation | **15** | Clarity of explanation, Code Quality, README/docs |

### Tiebreaker Priority (in order)
1. LiteRT Usage
2. Technological Implementation
3. Use-Case & Innovation
4. Deployment & Accessibility
5. Presentation & Documentation

---

## Key Constraints (DO NOT VIOLATE)

- **CompiledModel API only** — use `CompiledModel.create(context.assets, modelPath, CompiledModel.Options.Builder().setAccelerator(Accelerator.NPU, Accelerator.GPU).build())`. Never use `Interpreter`.
- **NPU accelerator required** — must verify at runtime that execution is on NPU, not CPU fallback. Display accelerator badge in UI.
- **Offline only** — no network calls, no Firebase, no cloud APIs, no analytics.
- **6 cells max** — NOT a keyboard, NOT a sentence builder, NOT a word predictor. Six large phrase cells.
- **No stretch feature creep** — iris_landmark model is stretch goal only; implement only if core works by Hour 8.
- **No MediaPipe framework dependency** — use raw .tflite models only. We do NOT depend on Qualcomm AI Hub accounts or AOT compilation. LiteRT JIT-compiles any .tflite for the Hexagon NPU on first launch and caches the result. Launch the app once before the demo to warm the cache.

---

## Tech Stack & Versions

| Component | Library | Version |
|-----------|---------|---------|
| Language | Kotlin | 1.9+ |
| UI | Jetpack Compose | BOM 2024.x |
| LiteRT Core | `com.google.ai.edge.litert:litert` | 2.1.0 |
| LiteRT Qualcomm NPU | `com.google.ai.edge.litert:litert-qualcomm` | 2.1.0 |
| Camera | CameraX | 1.3.x |
| Face detection | `android.media.FaceDetector` | framework (CPU) |
| TTS | Android TextToSpeech | framework |
| State | ViewModel + StateFlow | Jetpack lifecycle |
| minSdk | 26 | Android 8.0 |
| compileSdk | 35 | |
| Target device | Samsung Galaxy S25 Ultra | Snapdragon 8 Elite (SM8750) |

---

## EyeGaze Model (qualcomm/EyeGaze)

**File:** `app/src/main/assets/eyegaze.tflite`
**Source:** `models/mediapipe_face-tflite-float/eyegaze.tflite`

| Tensor | Name | Shape | Type | Notes |
|--------|------|-------|------|-------|
| Input | image | `[1, 96, 160]` | float32 | Grayscale [0,1], no channel dim |
| Output 0 | heatmaps | `[1, 3, 34, 48, 80]` | float32 | Eye landmark heatmaps (unused) |
| Output 1 | landmarks | `[1, 34, 2]` | float32 | 34 eye landmark XY positions |
| Output 2 | gaze_pitchyaw | `[1, 2]` | float32 | **[pitch, yaw] in radians** |

### Gaze Angles
```
pitch > 0 = looking down,  pitch < 0 = looking up     range: [-0.5, 0.5] rad
yaw   > 0 = looking right, yaw   < 0 = looking left   range: [-0.8, 0.8] rad
```

### EMA Smoothing
```kotlin
smoothedPitch = alpha * rawPitch + (1 - alpha) * smoothedPitch  // alpha = 0.3
smoothedYaw   = alpha * rawYaw   + (1 - alpha) * smoothedYaw
```

### Calibration (pitch/yaw → screen pixels)
```
calibrated = affineMatrix(2×3) * [pitch, yaw, 1]^T
```
4-point corner calibration computes the affine matrix from recorded (pitch,yaw) at each corner.

---

## Fallback Plan (if EyeDetector fails at Hour 8)

If `android.media.FaceDetector` is too unreliable, replace `EyeDetector.kt` with ML Kit face detection (Option B). Same pipeline contract — only `EyeDetector.detectAndCrop()` changes. The EyeGaze NPU inference, CalibrationEngine, and UI are unaffected.

---

## Data Flow Pipeline

```
Front Camera (640×480 @ 15 fps)
  → CameraX ImageAnalysis (ARGB_8888, KEEP_ONLY_LATEST)
  → android.media.FaceDetector → eye midpoint + eye distance   [CPU, ~30ms]
  → Crop eye region → resize 160×96 → grayscale normalize
  → eyegaze.tflite on NPU (CompiledModel)                      [NPU, ~8ms]
  → gaze_pitchyaw [1, 2] → pitch, yaw in radians
  → EMA smoothing (α = 0.3)
  → Calibration affine transform: (pitch, yaw) → screen (x, y)
  → Map to 2×3 grid cell index
  → Dwell timer (1.5s threshold)
  → TTS output (QUEUE_FLUSH)
```

---

## File Structure

```
GazeBoard/
├── CLAUDE.md                          ← You are here
├── AGENTS.md                          ← Agentic workflow definitions
├── README.md                          ← Public-facing repo README
├── docs/
│   ├── PRD.md                         ← Product Requirements Document
│   ├── ARCHITECTURE.md                ← Full technical architecture
│   ├── TIMELINE.md                    ← Hour-by-hour execution plan
│   ├── DEMO-SCRIPT.md                 ← 3-minute demo script
│   └── JUDGING-STRATEGY.md            ← Point-by-point scoring strategy
├── models/
│   └── README.md                      ← Model acquisition instructions
├── scripts/
│   ├── download_models.sh             ← Download face landmark .tflite
│   └── install_and_run.sh             ← Build, install, launch (warms JIT cache)
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/gazeboard/
        │   ├── MainActivity.kt
        │   ├── GazeBoardApplication.kt
        │   ├── ml/
        │   │   ├── EyeGazeModel.kt
        │   │   ├── EyeDetector.kt
        │   │   └── GazeEstimator.kt
        │   ├── camera/
        │   │   └── CameraManager.kt
        │   ├── ui/
        │   │   ├── CalibrationScreen.kt
        │   │   ├── BoardScreen.kt
        │   │   └── components/
        │   │       ├── PhraseCell.kt
        │   │       ├── GazeCursor.kt
        │   │       └── NpuBadge.kt
        │   ├── audio/
        │   │   └── TtsManager.kt
        │   ├── calibration/
        │   │   └── CalibrationEngine.kt
        │   └── state/
        │       ├── GazeBoardViewModel.kt
        │       └── AppState.kt
        └── res/values/strings.xml
```

---

## Build & Run

```bash
# 1. Acquire model (see models/README.md for all options)
bash scripts/download_models.sh

# 2. Build, install, and launch (first launch warms LiteRT JIT cache)
bash scripts/install_and_run.sh

# OR manually:
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.gazeboard/.MainActivity
```

---

## Known Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| CompiledModel API requires AOT model specific to SM8750 | High | Use Qualcomm AI Hub export; have JIT fallback path |
| Iris tracking jittery in low light | Medium | EMA smoothing + wider dwell threshold (2s) |
| Face not detected if phone tilted | Medium | Show "face not detected" overlay, prompt user to center |
| TTS latency causes lag feeling | Low | Pre-warm on launch, QUEUE_FLUSH mode |
| Hour 8 go/no-go: iris unreliable | Medium | Head pose pivot is ready (2-hour implementation) |
| Live demo device crash | Low | Pre-record backup video before demo |
