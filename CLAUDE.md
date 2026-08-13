# GazeBoard contributor context

This file is a compact implementation guide for coding tools and contributors. The [README](README.md) defines current public status, and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) defines the detailed data flow. Historical plans under `docs/` are not implementation evidence.

## Project shape

GazeBoard is an Android prototype that maps front-camera eye gaze to one of four screen quadrants. Quadrant dwell selects a quick phrase or advances a grouped-letter word predictor, then Android text-to-speech speaks the selection.

The current state machine is:

```text
Calibrating -> QuickPhrases -> Spelling <-> WordSelection
       |
       +-> ModelLoadError when NPU model creation fails
```

## Runtime path

```text
CameraX RGBA frame
  -> rotate to upright bitmap
  -> ML Kit face and eye landmarks
  -> 160 x 96 normalized grayscale left-eye crop
  -> EyeGaze TFLite model through LiteRT CompiledModel
  -> pitch and yaw exponential moving average
  -> CalibrationEngine midpoint thresholds
  -> GazeResult quadrant 0 to 4
  -> ViewModel dwell and state transition
  -> Compose UI and Android TextToSpeech
```

## Interface contract

`GazeEstimator.GazeResult` is consumed by `GazeBoardViewModel`:

```kotlin
data class GazeResult(
    val quadrant: Int,
    val confidence: Float,
    val inferenceMs: Long,
    val accelerator: String,
    val rawPitch: Float = 0f,
    val rawYaw: Float = 0f,
    val faceDetectMs: Long = 0L
)
```

`quadrant == 0` means that no usable gaze result was produced. Values `1` through `4` represent top-left, top-right, bottom-left, and bottom-right.

## Important constraints

- `EyeGazeModel` uses LiteRT 2.1.1 `CompiledModel` with `Accelerator.NPU` only. There is no CPU or GPU fallback.
- The Qualcomm v79 runtime is an install-time dynamic feature selected for SM8750 devices. Use `./gradlew :app:installDebug` for device deployment.
- The TFLite model is already committed at `app/src/main/assets/eyegaze.tflite`.
- The application manifest has camera permission and no Internet permission.
- Communication dwell is 1,000 ms, calibration dwell is 1,500 ms, and cooldown is 500 ms.
- Calibration persists two midpoint thresholds through `SharedPreferences`.
- `TriePredictor` performs a linear prefix scan over `words.txt`; it is not a trie implementation or a language model.
- The current Compose screens do not render the CameraX preview or `GazeCursor`.

## Source map

| Area | Files |
| --- | --- |
| Activity and app lifecycle | `MainActivity.kt`, `GazeBoardApplication.kt` |
| Camera | `camera/CameraManager.kt` |
| Gaze pipeline | `ml/EyeDetector.kt`, `ml/EyeGazeModel.kt`, `ml/GazeEstimator.kt` |
| Calibration | `calibration/CalibrationEngine.kt` |
| Prediction | `prediction/WordPredictor.kt`, `prediction/TriePredictor.kt` |
| State and dwell | `state/AppState.kt`, `state/GazeBoardViewModel.kt` |
| UI | `ui/` and `ui/components/` |
| Speech | `audio/TtsManager.kt` |

## Build and checks

The code targets Java 17 bytecode. Use JDK 17 or a compatible newer JDK and Android SDK 35:

```bash
./gradlew :app:assembleDebug
./gradlew :app:bundleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

As of August 13, 2026, debug and release assembly and app-bundle packaging pass, the unit-test task reports `NO-SOURCE`, and full debug lint fails with one manifest error plus warnings. The debug app bundle contains the model asset and all seven Qualcomm runtime libraries. No target-device validation record or project benchmark is checked in. Do not add performance, accuracy, or compatibility claims without reproducible evidence.

The legacy `scripts/download_models.sh` and `scripts/install_and_run.sh` are not the supported setup path in their current form. See the README for the known failures.

## Ownership

Preserve the team roles and interface responsibilities in [AGENTS.md](AGENTS.md). Use Git history to attribute committed work; do not infer ownership from file location alone.
