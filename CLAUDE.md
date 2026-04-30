# GazeBoard — Claude Code Context

## Project Summary

GazeBoard is a real-time, fully on-device eye-gaze-controlled AAC (Augmentative and Alternative Communication) board for people with ALS, locked-in syndrome, or severe motor disabilities. The user looks at one of 6 large phrase cells for ~1.5 seconds; the app detects gaze via MediaPipe FaceMesh iris landmarks from the front camera, selects the phrase, and speaks it aloud via Android TTS. No internet. No cloud. Runs on the Hexagon NPU of the Galaxy S25 Ultra via LiteRT's CompiledModel API. Built for the **Qualcomm × LiteRT Developer Hackathon, April 30–May 1, 2026**.

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

- **CompiledModel API only** — use `CompiledModel.create(context.assets, modelPath, CompiledModel.Options(Accelerator.NPU))`. Never use `Interpreter`.
- **NPU accelerator required** — must verify at runtime that execution is on NPU, not CPU fallback. Display accelerator badge in UI.
- **Offline only** — no network calls, no Firebase, no cloud APIs, no analytics.
- **6 cells max** — NOT a keyboard, NOT a sentence builder, NOT a word predictor. Six large phrase cells.
- **No stretch feature creep** — iris_landmark model is stretch goal only; implement only if core works by Hour 8.

---

## Tech Stack & Versions

| Component | Library | Version |
|-----------|---------|---------|
| Language | Kotlin | 1.9+ |
| UI | Jetpack Compose | BOM 2024.x |
| LiteRT Core | `com.google.ai.edge.litert:litert` | 2.1.0 |
| LiteRT Qualcomm NPU | `com.google.ai.edge.litert:litert-qualcomm` | 2.1.0 |
| Camera | CameraX | 1.3.x |
| TTS | Android TextToSpeech | framework |
| State | ViewModel + StateFlow | Jetpack lifecycle |
| minSdk | 26 | Android 8.0 |
| compileSdk | 35 | |
| Target device | Samsung Galaxy S25 Ultra | Snapdragon 8 Elite (SM8750) |

---

## Landmark Indices (face_landmark 478-point model)

| Index | Description |
|-------|-------------|
| 468 | Left iris center — **primary gaze point** |
| 469–472 | Left iris contour |
| 473 | Right iris center — **primary gaze point** |
| 474–477 | Right iris contour |
| 33 | Left eye outer corner (X normalization) |
| 133 | Left eye inner corner (X normalization) |
| 159 | Left eye upper lid (Y normalization + blink) |
| 145 | Left eye lower lid (Y normalization + blink) |
| 362 | Right eye outer corner (X normalization) |
| 263 | Right eye inner corner (X normalization) |
| 386 | Right eye upper lid (Y normalization + blink) |
| 374 | Right eye lower lid (Y normalization + blink) |

### Gaze Normalization Formula
```
// For left eye:
gazeX = (iris468.x - corner33.x) / (corner133.x - corner33.x)  // 0=left, 1=right
gazeY = (iris468.y - lid159.y)   / (lid145.y   - lid159.y)     // 0=top,  1=bottom

// Average left + right eyes, then apply EMA:
smoothed = alpha * raw + (1 - alpha) * previous   // alpha = 0.3

// Apply calibration affine transform:
calibrated = affineMatrix * [smoothedX, smoothedY, 1]^T
```

### Blink Detection
```
// Eye Aspect Ratio (EAR):
EAR = (lid159.y - lid145.y) / (corner133.x - corner33.x)
// Blink if EAR < 0.2 for ≥ 2 consecutive frames
```

---

## Fallback Plan (Head Pose — activate if iris fails by Hour 8)

If iris tracking consistently maps to wrong cells despite calibration, switch to **head pose** derived from existing landmark output. No new model needed.

Key landmarks for head pose: 1 (nose tip), 33 (left eye corner), 263 (right eye corner), 61 (mouth left), 291 (mouth right), 199 (chin).

Derive pitch (up/down) and yaw (left/right) using simplified solvePnP-equivalent. Map pitch to row (top/middle/bottom), yaw to column (left/right). Same 2×3 grid, same dwell timer, same TTS. 2-hour pivot, not a rebuild.

---

## Data Flow Pipeline

```
Front Camera (640×480 @ 15 fps)
  → ImageAnalysis (CameraX)
  → Bitmap crop + resize (192×192 RGBA float)
  → face_landmark.tflite on NPU (CompiledModel)
  → FloatArray[478 * 3] landmarks
  → Extract iris indices 468, 473
  → Normalize gaze (iris relative to eye corners)
  → EMA smoothing (α = 0.3)
  → Calibration affine transform
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
│   ├── export_models.py               ← AI Hub export script
│   └── push_to_device.sh              ← ADB push script
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/gazeboard/
        │   ├── MainActivity.kt
        │   ├── GazeBoardApplication.kt
        │   ├── ml/
        │   │   ├── FaceLandmarkModel.kt
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
# 1. Acquire compiled model (see models/README.md)
python scripts/export_models.py

# 2. Push model to device
bash scripts/push_to_device.sh

# 3. Open in Android Studio, sync Gradle, run on S25 Ultra
# OR build via CLI:
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
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
