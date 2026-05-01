# GazeBoard — Team Roles & Interface Contract

## Team

**Person A — ML Pipeline**
- Owns: `ml/EyeGazeModel.kt`, `ml/EyeDetector.kt`, `ml/GazeEstimator.kt`, `camera/CameraManager.kt`
- Deliverable by Hour 6: pipeline outputs `GazeResult(quadrant=1..4)` per camera frame
- Key tasks: verify model loads on NPU/CPU, tune eye crop multiplier in EyeDetector

**Person B — App Logic & Prediction**
- Owns: `prediction/`, `calibration/CalibrationEngine.kt`, `state/GazeBoardViewModel.kt`, `state/AppState.kt`
- Deliverable by Hour 6: state machine (Calibrating → QuickPhrases ↔ Spelling → WordSelection) + TriePredictor returning top candidates
- Key tasks: verify calibration persists, test predict() returns correct words

**Person C — UI & Demo**
- Owns: all `ui/` files, `audio/TtsManager.kt`, `MainActivity.kt`, `GazeBoardApplication.kt`, `README.md`
- Deliverable by Hour 6: both screens rendering, NPU badge showing, TTS working
- Key tasks: demo rehearsal, backup video, Devpost submission text

---

## Interface Contract (Person A → B, C)

```kotlin
// GazeEstimator.GazeResult — the data class everyone uses
data class GazeResult(
    val quadrant: Int,       // 0 = no face, 1 = TL, 2 = TR, 3 = BL, 4 = BR
    val confidence: Float,   // 0.0–1.0 (currently always 1.0)
    val inferenceMs: Long,   // NPU inference time for badge display
    val accelerator: String, // "NPU", "CPU" — shown in UI badge
    val rawPitch: Float,     // unsmoothed pitch — used during calibration
    val rawYaw: Float        // unsmoothed yaw — used during calibration
)
```

**Critical path**: Person A's `GazeResult.quadrant` is Person B's input for the state machine and Person C's input for the UI. Agree on this data class — it's already committed.

---

## Integration Checkpoint (Hour 6)

1. Person A: `GazeEstimator.estimate()` outputs `GazeResult(quadrant != 0)` for most frames
2. Person B: `GazeBoardViewModel.onGazeUpdate(result)` drives state transitions
3. Person C: UI updates when `appState` changes
4. End-to-end test: look at each quadrant for 1 second, confirm TTS fires
