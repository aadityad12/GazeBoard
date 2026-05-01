# GazeBoard — Technical Architecture

## System Diagram

```
+--------------------------------------------------------------+
|                   Samsung Galaxy S25 Ultra                   |
|                                                              |
|  Front Camera (640×480, RGBA_8888, ~15fps)                   |
|      |                                                       |
|      v                                                       |
|  CameraX ImageAnalysis                                       |
|      | STRATEGY_KEEP_ONLY_LATEST, background executor        |
|      | Rotate frame (imageProxy.imageInfo.rotationDegrees)   |
|      v                                                       |
|  EyeDetector (ML Kit)                                        |
|      | FaceDetectorOptions: PERFORMANCE_MODE_FAST            |
|      |   LANDMARK_MODE_ALL, minFaceSize=0.15                 |
|      | Crops left eye: interEyeDist × 0.75 region            |
|      | Scale to 160×96, BT.601 grayscale → [0,1]             |
|      | Returns DetectResult (buffer, eyeCenter, detectMs)    |
|      v                                                       |
|  FloatBuffer[15360] (96×160 grayscale)                       |
|      v                                                       |
|  EyeGazeModel (LiteRT CompiledModel — NPU only)              |
|      | CompiledModel.create(assets, "eyegaze.tflite",         |
|      |     CompiledModel.Options(Accelerator.NPU))            |
|      | NPU warm-up at load time (JIT cache)                  |
|      | Output 2: [pitch, yaw] in radians                     |
|      v                                                       |
|  GazeAngles(pitch, yaw)                                      |
|      v                                                       |
|  GazeEstimator                                               |
|      | EMA smoothing α=0.7                                   |
|      | CalibrationEngine.mapToQuadrant(pitch, yaw) → 1..4   |
|      v                                                       |
|  GazeResult(quadrant, inferenceMs, accelerator,              |
|              rawPitch, rawYaw, faceDetectMs)                 |
|      v                                                       |
|  GazeBoardViewModel                                          |
|      | Dwell timer (1.0s threshold, 0.5s cooldown)           |
|      | State machine transitions                             |
|      | TriePredictor.predict(gestureSequence) → candidates   |
|      | TtsManager.speak()                                    |
|      v                                                       |
|  QuickPhrasesScreen / SpellScreen / CalibrationScreen        |
+--------------------------------------------------------------+
```

---

## State Machine

```
                        ┌──────────────────┐
           NPU load     │  ModelLoadError   │
           fails  ───→  │  (Retry button)   │
                        └──────────────────┘

AppStart ──→  Calibrating(step: 0..3)
                  │ (4 corners complete)
                  ▼
             QuickPhrases ◄────────── startRecalibration()
              │       │
       Q1/Q2/Q3   Q4 (MORE)
       speaks    │
       phrase    ▼
              Spelling ◄─── confirmWord()
              │
        2-3 candidates
              │
              ▼
         WordSelection ──→ Q4=BACK ──→ Spelling
              │
         Q1/Q2/Q3
              │
         confirmWord() ──→ Spelling
```

---

## Inference Pipeline — Latency Budget

Target: **≥10 FPS end-to-end** (visible in debug overlay).

| Stage | Target | Implementation |
|-------|--------|----------------|
| Camera → ImageProxy | ~0ms | CameraX hardware pipeline |
| Frame rotation | ~1ms | `Matrix.postRotate()` |
| ML Kit eye detect | ~20–40ms | CPU, `PERFORMANCE_MODE_FAST` |
| Eye crop + resize to 160×96 | ~2ms | `Bitmap.createScaledBitmap()` |
| BT.601 grayscale normalize | ~1ms | CPU pixel loop |
| EyeGaze NPU inference | ~8–15ms | Hexagon NPU via LiteRT `CompiledModel` |
| EMA smoothing | <1ms | CPU arithmetic |
| CalibrationEngine.mapToQuadrant | <1ms | Two comparisons |
| StateFlow emission | <1ms | Kotlin coroutine |

`STRATEGY_KEEP_ONLY_LATEST` drops frames when inference is slower than camera rate, bounding latency.

---

## EyeGaze Model Contract

**File:** `app/src/main/assets/eyegaze.tflite`  
**Source:** `qualcomm/EyeGaze` on HuggingFace

| Tensor | Shape | Meaning |
|--------|-------|---------|
| Input | `FloatBuffer[15360]` | 96×160 grayscale [0,1] eye crop |
| Output 0 | `[1,3,34,48,80]` | Heatmaps (allocated but unused) |
| Output 1 | `[1,34,2]` | Landmarks (allocated but unused) |
| Output 2 | `[1,2]` | **[pitch, yaw] in radians** |

Direction conventions:
```
pitch > 0 → looking down    pitch < 0 → looking up
yaw   > 0 → looking right   yaw   < 0 → looking left
```

---

## CompiledModel API Usage

```kotlin
// EyeGazeModel.kt
val mdl = CompiledModel.create(
    context.assets,
    "eyegaze.tflite",
    CompiledModel.Options(Accelerator.NPU)   // NPU only, no fallback
)
val inputs  = mdl.createInputBuffers()
val outputs = mdl.createOutputBuffers()

// Warm-up (triggers JIT compilation on Hexagon DSP):
inputs[0].writeFloat(FloatArray(INPUT_SIZE) { 0f })
mdl.run(inputs, outputs)

// Per-frame inference:
inputs[0].writeFloat(inputArray)          // float[15360]
mdl.run(inputs, outputs)
val pitchYaw = outputs[2].readFloat()     // [pitch, yaw]
```

If `CompiledModel.create()` throws, `GazeBoardViewModel` catches it and sets `AppState.ModelLoadError`.

---

## Eye Crop Preprocessing

```
ImageProxy (RGBA_8888, 640×480 rotated to portrait)
    │
    v
toBitmap()
    │
    v
ML Kit FaceDetector (Tasks.await)
    │  → FaceLandmark.LEFT_EYE position
    │  → FaceLandmark.RIGHT_EYE position
    │  → inter-eye distance
    │
    v
Crop region: leftEyePos ± (interEyeDist × 0.75 / 2)
    │  (clamped to image bounds)
    │
    v
Bitmap.createScaledBitmap(width=160, height=96)
    │
    v
BT.601 luma: 0.299R + 0.587G + 0.114B, normalized to [0,1]
    │
    v
FloatBuffer[15360] (row-major H×W)
```

**Note**: ML Kit `LEFT_EYE` is the eye on the LEFT side of the (non-mirrored, face-to-face) image. This is the subject's anatomical left eye. The `PreviewView` shows a mirrored (selfie) view; the overlay in `CameraPreviewPip` flips X to compensate.

---

## EMA Smoothing

```kotlin
// GazeEstimator.kt
private val alpha = 0.7f

smoothedPitch = alpha * rawPitch + (1f - alpha) * smoothedPitch
smoothedYaw   = alpha * rawYaw   + (1f - alpha) * smoothedYaw
```

α=0.7 weights the current frame heavily (~2-3 frame time constant at 15fps). This is intentionally responsive to avoid significant lag in gaze tracking. The 1-second dwell threshold absorbs frame-level jitter.

---

## Calibration System

**Protocol**: User looks at 4 screen corners in order: TL → TR → BL → BR. At each corner the app dwells for 1.5 seconds, collecting raw pitch/yaw samples throughout. After all 4 corners are committed, the midpoint thresholds are computed.

**Quadrant mapping** (CalibrationEngine.kt):
```kotlin
pitchMid = average of all 4 corner pitches
yawMid   = average of all 4 corner yaws

isUp   = pitch < pitchMid  →  quadrant 1 (TL) or 2 (TR)
isLeft = yaw   < yawMid    →  quadrant 1 (TL) or 3 (BL)
```

**Persistence**: `pitchMid` and `yawMid` are stored in `SharedPreferences ("gazeboard_calib")`. If calibration data exists at launch, the app skips to `QuickPhrases`. The "↺ Recalibrate" button resets and re-runs calibration.

**Known limitation**: The calibration dots are placed at the screen corners (`margin=80px`), which require more extreme gaze angles than looking at the quadrant centers during actual use. Combined with the left eye's asymmetric abduction/adduction range, left-quadrant detection tends to have a narrower activation margin. See `docs/GAZE_ISSUES.md` for detailed analysis.

---

## Dwell Selection System

```kotlin
// GazeBoardViewModel.kt
private const val DWELL_MS      = 1000L   // selection threshold
private const val CALIB_DWELL_MS = 1500L  // calibration dwell per corner
private const val COOLDOWN_MS   = 500L    // post-selection lockout
```

Logic:
1. Each frame, `onGazeUpdate()` calls `handleDwellGaze(quadrant)`
2. If quadrant changes → reset `dwellStartMs`, show progress = 0
3. If same quadrant → compute `elapsed / DWELL_MS`, update dwell ring
4. If `elapsed >= DWELL_MS` → `selectQuadrant()` → state machine transition
5. Post-selection: 500ms cooldown ignores all gaze input

**Note**: when `quadrant == 0` (no face detected), the visual progress resets to 0 but the timer (`dwellStartMs`) is NOT reset. This is intentional blink tolerance — brief face loss doesn't restart the dwell. See `docs/GAZE_ISSUES.md` for timing analysis.

---

## Debug Mode

A "□ Debug" tap button appears in the bottom-left of every screen. Tapping it toggles `GazeBoardViewModel.debugMode` and shows `DebugOverlay` with:
- FPS (10-frame rolling average)
- NPU inference latency (ms)
- Face detect latency (ms)  
- Total pipeline latency
- Raw pitch/yaw (smoothed)
- Active quadrant
- Face detected indicator
- Accelerator name
- Current AppState

---

## NPU Verification (for judges)

1. `EyeGazeModel.kt` uses `CompiledModel.create()` with `Accelerator.NPU` — satisfies eligibility gate
2. `NpuBadge` shows "LiteRT: NPU · Xms" when running on NPU
3. Logcat: `"EyeGaze model loaded on NPU via CompiledModel API"` on launch
4. Logcat: `"NPU JIT warm-up complete: Xms"` on first launch
5. If NPU fails: `ModelErrorScreen` shown (no silent CPU fallback)
