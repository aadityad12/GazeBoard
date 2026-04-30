# GazeBoard

> Eye-gaze-controlled AAC communication board. Free. Offline. Runs on a phone.

Built for the **Qualcomm x LiteRT Developer Hackathon** (April 30-May 1, 2026, Google Sunnyvale) - Track 2: Classical Models, Vision & Audio.

---

## The Problem

Dedicated AAC devices that give people with ALS or locked-in syndrome their voice cost **$8,000-$15,000**. Insurance approval takes 6-18 months. In the meantime, patients wait in silence.

Every Samsung Galaxy S25 Ultra already has the hardware to solve this: a front-facing camera, a Hexagon NPU, and speakers. GazeBoard is the software.

---

## What GazeBoard Does

GazeBoard is a real-time, fully on-device eye-gaze AAC board. The user looks at one of 6 large phrase cells for about 1.5 seconds. The app detects the face with Android's built-in `FaceDetector`, crops the eye region to a `160x96` grayscale tensor, and runs `eyegaze.tflite` through LiteRT's `CompiledModel` API on the Hexagon NPU. The model outputs gaze `(pitch, yaw)` angles, which are smoothed and mapped to screen coordinates for dwell selection. When the dwell threshold is reached, the phone speaks the phrase aloud.

**Zero internet. Zero cloud. Zero cost.**

### Demo

[Demo Video](demo_backup.mp4) <- *Recorded during hackathon*

---

## Team

| Name | Email | Role |
|------|-------|------|
| [TEAM_MEMBER_1_NAME] | [TEAM_MEMBER_1_EMAIL] | LiteRT inference, NPU integration |
| [TEAM_MEMBER_2_NAME] | [TEAM_MEMBER_2_EMAIL] | CameraX pipeline, gaze math, calibration |
| [TEAM_MEMBER_3_NAME] | [TEAM_MEMBER_3_EMAIL] | UI/Compose, TTS, docs, demo |

---

## Architecture

```text
Front Camera (640x480)
  -> CameraX ImageAnalysis (RGBA_8888, KEEP_ONLY_LATEST)
  -> ImageProxy.toBitmap()
  -> EyeDetector
       - convert ARGB_8888 -> RGB_565 for android.media.FaceDetector
       - crop eye region from original frame
       - resize to 160x96
       - grayscale normalize to FloatBuffer[15360]
  -> EyeGazeModel
       - eyegaze.tflite via LiteRT CompiledModel
       - Accelerator.NPU preferred, Accelerator.GPU fallback
       - outputs heatmaps, landmarks, gaze_pitchyaw
  -> GazeEstimator
       - reads pitch/yaw in radians
       - EMA smoothing, alpha = 0.3
  -> CalibrationEngine
       - 4-point pitch/yaw -> screen affine transform
  -> GazeBoardViewModel
       - maps calibrated screen point to a 2x3 cell
       - runs 1.5s dwell timer
  -> TextToSpeech output
```

**Inference latency:** target ~8ms on NPU for EyeGaze inference
**Frame rate:** target 10+ FPS continuous end-to-end
**Model:** `eyegaze.tflite` - LiteRT `CompiledModel` JIT-compiles for Hexagon NPU on first launch and caches the result

---

## Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.0.21 |
| UI | Jetpack Compose | 2024.06.00 BOM |
| ML Runtime | LiteRT (`com.google.ai.edge.litert:litert`) | 2.1.4 |
| NPU Backend | Qualcomm QNN LiteRT delegate (`com.qualcomm.qti:qnn-litert-delegate`) | 2.34.0 |
| Camera | CameraX | 1.3.4 |
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

### Step 2: Confirm the EyeGaze model

The app expects the model at:

```text
app/src/main/assets/eyegaze.tflite
```

See `models/README.md` for tensor shapes, verification, and troubleshooting.

### Step 3: Build, install, and warm the JIT cache

```bash
bash scripts/install_and_run.sh
```

This builds the APK, installs it, and launches the app. **The first launch JIT-compiles the model for the Hexagon NPU and caches it** (about 2-5 seconds one-time delay). All subsequent launches use the cached compiled model. Run this once before the demo.

### Step 4: Verify NPU execution

```bash
adb logcat | grep GazeBoard
```

Expected logs include EyeGaze model load and inference timing. The board's `NpuBadge` should show `NPU` when the Qualcomm delegate accepts the model. If the device falls back to `GPU` or `CPU`, the badge and Logcat should make that visible.

---

## Using the App

1. **Launch** GazeBoard.
2. **Calibration:** Look at each of the 4 red corner dots for about 1.5 seconds each.
3. **Communicate:** Look at any phrase cell for 1.5 seconds; the phone speaks it aloud.
4. The NPU badge in the corner shows accelerator type and recent inference latency.

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
| `ml/EyeGazeModel.kt` | LiteRT `CompiledModel` wrapper for `eyegaze.tflite`; owns `runInference(FloatBuffer): GazeAngles?` |
| `ml/EyeDetector.kt` | Android `FaceDetector` eye crop pipeline; returns `FloatBuffer[15360]` grayscale input |
| `ml/GazeEstimator.kt` | Calls `EyeDetector` + `EyeGazeModel`, then smooths pitch/yaw with EMA |
| `camera/CameraManager.kt` | CameraX `ImageAnalysis` pipeline with RGBA frames and latest-frame backpressure |
| `calibration/CalibrationEngine.kt` | 4-point pitch/yaw to screen affine calibration |
| `state/GazeBoardViewModel.kt` | Central state, cell mapping, dwell timer, calibration handoff |
| `ui/BoardScreen.kt` | 2x3 phrase grid, gaze cursor, dwell progress rings, NPU badge |
| `audio/TtsManager.kt` | Android TTS wrapper with pre-warming |

---

## LiteRT CompiledModel API

GazeBoard uses the **LiteRT CompiledModel API** (not the deprecated Interpreter API) with NPU preferred and GPU fallback:

```kotlin
val model = CompiledModel.create(
    context.assets,
    "eyegaze.tflite",
    CompiledModel.Options(Accelerator.NPU, Accelerator.GPU)
)

val inputs = model.createInputBuffers()
val outputs = model.createOutputBuffers()
inputs[0].writeFloat(eyeCropFloatArray) // 96 * 160 grayscale values
model.run(inputs, outputs)
val pitchYaw = outputs[2].readFloat()    // [pitch, yaw]
```

LiteRT JIT-compiles the model for the Snapdragon 8 Elite's Hexagon NPU on first launch and caches the result. No cloud inference is used.

---

## Current Integration Notes

- `EyeDetector`, `EyeGazeModel`, `GazeEstimator`, `CameraManager`, `CalibrationEngine`, UI, and TTS components are present in the repo.
- The calibration engine math is implemented, but some ViewModel and calibration-screen wiring is still marked with TODOs in code. Documentation should not be read as claiming those TODOs are already fully completed.
- The demo target remains NPU execution; GPU/CPU fallback paths exist so failures are visible rather than silent.

---

## Privacy & Security

- **No network access.** The app has no internet permission.
- **No data storage.** No images, gaze data, or phrases are saved to disk.
- **Camera frames are processed in memory only** and never written to storage.
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

```text
Copyright 2026 GazeBoard Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

## References

- [LiteRT CompiledModel API](https://ai.google.dev/edge/litert/android/compiled-model)
- [Android FaceDetector](https://developer.android.com/reference/android/media/FaceDetector)
- [Qualcomm AI Hub](https://aihub.qualcomm.com)
- [litert-samples (Google AI Edge)](https://github.com/google-ai-edge/litert-samples)

---

## Acknowledgments

- **Qualcomm** - Snapdragon 8 Elite SoC, Hexagon NPU, and QNN LiteRT delegate
- **Google** - LiteRT runtime and CompiledModel API
- **Samsung** - Galaxy S25 Ultra hardware platform
- **The AAC community** - for making clear why this matters
