# GazeBoard - Technical Architecture

## System Diagram

```text
+-----------------------------------------------------------------+
|                    Samsung Galaxy S25 Ultra                     |
|                                                                 |
|  Front Camera                                                   |
|      | 640x480 RGBA_8888 frames                                 |
|      v                                                          |
|  CameraX ImageAnalysis                                          |
|      | STRATEGY_KEEP_ONLY_LATEST, background executor           |
|      v                                                          |
|  EyeDetector                                                    |
|      | ARGB_8888 -> RGB_565 copy for android.media.FaceDetector |
|      | face midpoint + eye distance                             |
|      | crop eye region from original frame                      |
|      | resize to 160x96                                         |
|      | grayscale normalize [0,1]                                |
|      v                                                          |
|  FloatBuffer[15360]                                             |
|      v                                                          |
|  EyeGazeModel                                                   |
|      | eyegaze.tflite via LiteRT CompiledModel                  |
|      | Accelerator.NPU preferred, Accelerator.GPU fallback      |
|      | outputs: heatmaps, landmarks, gaze_pitchyaw              |
|      v                                                          |
|  GazeAngles(pitch, yaw) in radians                              |
|      v                                                          |
|  GazeEstimator                                                  |
|      | EMA smoothing, alpha = 0.3                               |
|      v                                                          |
|  CalibrationEngine                                              |
|      | 4-point pitch/yaw -> screen affine transform             |
|      v                                                          |
|  GazeBoardViewModel                                             |
|      | map screen point -> 2x3 cell                             |
|      | dwell timer, StateFlow<GazeState>                        |
|      v                                                          |
|  BoardScreen + GazeCursor + NpuBadge + TtsManager               |
+-----------------------------------------------------------------+
```

---

## Inference Pipeline - Latency Budget

Target: **10+ FPS end-to-end** with bounded latency.

| Stage | Target | Notes |
|-------|--------|-------|
| Camera -> ImageProxy | ~0ms | Hardware pipeline |
| FaceDetector eye locate | ~20-40ms | CPU, `android.media.FaceDetector` |
| Eye crop + resize to 160x96 | ~2ms | CPU |
| Grayscale normalize | ~1ms | CPU |
| EyeGaze inference | ~8ms target | Hexagon NPU via LiteRT `CompiledModel` |
| EMA smoothing | <1ms | CPU arithmetic |
| Calibration affine transform | <1ms | 2x3 matrix multiply |
| ViewModel StateFlow emission | <1ms | UI state update |

If EyeGaze inference falls back to GPU or CPU, the `NpuBadge` should make that visible. `FaceDetector` itself is CPU-bound by design.

---

## EyeGaze Model Contract

```kotlin
// EyeGazeModel.kt
fun runInference(inputBuffer: FloatBuffer): GazeAngles?

data class GazeAngles(
    val pitch: Float, // radians, positive = down
    val yaw: Float    // radians, positive = right
)
```

Input comes from `EyeDetector.detectAndCrop()`:

```kotlin
// EyeDetector.kt
fun detectAndCrop(bitmap: Bitmap): FloatBuffer?
```

Tensor contract:

| Tensor | Shape | Meaning |
|--------|-------|---------|
| Input | `FloatBuffer[15360]` | `96x160` grayscale `[0,1]` eye crop |
| Output 0 | `[1, 3, 34, 48, 80]` | heatmaps |
| Output 1 | `[1, 34, 2]` | landmarks |
| Output 2 | `[1, 2]` | `gaze_pitchyaw` |

Only output 2 drives the current gaze cursor. Heatmaps and landmarks are allocated because the model produces them, but the board uses pitch/yaw for calibration.

---

## CompiledModel API

```kotlin
// EyeGazeModel.kt
val model = CompiledModel.create(
    context.assets,
    "eyegaze.tflite",
    CompiledModel.Options(Accelerator.NPU, Accelerator.GPU)
)

val inputs = model.createInputBuffers()
val outputs = model.createOutputBuffers()

inputs[0].writeFloat(inputArray)
model.run(inputs, outputs)

val pitchYaw = outputs[2].readFloat()
val pitch = pitchYaw[0]
val yaw = pitchYaw[1]
```

The app attempts NPU first, then GPU, then CPU. The demo target is NPU execution, and fallback should be visible in logs and the badge.

---

## CameraX ImageAnalysis Configuration

```kotlin
// CameraManager.kt
val imageAnalysis = ImageAnalysis.Builder()
    .setTargetResolution(Size(640, 480))
    .setTargetRotation(Surface.ROTATION_0)
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
    .build()

imageAnalysis.setAnalyzer(inferenceExecutor) { imageProxy ->
    try {
        val bitmap = imageProxy.toBitmap()
        val gazeResult = gazeEstimator.estimate(bitmap, eyeGazeModel)
        viewModel.onGazeUpdate(
            gazeResult = gazeResult,
            inferenceMs = eyeGazeModel.lastInferenceMs,
            accelerator = eyeGazeModel.acceleratorName
        )
    } finally {
        imageProxy.close()
    }
}
```

`STRATEGY_KEEP_ONLY_LATEST` is important: stale frames are dropped so gaze latency stays bounded even when detection or inference takes longer than a frame interval.

---

## Eye Crop Preprocessing

```text
ImageProxy (RGBA_8888, 640x480)
    |
    v
toBitmap()
    |
    v
ARGB_8888 copy -> RGB_565 copy for FaceDetector
    |
    v
FaceDetector.findFaces()
    |
    v
eye midpoint + eye distance
    |
    v
crop eye region from original ARGB frame
    |
    v
Bitmap.createScaledBitmap(width=160, height=96)
    |
    v
BT.601 grayscale normalize to [0,1]
    |
    v
FloatBuffer[15360]
```

The output layout is row-major height x width. The model expects a single-channel grayscale eye crop, not a full-face RGB tensor.

---

## Gaze Math

### EMA Smoothing

```kotlin
private val alpha = 0.3f
private var hasFirstSample = false
private var smoothedPitch = 0f
private var smoothedYaw = 0f

fun smooth(rawPitch: Float, rawYaw: Float): Pair<Float, Float> {
    if (!hasFirstSample) {
        smoothedPitch = rawPitch
        smoothedYaw = rawYaw
        hasFirstSample = true
    } else {
        smoothedPitch = alpha * rawPitch + (1f - alpha) * smoothedPitch
        smoothedYaw = alpha * rawYaw + (1f - alpha) * smoothedYaw
    }
    return smoothedPitch to smoothedYaw
}
```

Pitch and yaw are smoothed independently. The first sample initializes the filter directly to avoid startup lag.

### Direction Conventions

| Value | Meaning |
|-------|---------|
| `pitch > 0` | looking down |
| `pitch < 0` | looking up |
| `yaw > 0` | looking right |
| `yaw < 0` | looking left |

---

## Calibration System

### 4-Point Calibration Protocol

```text
Screen targets displayed in sequence:
  [0] Top-left     -> record screen point + current pitch/yaw
  [1] Top-right    -> record screen point + current pitch/yaw
  [2] Bottom-left  -> record screen point + current pitch/yaw
  [3] Bottom-right -> record screen point + current pitch/yaw
```

### Affine Transform

```kotlin
// CalibrationEngine.kt
fun addCalibrationPoint(screenPoint: PointF, pitchYaw: PointF)
fun computeAffineTransform(): Boolean
fun applyCalibration(pitch: Float, yaw: Float): PointF
fun reset()
```

Affine model:

```text
[screenX]   [a00 a01 a02]   [pitch]
[screenY] = [a10 a11 a12] * [yaw  ]
                             [1    ]
```

With four points, the engine solves an overdetermined least-squares fit. At least three non-collinear gaze points are required.

### Integration Status

The affine math exists in `CalibrationEngine.kt`. Some ViewModel and calibration-screen call sites are still marked as TODOs in code, so the current docs describe the intended contract and implemented math without claiming the full calibration flow is complete.

---

## Dwell Selection System

```kotlin
private const val DWELL_THRESHOLD_MS = 1500L
private const val COOLDOWN_MS = 500L

fun onCellHovered(cellIndex: Int?) {
    when {
        cellIndex == null -> resetDwell()
        cellIndex != currentDwellCell -> startNewDwell(cellIndex)
        else -> updateProgressAndSelectIfReady(cellIndex)
    }
}
```

The ViewModel maps calibrated screen pixels to a 2x3 row-major grid:

```text
0 | 1 | 2
--+---+--
3 | 4 | 5
```

When the same cell remains hovered for 1.5 seconds, `TtsManager.speak(phrase)` is called and the UI briefly shows the selected state.

---

## State Contract

```kotlin
data class GazeState(
    val gazePoint: Offset?,
    val hoveredCell: Int?,
    val dwellProgress: Float,
    val inferenceMs: Long,
    val accelerator: String,
    val faceDetected: Boolean,
    val rawPitch: Float,
    val rawYaw: Float
)
```

`rawPitch` and `rawYaw` are the smoothed model outputs used for calibration capture. `gazePoint` is the calibrated screen position in pixels when calibration is active and available.

---

## NPU Verification

Evidence to show during judging:

- `EyeGazeModel.kt` uses LiteRT `CompiledModel`, not `Interpreter`.
- The model is created with `Accelerator.NPU` preferred and `Accelerator.GPU` fallback.
- The `NpuBadge` displays accelerator name and inference latency.
- Logcat includes EyeGaze model load and per-frame inference timing.

---

## Head Pose Pivot

If EyeGaze accuracy is below the Hour 8 threshold after calibration, the planned pivot is to use head pose derived from available landmark geometry or another face-pose signal. The rest of the pipeline stays mostly the same:

```text
camera -> face/eye signal -> pitch/yaw-like values -> CalibrationEngine -> grid cell
```

The demo narrative should be adjusted only if this pivot is actually implemented.
