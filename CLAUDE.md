# GazeBoard — Claude Code Context

## Project Summary

GazeBoard is a real-time, fully on-device eye-gaze AAC (Augmentative and Alternative Communication) board for people with ALS, locked-in syndrome, or severe motor disabilities. The user communicates by looking at one of 4 large screen quadrants. The app detects gaze direction via a two-stage pipeline — ML Kit face detection crops the eye region, then the **EyeGaze model** (`qualcomm/EyeGaze`) estimates pitch and yaw angles on the NPU via LiteRT's CompiledModel API. Gaze angles are mapped to quadrants after 4-corner calibration. Two screens: Quick Phrases (Yes/No/Help/More) and Spell Mode (letter groups + T9-style word prediction). Built for the **Qualcomm × LiteRT Developer Hackathon, April 30–May 1, 2026**.

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
| Technological Implementation | **40** | Low latency, energy efficiency, optimization for SM8750, NPU utilization |
| Application Use-Case & Innovation | **25** | Problem solving, creativity, UX quality |
| Deployment & Accessibility | **20** | Ease of install, stability during demo |
| Presentation & Documentation | **15** | Code quality, README, verbal clarity |

### Tiebreaker Priority (in order)
1. LiteRT Usage
2. Technological Implementation
3. Use-Case & Innovation
4. Deployment & Accessibility
5. Presentation & Documentation

---

## Key Constraints (DO NOT VIOLATE)

- **CompiledModel API only** — `CompiledModel.create(context.assets, modelPath, CompiledModel.Options(Accelerator.NPU))`. Never use `Interpreter`. NPU only — no CPU/GPU fallback.
- **No silent fallback** — if NPU fails to load, the app transitions to `AppState.ModelLoadError` and shows an error screen with a Retry button. Do NOT silently fall back to CPU.
- **Show the accelerator badge** — display "LiteRT: NPU · Xms" in the UI. Judges WILL look for this.
- **Offline only** — no network calls, no Firebase, no cloud APIs, no analytics.
- **4 quadrants max** — Quick Phrases screen (Yes/No/Help/More) + Spell Mode (letter groups or word candidates). Nothing else.
- **Word predictor is behind `WordPredictor` interface** — `TriePredictor` is the implementation. Dictionary is `words.txt` (~300 ALS-focused words, not a generic corpus).
- **EyeGaze model is in `app/src/main/assets/eyegaze.tflite`** — do not re-download.
- **ML Kit face detection** — `android.media.FaceDetector` is proven unreliable on S25 Ultra (0% detection rate on rotated sensor frames). ML Kit is the correct choice here.
- **NPU warm-up** — `EyeGazeModel.load()` runs one dummy inference immediately after loading to trigger LiteRT JIT compilation and cache the compiled kernel. This eliminates cold-start latency on demo day.

---

## Tech Stack & Versions

| Component | Library | Version |
|-----------|---------|---------|
| Language | Kotlin | 2.0.21 + `-Xskip-metadata-version-check` |
| UI | Jetpack Compose | BOM 2024.06.00 |
| LiteRT Core | `com.google.ai.edge.litert:litert` | 2.1.1 |
| LiteRT Qualcomm NPU | Dynamic feature module `qualcomm_runtime_v79` | (bundled in `:litert_npu_runtime_libraries`) |
| Camera | CameraX | 1.3.4 |
| Face detection | `com.google.mlkit:face-detection` | 16.1.7 |
| TTS | Android TextToSpeech | framework |
| State | ViewModel + StateFlow | Jetpack lifecycle |
| minSdk | 26 | Android 8.0 |
| compileSdk | 35 | |
| Target device | Samsung Galaxy S25 Ultra | Snapdragon 8 Elite (SM8750) |

---

## EyeGaze Model

**File:** `app/src/main/assets/eyegaze.tflite`
**Source:** `qualcomm/EyeGaze` on HuggingFace

| Tensor | Shape | Notes |
|--------|-------|-------|
| Input | `[1, 96, 160]` | Grayscale [0,1], no channel dim |
| Output 0 | `[1, 3, 34, 48, 80]` | Heatmaps (unused) |
| Output 1 | `[1, 34, 2]` | Landmarks (unused) |
| Output 2 | `[1, 2]` | **[pitch, yaw] in radians** |

```
pitch > 0 = looking down,  pitch < 0 = looking up
yaw   > 0 = looking right, yaw   < 0 = looking left
```

---

## Architecture

```
AppState: Calibrating(step:Int) | QuickPhrases | Spelling | WordSelection | ModelLoadError

Front Camera (640×480 @ 15fps)
  → CameraX ImageAnalysis (RGBA_8888, KEEP_ONLY_LATEST)
  → Rotate frame (imageProxy.imageInfo.rotationDegrees)
  → ML Kit FaceDetector → DetectResult (eye crop + center coords + detect latency) [CPU, ~30ms]
  → eyegaze.tflite via CompiledModel API [NPU only, ~40ms]
  → pitch, yaw in radians
  → EMA smoothing (α=0.7)
  → CalibrationEngine.mapToQuadrant(pitch, yaw) → 1..4
  → GazeResult(quadrant, inferenceMs, accelerator, rawPitch, rawYaw, faceDetectMs)
  → GazeBoardViewModel dwell timer (1.0s threshold, 0.5s cooldown)
  → State machine transition
  → TriePredictor.predict(gestureSequence) → word candidates
  → TTS output
```

---

## File Structure

```
GazeBoard/
├── CLAUDE.md
├── AGENTS.md
├── README.md
├── LICENSE
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── TIMELINE.md
│   ├── DEMO-SCRIPT.md
│   └── JUDGING-STRATEGY.md
├── models/
│   └── README.md
├── scripts/
│   └── install_and_run.sh
└── app/
    └── src/main/
        ├── assets/
        │   ├── eyegaze.tflite
        │   └── words.txt
        └── java/com/gazeboard/
            ├── MainActivity.kt
            ├── GazeBoardApplication.kt
            ├── ml/
            │   ├── EyeGazeModel.kt     — CompiledModel wrapper
            │   ├── EyeDetector.kt      — ML Kit face detection + eye crop
            │   └── GazeEstimator.kt    — Pipeline orchestrator → GazeResult
            ├── prediction/
            │   ├── WordPredictor.kt    — Interface
            │   └── TriePredictor.kt    — Dictionary-based implementation
            ├── calibration/
            │   └── CalibrationEngine.kt — 4-corner → mapToQuadrant()
            ├── camera/
            │   └── CameraManager.kt   — CameraX ImageAnalysis pipeline
            ├── state/
            │   ├── AppState.kt        — Sealed class state machine
            │   └── GazeBoardViewModel.kt
            ├── audio/
            │   └── TtsManager.kt
            └── ui/
                ├── QuickPhrasesScreen.kt  — home screen (Yes/No/Help/More); also defines SentenceBar, FaceIndicator
                ├── SpellScreen.kt         — spell mode (letter groups + word selection); also defines GestureSequenceRow
                ├── CalibrationScreen.kt   — 4-corner calibration with dwell ring
                ├── ModelErrorScreen.kt    — shown when NPU fails to load
                └── components/
                    ├── QuadrantCell.kt    — animated dwell-ring cell
                    ├── NpuBadge.kt        — accelerator + latency overlay badge
                    ├── GazeCursor.kt      — gaze cursor (pixel coordinates)
                    ├── CameraPreviewPip.kt — PiP camera preview with eye overlay
                    └── DebugOverlay.kt    — live debug panel (FPS, latencies, pitch/yaw)
```

---

## Build & Run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
export ADB="$HOME/Library/Android/sdk/platform-tools/adb"
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.gazeboard/.MainActivity
```

---

## Known Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| NPU fails at runtime (`Failed to compile model`) | App shows `ModelErrorScreen` with Retry button — no silent CPU fallback. If NPU is unavailable on device, this is visible. |
| Face not detected if phone tilted | ML Kit handles rotation; frame is rotated upright before inference |
| Gaze jitter | EMA smoothing α=0.7; 1s dwell threshold prevents accidental selection |
| Calibration needed on every demo | CalibrationEngine persists pitchMid/yawMid in SharedPreferences; re-calibrate button on main screen |
| Left-side gaze detection weaker | Known issue — see `docs/GAZE_ISSUES.md` for root cause analysis |
| Demo device crash | Pre-record backup video before demo |
