# GazeBoard — Agentic Coding Workflow

## Role Definitions

### Person A — ML & Inference
**Owns:** `app/src/main/java/com/gazeboard/ml/`
**Deliverable by Hour 6:** `EyeGazeModel.kt` running CompiledModel inference on NPU, `EyeDetector.kt` producing eye crops, delivering verified `(pitch, yaw)` per frame at ≥10 FPS.

**Tasks:**
1. Wire up `CompiledModel.create()` with `Accelerator.NPU, Accelerator.GPU` in `EyeGazeModel.kt`
2. Log all input/output tensor shapes on load to confirm they match `metadata.json`
3. Implement `EyeDetector.detectAndCrop()` using `android.media.FaceDetector`
   - RGB_565 bitmap conversion for FaceDetector
   - Crop → resize to 160×96 → grayscale normalize [0,1]
   - Output: `FloatBuffer[15360]`
4. Allocate all 3 output buffers for `CompiledModel.run()` (heatmaps, landmarks, gaze_pitchyaw)
5. Verify NPU execution via `model.accelerator.name`; update `NpuBadge` with result
6. First launch: accept the ~2–5s JIT compilation delay; subsequent launches use cache

**Blocks:** Person B cannot implement GazeEstimator without the `EyeGazeModel.runInference()` interface.

---

### Person B — Camera & Gaze Math
**Owns:** `app/src/main/java/com/gazeboard/camera/`, `app/src/main/java/com/gazeboard/calibration/`, `app/src/main/java/com/gazeboard/ml/GazeEstimator.kt`
**Deliverable by Hour 6:** Calibrated gaze cursor on screen, correctly mapping to grid cells via pitch/yaw → affine → screen pixels.

**Tasks:**
1. Set up CameraX `ImageAnalysis` (640×480, RGBA_8888, `KEEP_ONLY_LATEST`)
2. In `GazeEstimator.estimate()`: call `EyeDetector.detectAndCrop()` then `EyeGazeModel.runInference()`
3. Implement EMA smoothing for pitch and yaw (α = 0.3, initialized to first sample)
4. Implement `CalibrationEngine`: 4-point (pitch, yaw) → (screenX, screenY) affine mapping
5. Wire calibration into `GazeBoardViewModel.onCalibrationPointCaptured()`
6. Replace the stub linear mapping in `GazeBoardViewModel.onGazeUpdate()` with `calibrationEngine.applyCalibration(pitch, yaw)`

**Depends on:** Person A's `EyeGazeModel.runInference(eyeBuffer: FloatBuffer): GazeAngles?` interface.

---

### Person C — UI, TTS & Docs
**Owns:** `app/src/main/java/com/gazeboard/ui/`, `app/src/main/java/com/gazeboard/audio/`, `README.md`, `docs/`
**Deliverable by Hour 6:** Complete 2×3 board UI composable with dwell timer animation and TTS triggering on selection.

**Tasks:**
1. Implement `BoardScreen.kt` — 2×3 phrase grid, occupies full screen
2. Implement `PhraseCell.kt` — phrase text, dwell progress ring animation, selected state
3. Implement `GazeCursor.kt` — circle drawn at calibrated gaze position via Canvas
4. Implement `NpuBadge.kt` — shows accelerator type + inference latency ms
5. Implement `TtsManager.kt` — pre-warm on app launch, `fun speak(phrase: String)`
6. Implement `CalibrationScreen.kt` — 4-corner targets, captures gaze at each corner
7. Write `README.md` and polish `docs/`

**Depends on:** ViewModel StateFlow (can stub initially), Person B's cell index output.

---

## Interface Contracts (DO NOT CHANGE without team agreement)

### Contract A→B: Eye Gaze Inference
```kotlin
// EyeGazeModel.kt — Person A owns this signature
fun runInference(inputBuffer: FloatBuffer): GazeAngles?
// inputBuffer: FloatBuffer[15360] = grayscale [0,1] at 160×96 (row-major height×width)
// GazeAngles.pitch: Float  — radians, positive=down
// GazeAngles.yaw:   Float  — radians, positive=right
// Returns null if model not loaded or inference fails

// EyeDetector.kt — Person A owns this signature
fun detectAndCrop(bitmap: Bitmap): FloatBuffer?
// Returns FloatBuffer[15360] for EyeGazeModel, or null if no face detected
```

### Contract B→C: Gaze & Cell Output (via ViewModel StateFlow)
```kotlin
// GazeBoardViewModel.kt — shared ownership
data class GazeState(
    val gazePoint: Offset?,      // calibrated gaze in screen pixels; null if no face
    val hoveredCell: Int?,       // 0–5 (row-major 2×3); null if between cells
    val dwellProgress: Float,    // 0.0–1.0
    val inferenceMs: Long,       // EyeGaze NPU inference time in ms
    val accelerator: String,     // "NPU", "GPU", or "CPU"
    val faceDetected: Boolean,
    val rawPitch: Float,         // smoothed pitch for calibration capture
    val rawYaw: Float            // smoothed yaw for calibration capture
)
```

### Contract B→Calibration Engine
```kotlin
// CalibrationEngine.kt — Person B owns this signature
fun addCalibrationPoint(screenPoint: PointF, pitchYaw: PointF)  // x=pitch, y=yaw
fun computeAffineTransform(): Boolean
fun applyCalibration(pitch: Float, yaw: Float): PointF  // returns screen pixel PointF
fun reset()
```

### Contract C→TTS
```kotlin
// TtsManager.kt — Person C owns this signature
fun initialize(context: Context, onReady: () -> Unit)
fun speak(phrase: String)
fun shutdown()
```

---

## Critical Path & Dependencies

```
Hour 0  ─── Person A: CompiledModel loads model file (no-crash baseline)
         ─── Person B: CameraX preview visible on screen
         ─── Person C: Board UI renders with hardcoded phrases

Hour 3  ─── Person A: Inference running, printing FloatArray to Logcat
         ─── Person B: Bitmap preprocessing complete, calling A's API
         ─── Person C: Dwell timer animation working in isolation

Hour 6  ─── INTEGRATION CHECKPOINT ──────────────────────────────────────
            Person A hands off working runInference() to Person B.
            Person B integrates into CameraX pipeline.
            End-to-end test: camera → landmarks → gaze cursor on screen.
            Person C connects ViewModel StateFlow to UI.

Hour 8  ─── GO/NO-GO: Is gaze reliably hitting intended cells?
            YES → continue iris tracking, polish UX
            NO  → Person B pivots to head pose (see CLAUDE.md fallback)
```

---

## Definition of Done — Hour 6 Checklist

**Person A ✓:**
- [ ] `CompiledModel.create()` succeeds with NPU accelerator
- [ ] Logcat shows "EyeGaze: confirmed NPU execution via CompiledModel API"
- [ ] Inference runs at ≥10 FPS (verify via `lastInferenceMs` in Logcat)
- [ ] `EyeDetector.detectAndCrop()` returns non-null FloatBuffer when face is in frame
- [ ] `EyeGazeModel.runInference()` returns non-null `GazeAngles` with pitch/yaw changing as eyes move
- [ ] Pitch changes when looking up/down; yaw changes when looking left/right

**Person B ✓:**
- [ ] CameraX `ImageAnalysis` delivering ARGB_8888 frames to `GazeEstimator`
- [ ] Gaze cursor moves left when looking left (yaw↓), right when looking right (yaw↑)
- [ ] EMA smoothing prevents jitter
- [ ] Calibration screen collects 4 (pitch, yaw) points without crashing
- [ ] `CalibrationEngine.computeAffineTransform()` succeeds (Logcat: "Affine transform computed")

**Person C ✓:**
- [ ] All 6 phrase cells render, fill screen, are readable at arm's length
- [ ] Dwell progress ring animates correctly (0% → 100% over 1.5s)
- [ ] TTS speaks phrase on selection (tested with button tap as stub input)
- [ ] NPU badge displays accelerator type and latency
- [ ] No crashes in 5 minutes of continuous use

---

## Hour 8 Go/No-Go Decision

**Who decides:** All three team members together.

**Test:** Person A + B sit in front of camera and attempt to select each of the 6 cells intentionally. Score = correct selections / total attempts.

**Threshold:** If accuracy < 70% after calibration, switch to head pose.

**Head Pose Pivot checklist:**
- [ ] Replace gaze normalization in `GazeEstimator.kt` with pitch/yaw from landmark geometry
- [ ] Update `CalibrationEngine.kt` to calibrate head angle → cell mapping
- [ ] Update demo narrative (mention head pose, still emphasizes NPU + landmark model)
- [ ] Estimated time: 2 hours

---

## Integration Testing Plan

### Unit Tests (can mock)
- `GazeEstimator`: given landmark FloatArray with known iris positions → expected gaze PointF
- `CalibrationEngine`: given 4 point pairs → affine matrix produces correct output for test input
- `DwellTimer`: given 60 frames at 1 cell → fires selection at 1.5s mark

### Device Integration Tests
1. **NPU execution:** Launch app, check Logcat for `[GazeBoard] Accelerator: NPU`. Fail = crash or CPU fallback.
2. **Landmark accuracy:** Print landmark 468 to Logcat while looking left/right. x value should vary 0.2–0.8.
3. **End-to-end selection:** Look at top-left cell for 2 seconds. TTS should say "Yes".
4. **Calibration persistence:** Calibrate, rotate device slightly, recalibrate. Cells should still be selectable.

---

## Git Workflow

```bash
# Branch naming
feature/person-a-litert-model
feature/person-b-gaze-pipeline
feature/person-c-board-ui

# Merge to main only after Hour 6 integration checkpoint passes
# Tag releases:
git tag v0.1-integration   # Hour 6 checkpoint
git tag v0.2-calibration   # Hour 10 (calibration + polish)
git tag v1.0-demo          # Hour 20 (demo-ready build)

# Commit message format:
# [A] Wire CompiledModel with NPU accelerator
# [B] Add EMA smoothing to gaze pipeline
# [C] Implement dwell progress ring animation
```

---

## Emergency Contacts & Resources

- LiteRT CompiledModel API docs: `https://ai.google.dev/edge/litert/android/compiled-model`
- Qualcomm AI Hub: `https://aihub.qualcomm.com`
- litert-samples repo: `https://github.com/google-ai-edge/litert-samples`
- MediaPipe FaceMesh landmark map: `https://storage.googleapis.com/mediapipe-assets/documentation/mediapipe_face_landmark_fullsize.png`
- ADB device check: `adb devices` (should show S25 Ultra)
