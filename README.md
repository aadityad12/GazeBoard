# GazeBoard

> Eye-gaze-controlled AAC communication board. Free. Offline. Runs on a phone.

Built for the **Qualcomm × LiteRT Developer Hackathon** (April 30–May 1, 2026, Google Sunnyvale) — Track 2: Classical Models, Vision & Audio.

---

## The Problem

Dedicated AAC devices that give people with ALS or locked-in syndrome their voice cost **$8,000–$15,000**. Insurance approval takes 6–18 months. In the meantime, patients wait in silence.

Every Samsung Galaxy S25 Ultra already has the hardware to solve this: a front-facing camera, a Hexagon NPU, and speakers. GazeBoard is the software.

---

## What GazeBoard Does

GazeBoard is a real-time, fully on-device eye-gaze AAC board. The user looks at one of 6 large phrase cells for ~1.5 seconds. The app detects their gaze using MediaPipe FaceMesh iris landmarks processed on the Hexagon NPU via LiteRT's CompiledModel API. When the dwell threshold is reached, the phone speaks the phrase aloud.

**Zero internet. Zero cloud. Zero cost.**

### Demo

[📹 Demo Video](demo_backup.mp4) ← *Recorded during hackathon*

---

## Team

| Name | Email | Role |
|------|-------|------|
| [TEAM_MEMBER_1_NAME] | [TEAM_MEMBER_1_EMAIL] | LiteRT inference, NPU integration |
| [TEAM_MEMBER_2_NAME] | [TEAM_MEMBER_2_EMAIL] | CameraX pipeline, gaze math, calibration |
| [TEAM_MEMBER_3_NAME] | [TEAM_MEMBER_3_EMAIL] | UI/Compose, TTS, docs, demo |

---

## Architecture

```
Front Camera (640×480 @ 15 FPS)
  → CameraX ImageAnalysis
  → Bitmap resize (192×192)
  → face_landmark.tflite on Hexagon NPU   ← LiteRT CompiledModel API
  → FloatArray[1434] (478 landmarks × XYZ)
  → GazeEstimator (iris indices 468/473, normalization, EMA smoothing)
  → CalibrationEngine (4-point affine transform)
  → DwellTimer (1.5s threshold per cell)
  → TextToSpeech output
```

**Inference latency:** ~8ms on NPU (Hexagon DSP, SM8750)
**Frame rate:** 15+ FPS continuous
**Model:** Face landmark .tflite — LiteRT CompiledModel API JIT-compiles for Hexagon NPU on first launch

---

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 1.9+ |
| UI | Jetpack Compose | 2024.x BOM |
| ML Runtime | LiteRT (`com.google.ai.edge.litert:litert`) | 2.1.0 |
| NPU Backend | LiteRT Qualcomm (`com.google.ai.edge.litert:litert-qualcomm`) | 2.1.0 |
| Camera | CameraX | 1.3.x |
| TTS | Android TextToSpeech | framework |
| State | ViewModel + StateFlow | Jetpack |
| minSdk | Android 8.0 (API 26) | |
| Target Device | Samsung Galaxy S25 Ultra (Snapdragon 8 Elite, SM8750) | |

---

## Setup from Scratch

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17+
- ADB (Android SDK Platform Tools)
- Samsung Galaxy S25 Ultra with USB debugging enabled

### Step 1: Clone the repository

```bash
git clone https://github.com/aadityad12/GazeBoard.git
cd GazeBoard
```

### Step 2: Acquire the model

```bash
bash scripts/download_models.sh
```

This tries (in order): LiteRT HuggingFace community models → Qualcomm HuggingFace models → stock MediaPipe FaceMesh. See `models/README.md` for all options including the fastest path (ask on-site engineers at the hackathon).

### Step 3: Build, install, and warm the JIT cache

```bash
bash scripts/install_and_run.sh
```

This builds the APK, installs it, and launches the app. **The first launch JIT-compiles the model for the Hexagon NPU and caches it** (~2–5s one-time delay). All subsequent launches use the cached compiled model. Run this once before the demo.

### Step 4: Verify NPU execution

```bash
adb logcat | grep GazeBoard
```

Expected: `[GazeBoard] Confirmed NPU execution via CompiledModel API`

If you see `WARNING: Running on GPU` on first launch — the JIT cache is still warming. Relaunch the app; it should show NPU.

---

## Using the App

1. **Launch** GazeBoard
2. **Calibration:** Look at each of the 4 red corner dots for 1.5 seconds each (~10 seconds total)
3. **Communicate:** Look at any phrase cell for 1.5 seconds — the phone speaks it aloud
4. The NPU badge in the corner shows real-time inference latency

### Default Phrases

| Position | Phrase |
|----------|--------|
| Top-left | Yes |
| Top-center | No |
| Top-right | Help |
| Bottom-left | Thank you |
| Bottom-center | I need water |
| Bottom-right | Call nurse |

---

## Key Implementation Files

| File | Description |
|------|-------------|
| `ml/FaceLandmarkModel.kt` | CompiledModel API wrapper, NPU inference, bitmap preprocessing |
| `ml/GazeEstimator.kt` | Iris landmark extraction, gaze normalization, EMA smoothing, blink detection |
| `camera/CameraManager.kt` | CameraX ImageAnalysis pipeline |
| `calibration/CalibrationEngine.kt` | 4-point affine calibration |
| `ui/BoardScreen.kt` | 2×3 phrase grid, gaze cursor, dwell progress rings |
| `audio/TtsManager.kt` | Android TTS wrapper with pre-warming |
| `state/GazeBoardViewModel.kt` | Central state machine, dwell timer |

---

## LiteRT CompiledModel API

GazeBoard uses the **LiteRT CompiledModel API** (not the deprecated Interpreter API) with `Accelerator.NPU, Accelerator.GPU`:

```kotlin
val options = CompiledModel.Options.Builder()
    .setAccelerator(Accelerator.NPU, Accelerator.GPU)
    .build()

val model = CompiledModel.create(
    context.assets,
    "face_landmark.tflite",
    options
)
```

LiteRT JIT-compiles the model for the Snapdragon 8 Elite's Hexagon NPU on first launch and caches the result. No AOT compilation or cloud tooling required — just the CompiledModel API and a standard .tflite model.

---

## Privacy & Security

- **No network access.** The app has no internet permission.
- **No data storage.** No images, gaze data, or phrases are saved to disk.
- **Camera frames are processed in memory only** — never written to storage.
- Works fully in airplane mode.

---

## Building a Release APK

```bash
./gradlew :app:assembleRelease

# Sign with your keystore (create one if needed):
keytool -genkey -v -keystore gazeboard.jks -keyalg RSA -keysize 2048 -validity 10000 -alias gazeboard

# Install release build:
adb install app/build/outputs/apk/release/app-release.apk
```

---

## License

```
Copyright 2026 GazeBoard Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

## References

- [LiteRT CompiledModel API](https://ai.google.dev/edge/litert/android/compiled-model)
- [MediaPipe Face Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker)
- [Qualcomm AI Hub](https://aihub.qualcomm.com)
- [litert-samples (Google AI Edge)](https://github.com/google-ai-edge/litert-samples)
- [MediaPipe Face Landmark Map](https://storage.googleapis.com/mediapipe-assets/documentation/mediapipe_face_landmark_fullsize.png)

---

## Acknowledgments

- **Qualcomm** — Snapdragon 8 Elite SoC, Hexagon NPU, AI Hub compilation infrastructure
- **Google** — LiteRT runtime, MediaPipe FaceMesh model, litert-samples reference implementation
- **Samsung** — Galaxy S25 Ultra hardware platform
- **The AAC community** — for making clear why this matters
