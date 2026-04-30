# GazeBoard — Technical Architecture

## System Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Samsung Galaxy S25 Ultra                      │
│                                                                   │
│  ┌──────────────┐    ┌──────────────────────────────────────┐   │
│  │ Front Camera │───▶│         CameraX ImageAnalysis         │   │
│  │  640×480     │    │    (background executor thread)       │   │
│  │   15 FPS     │    └──────────────┬───────────────────────┘   │
│  └──────────────┘                   │ ImageProxy (RGBA_8888)     │
│                                     ▼                            │
│                        ┌────────────────────────┐               │
│                        │   Bitmap Preprocessing  │               │
│                        │  crop face region        │               │
│                        │  resize → 192×192        │               │
│                        │  normalize [0,1]          │               │
│                        └────────────┬───────────┘               │
│                                     │ FloatBuffer 192×192×3      │
│                                     ▼                            │
│                        ┌────────────────────────┐               │
│                        │  FaceLandmarkModel      │               │
│                        │  CompiledModel API      │               │
│                        │  Accelerator.NPU        │               │
│                        │  (Hexagon DSP/NPU)      │               │
│                        └────────────┬───────────┘               │
│                                     │ FloatArray[1434]           │
│                                     │ (478 landmarks × 3)        │
│                                     ▼                            │
│                        ┌────────────────────────┐               │
│                        │    GazeEstimator        │               │
│                        │  extract iris idx 468   │               │
│                        │  normalize to eye rect  │               │
│                        │  EMA smoothing α=0.3    │               │
│                        │  blink detection EAR    │               │
│                        └────────────┬───────────┘               │
│                                     │ PointF (rawGaze)           │
│                                     ▼                            │
│                        ┌────────────────────────┐               │
│                        │  CalibrationEngine      │               │
│                        │  4-point affine matrix  │               │
│                        │  screen space mapping   │               │
│                        └────────────┬───────────┘               │
│                                     │ PointF (screenGaze)        │
│                                     ▼                            │
│                        ┌────────────────────────┐               │
│                        │  GazeBoardViewModel     │               │
│                        │  mapToCell(gaze)        │               │
│                        │  DwellTimer (1.5s)      │               │
│                        │  StateFlow<GazeState>   │               │
│                        └────────────┬───────────┘               │
│                                     │                            │
│              ┌──────────────────────┼──────────────────┐        │
│              ▼                      ▼                   ▼        │
│      ┌──────────────┐   ┌──────────────────┐  ┌──────────────┐ │
│      │  BoardScreen  │   │  GazeCursor      │  │  TtsManager  │ │
│      │  PhraseCell   │   │  Canvas overlay   │  │  speak()     │ │
│      │  DwellRing    │   │  NpuBadge        │  └──────────────┘ │
│      └──────────────┘   └──────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Inference Pipeline — Latency Budget

Target: **< 30ms total per frame** (enables ≥15 FPS continuous tracking)

| Stage | Target | Notes |
|-------|--------|-------|
| Camera → ImageProxy | ~0ms | Hardware pipeline |
| Bitmap crop + resize | ~2ms | CPU, done on analysis executor |
| Float normalization | ~1ms | CPU |
| NPU inference (face_landmark) | **~8ms** | Hexagon NPU via CompiledModel |
| Landmark extraction + gaze math | ~1ms | CPU, simple arithmetic |
| EMA + calibration transform | ~0.5ms | Matrix multiply |
| ViewModel StateFlow emission | ~0.5ms | Coroutine dispatch |
| **Total** | **~13ms** | ~76 FPS theoretical max |

If NPU inference is forced to CPU (fallback): expect 60–120ms, dropping to ~8 FPS. This is a fail state — display warning.

---

## CompiledModel API — Kotlin Implementation

LiteRT JIT-compiles the model for the Hexagon NPU on first launch and caches the result.
**Launch the app once before the demo** to warm the cache. No AOT compilation or Qualcomm AI Hub account required.

```kotlin
// FaceLandmarkModel.kt

import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Accelerator

class FaceLandmarkModel(private val context: Context) {

    private var model: CompiledModel? = null
    private var lastAccelerator: String = "UNKNOWN"

    fun load() {
        // NPU preferred; GPU as fallback if any op isn't NPU-supported.
        // LiteRT JIT-compiles for Hexagon NPU on first launch (~2-5s), then caches.
        val options = CompiledModel.Options.Builder()
            .setAccelerator(Accelerator.NPU, Accelerator.GPU)
            .build()

        // First launch: JIT compilation. Subsequent launches: cached compiled model.
        model = CompiledModel.create(
            context.assets,
            "face_landmark.tflite",
            options
        )

        // Verify we're actually on NPU — this is critical for judging
        lastAccelerator = model?.accelerator?.name ?: "UNKNOWN"
        if (lastAccelerator != "NPU") {
            Log.w("GazeBoard", "WARNING: Running on $lastAccelerator, not NPU!")
        } else {
            Log.i("GazeBoard", "Confirmed NPU execution via CompiledModel API")
        }
    }

    fun runInference(bitmap: Bitmap): FloatArray? {
        val mdl = model ?: return null
        val startMs = SystemClock.elapsedRealtime()

        val inputBuffer = bitmapToFloatBuffer(bitmap)  // 192×192×3 normalized
        val outputBuffer = FloatArray(478 * 3)

        mdl.run(arrayOf(inputBuffer), arrayOf(outputBuffer))

        val inferenceMs = SystemClock.elapsedRealtime() - startMs
        Log.d("GazeBoard", "Inference: ${inferenceMs}ms on $lastAccelerator")

        return outputBuffer
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        // Resize to 192×192, convert to normalized float [0,1]
        val scaled = Bitmap.createScaledBitmap(bitmap, 192, 192, false)
        val buffer = FloatBuffer.allocate(192 * 192 * 3)
        val pixels = IntArray(192 * 192)
        scaled.getPixels(pixels, 0, 192, 0, 0, 192, 192)
        for (pixel in pixels) {
            buffer.put(((pixel shr 16) and 0xFF) / 255f)  // R
            buffer.put(((pixel shr 8)  and 0xFF) / 255f)  // G
            buffer.put((pixel          and 0xFF) / 255f)  // B
        }
        buffer.rewind()
        return buffer
    }
}
```

---

## CameraX ImageAnalysis Configuration

```kotlin
// CameraManager.kt

val imageAnalysis = ImageAnalysis.Builder()
    .setTargetResolution(Size(640, 480))
    .setTargetRotation(Surface.ROTATION_0)
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)  // never queue frames
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
    .build()

imageAnalysis.setAnalyzer(inferenceExecutor) { imageProxy ->
    val bitmap = imageProxy.toBitmap()   // RGBA_8888 direct conversion
    val landmarks = faceLandmarkModel.runInference(bitmap)
    if (landmarks != null) {
        val gaze = gazeEstimator.estimate(landmarks)
        viewModel.onGazeUpdate(gaze)
    }
    imageProxy.close()  // MUST call or pipeline stalls
}

// Bind to lifecycle
val cameraProvider = ProcessCameraProvider.getInstance(context).await()
cameraProvider.bindToLifecycle(
    lifecycleOwner,
    CameraSelector.DEFAULT_FRONT_CAMERA,
    imageAnalysis
)
```

---

## Bitmap Preprocessing Pipeline

```
ImageProxy (RGBA_8888, 640×480)
    │
    ▼
toBitmap() — zero-copy when format matches
    │
    ▼
Face detection region crop (optional — use full frame if no face detector)
    │
    ▼
Bitmap.createScaledBitmap(192, 192, bilinear=true)
    │
    ▼
getPixels() → IntArray
    │
    ▼
Normalize: R/255f, G/255f, B/255f → FloatBuffer[192*192*3]
    │
    ▼
CompiledModel.run(input: FloatBuffer, output: FloatArray[1434])
```

Note: The face_landmark model expects the **full face**, not just the eye region. If inference quality is poor, add a face bounding-box detection step first (can use a second small TFLite model or MediaPipe's BlazeFace).

---

## Gaze Math Module

### Normalization Formula
```kotlin
// GazeEstimator.kt

fun extractGaze(landmarks: FloatArray): PointF? {
    // Landmark layout: [x0,y0,z0, x1,y1,z1, ...]
    fun lm(idx: Int) = PointF(landmarks[idx*3], landmarks[idx*3+1])

    val irisL  = lm(468)   // Left iris center
    val irisR  = lm(473)   // Right iris center
    val cornerLOuter = lm(33)
    val cornerLInner = lm(133)
    val lidLUpper    = lm(159)
    val lidLLower    = lm(145)
    val cornerROuter = lm(362)
    val cornerRInner = lm(263)

    val gazeLx = (irisL.x - cornerLOuter.x) / (cornerLInner.x - cornerLOuter.x)
    val gazeRx = (irisR.x - cornerRInner.x) / (cornerROuter.x - cornerRInner.x)
    val gazeY  = (irisL.y - lidLUpper.y)    / (lidLLower.y   - lidLUpper.y)

    // Average left and right eyes for stability
    val rawGazeX = (gazeLx + gazeRx) / 2f
    val rawGazeY = gazeY

    return PointF(rawGazeX.coerceIn(0f, 1f), rawGazeY.coerceIn(0f, 1f))
}
```

### EMA Smoothing
```kotlin
private var smoothedGaze: PointF = PointF(0.5f, 0.5f)
private val alpha = 0.3f   // higher = more responsive, lower = smoother

fun smooth(raw: PointF): PointF {
    smoothedGaze = PointF(
        alpha * raw.x + (1f - alpha) * smoothedGaze.x,
        alpha * raw.y + (1f - alpha) * smoothedGaze.y
    )
    return smoothedGaze
}
```

### Blink Detection (Eye Aspect Ratio)
```kotlin
fun isBlinking(landmarks: FloatArray): Boolean {
    fun lm(idx: Int) = PointF(landmarks[idx*3], landmarks[idx*3+1])

    val upper = lm(159)
    val lower = lm(145)
    val outer = lm(33)
    val inner = lm(133)

    val vertDist = abs(upper.y - lower.y)
    val horizDist = abs(inner.x - outer.x)

    val ear = if (horizDist > 0) vertDist / horizDist else 1f
    return ear < 0.2f   // blink threshold
}
```

---

## Calibration System

### 4-Point Calibration Protocol
```
Screen corners displayed in sequence:
  [0] Top-left     → user looks here for 1.5s → record gazePoint[0]
  [1] Top-right    → user looks here for 1.5s → record gazePoint[1]
  [2] Bottom-left  → user looks here for 1.5s → record gazePoint[2]
  [3] Bottom-right → user looks here for 1.5s → record gazePoint[3]
```

### Affine Transform Computation
```kotlin
// CalibrationEngine.kt

// Given: screenPoints[4] (known screen corners) + gazePoints[4] (recorded raw gaze)
// Compute 2×3 affine matrix A such that: A * [gx, gy, 1]^T ≈ [sx, sy]

// Using OpenCV-style Least Squares (manual implementation):
// Solve: [screen_x, screen_y] = A * [gaze_x, gaze_y, 1]
// Min 3 non-collinear points needed; 4 provides overdetermined system → use pseudoinverse

fun computeAffineTransform() {
    // Build matrices G (gaze, N×3) and S (screen, N×2)
    // A = (G^T * G)^-1 * G^T * S   (least squares)
    // Store A as FloatArray(6) = [a00, a01, a02, a10, a11, a12]
}

fun applyCalibration(rawGaze: PointF): PointF {
    val sx = a[0]*rawGaze.x + a[1]*rawGaze.y + a[2]
    val sy = a[3]*rawGaze.x + a[4]*rawGaze.y + a[5]
    return PointF(sx, sy)
}
```

---

## Dwell Selection System

```kotlin
// GazeBoardViewModel.kt

private val DWELL_THRESHOLD_MS = 1500L
private val COOLDOWN_MS = 500L

private var dwellStartMs: Long = 0L
private var currentDwellCell: Int? = null

fun onCellHovered(cellIndex: Int?) {
    when {
        cellIndex == null -> resetDwell()
        cellIndex != currentDwellCell -> {
            // New cell — restart timer
            currentDwellCell = cellIndex
            dwellStartMs = SystemClock.elapsedRealtime()
        }
        else -> {
            // Same cell — check progress
            val elapsed = SystemClock.elapsedRealtime() - dwellStartMs
            val progress = (elapsed.toFloat() / DWELL_THRESHOLD_MS).coerceIn(0f, 1f)
            _gazeState.update { it.copy(dwellProgress = progress) }

            if (elapsed >= DWELL_THRESHOLD_MS) {
                selectCell(cellIndex)
            }
        }
    }
}

private fun selectCell(index: Int) {
    val phrase = phrases[index]
    ttsManager.speak(phrase)
    _appState.value = AppState.Selected(index)
    viewModelScope.launch {
        delay(COOLDOWN_MS)
        resetDwell()
        _appState.value = AppState.Tracking
    }
}
```

---

## TTS Integration

```kotlin
// TtsManager.kt

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    fun initialize(onReady: () -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isReady = true
                onReady()
            }
        }
    }

    fun speak(phrase: String) {
        if (!isReady) return
        // QUEUE_FLUSH interrupts any current speech — intentional
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, phrase)
    }

    fun shutdown() = tts?.shutdown()
}
```

**Pre-warming:** Call `initialize()` in `Application.onCreate()` so TTS engine is ready before the first selection. First speech after cold start has ~200ms delay; subsequent calls are instant.

---

## State Management

```kotlin
// AppState.kt
sealed class AppState {
    object Calibrating : AppState()
    object Tracking : AppState()
    data class Selected(val cellIndex: Int) : AppState()
    object Cooldown : AppState()
}

// GazeBoardViewModel StateFlow
private val _appState = MutableStateFlow<AppState>(AppState.Calibrating)
val appState: StateFlow<AppState> = _appState.asStateFlow()

private val _gazeState = MutableStateFlow(GazeState())
val gazeState: StateFlow<GazeState> = _gazeState.asStateFlow()
```

---

## NPU Verification

```kotlin
// In FaceLandmarkModel.kt, called from initialization:
val acceleratorName = model?.accelerator?.name ?: "UNKNOWN"

// In NpuBadge.kt composable — visible on board screen at all times:
@Composable
fun NpuBadge(accelerator: String, inferenceMs: Long) {
    val color = when (accelerator) {
        "NPU" -> Color(0xFF00E676)   // green — on NPU
        "GPU" -> Color(0xFFFFD600)   // yellow — fallback
        else  -> Color(0xFFFF1744)   // red — CPU fallback (bad)
    }
    Box(modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(4.dp)) {
        Text("$accelerator · ${inferenceMs}ms", color = Color.Black, fontSize = 11.sp)
    }
}
```

---

## Head Pose Fallback (activate if iris fails at Hour 8)

### Key Landmarks for Head Pose
- 1: Nose tip
- 33: Left eye outer corner
- 263: Right eye outer corner
- 61: Mouth left corner
- 291: Mouth right corner
- 199: Chin

### Simplified Pitch/Yaw Derivation
```kotlin
// In GazeEstimator.kt — replace extractGaze() with this:

fun extractHeadPose(landmarks: FloatArray): PointF {
    fun lm(idx: Int) = PointF(landmarks[idx*3], landmarks[idx*3+1])

    val noseTip   = lm(1)
    val leftEye   = lm(33)
    val rightEye  = lm(263)
    val chin      = lm(199)

    // Face center
    val faceX = (leftEye.x + rightEye.x) / 2f
    val faceY = (noseTip.y + chin.y) / 2f

    // Yaw: nose tip deviation from eye midpoint (normalized by eye width)
    val eyeWidth = abs(rightEye.x - leftEye.x)
    val yaw = (noseTip.x - faceX) / eyeWidth   // -1=left, +1=right

    // Pitch: nose tip position relative to face vertical span
    val faceHeight = abs(chin.y - (leftEye.y + rightEye.y) / 2f)
    val pitch = (noseTip.y - faceY) / faceHeight   // -1=up, +1=down

    // Normalize to [0,1] for consistency with iris gaze format
    return PointF(
        (yaw + 1f).coerceIn(0f, 2f) / 2f,
        (pitch + 1f).coerceIn(0f, 2f) / 2f
    )
}
```

No model change needed. Same CompiledModel. Same NPU inference. Same demo narrative.

---

## Distance Monitoring

```kotlin
// Use inter-pupillary distance (landmarks 468 ↔ 473) as face-distance proxy

fun estimateDistance(landmarks: FloatArray): Float {
    val irisL = PointF(landmarks[468*3], landmarks[468*3+1])
    val irisR = PointF(landmarks[473*3], landmarks[473*3+1])
    val ipd = sqrt((irisR.x - irisL.x).pow(2) + (irisR.y - irisL.y).pow(2))
    // ipd in normalized [0,1] coords; typical face-filling distance: ipd ~ 0.15–0.25
    return ipd
}

// Show "Move closer" overlay if ipd < 0.10 (face too small / too far)
// Show "Move back"  overlay if ipd > 0.35 (face too close, landmarks degrade)
```
