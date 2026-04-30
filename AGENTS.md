# GazeBoard — Agentic Coding Workflow

## Role Definitions

### Person A — ML & Inference
**Owns:** `app/src/main/java/com/gazeboard/ml/`
**Deliverable by Hour 6:** `FaceLandmarkModel.kt` running CompiledModel inference on NPU, outputting a verified `FloatArray` of 478×3 landmarks per frame at ≥10 FPS.

**Tasks:**
1. Wire up `CompiledModel.create()` with `Accelerator.NPU`
2. Implement bitmap → FloatBuffer preprocessing (192×192 RGBA normalized to [0,1])
3. Parse output tensor into `FloatArray(478 * 3)` — layout is `[x0,y0,z0, x1,y1,z1, ...]`
4. Verify NPU execution via accelerator query; log and display if falling back to CPU
5. Expose `fun runInference(bitmap: Bitmap): FloatArray?` — returns null if face not detected

**Blocks:** Person B cannot implement gaze math without this output.

---

### Person B — Camera & Gaze Math
**Owns:** `app/src/main/java/com/gazeboard/camera/`, `app/src/main/java/com/gazeboard/calibration/`
**Deliverable by Hour 6:** Calibrated gaze point rendered on screen as a moving cursor, correctly mapping to grid cells.

**Tasks:**
1. Set up CameraX `ImageAnalysis` use case (640×480, RGBA_8888, non-blocking backpressure)
2. Implement bitmap crop → 192×192 resize → call Person A's `runInference()`
3. Implement gaze normalization formula (see CLAUDE.md)
4. Implement EMA smoothing (α = 0.3)
5. Implement 4-point calibration → affine matrix computation
6. Implement `fun applyCalibration(rawGaze: PointF): PointF`
7. Implement `fun mapToCell(calibratedGaze: PointF): Int?` (0–5 for 2×3 grid, null if outside)

**Depends on:** Person A's `FaceLandmarkModel.runInference()` interface.

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

### Contract A→B: Landmark Output
```kotlin
// FaceLandmarkModel.kt — Person A owns this signature
fun runInference(bitmap: Bitmap): FloatArray?
// Returns: FloatArray of size 478 * 3, layout [x0,y0,z0, x1,y1,z1, ...]
// All values normalized to [0,1] relative to 192×192 input
// Returns null if model fails or face confidence below threshold
// Must be called from background thread (ImageAnalysis executor)
```

### Contract B→C: Gaze & Cell Output (via ViewModel StateFlow)
```kotlin
// GazeBoardViewModel.kt — shared ownership
data class GazeState(
    val gazePoint: Offset?,          // calibrated gaze in screen coordinates, null if undetected
    val hoveredCell: Int?,           // 0–5 (row-major: 0=top-left, 5=bottom-right), null if none
    val dwellProgress: Float,        // 0.0f–1.0f progress toward selection threshold
    val inferenceMs: Long,           // last NPU inference time in milliseconds
    val accelerator: String,         // "NPU", "GPU", or "CPU"
    val isBlinking: Boolean          // true if EAR < 0.2
)
```

### Contract B→Calibration Engine
```kotlin
// CalibrationEngine.kt — Person B owns this signature
fun addCalibrationPoint(screenPoint: PointF, gazePoint: PointF)
fun computeAffineTransform(): Boolean  // returns false if < 4 points collected
fun applyCalibration(rawGaze: PointF): PointF
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
- [ ] Inference runs at ≥10 FPS on device (verify via Logcat timing)
- [ ] Output FloatArray has exactly 478*3 = 1434 elements
- [ ] Landmark 468 (left iris center) x,y values change smoothly as face moves
- [ ] `runInference()` returns null gracefully when face is absent

**Person B ✓:**
- [ ] CameraX `ImageAnalysis` delivering frames to inference pipeline
- [ ] Gaze cursor moves in correct direction as eyes move
- [ ] EMA smoothing prevents jitter (cursor doesn't teleport)
- [ ] `mapToCell()` returns correct cell index (manually verified)
- [ ] Calibration screen collects 4 points without crashing

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
