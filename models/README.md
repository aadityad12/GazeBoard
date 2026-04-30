# GazeBoard — Model Acquisition Guide

## Required Models

| Model | File | Size | Input | Output |
|-------|------|------|-------|--------|
| MediaPipe FaceMesh (AOT for SM8750) | `face_landmark_compiled.tflite` | ~3MB | 192×192×3 float | 478×3 float |

---

## Method 1: Qualcomm AI Hub (Recommended — AOT compiled for SM8750)

This produces a model compiled specifically for the Snapdragon 8 Elite's Hexagon NPU, enabling `CompiledModel` API usage with `Accelerator.NPU`.

### Step 1: Create Qualcomm AI Hub account

Go to `https://aihub.qualcomm.com` and create a free account. Verify your email.

### Step 2: Install Python dependencies

```bash
python -m pip install qai-hub-models qai-hub
```

### Step 3: Authenticate with AI Hub

```bash
qai-hub configure --api-token YOUR_TOKEN_HERE
# Token found at: https://aihub.qualcomm.com/account (after login)
```

### Step 4: Export the model

```bash
# Simplest path — use the pre-packaged export script:
python scripts/export_models.py

# OR manually:
python -m qai_hub_models.models.mediapipe_face.export \
    --device "Samsung Galaxy S25 Ultra" \
    --target-runtime tflite \
    --output-dir models/
```

### Step 5: Verify the output

The export produces a `.tflite` file. Rename it to `face_landmark_compiled.tflite`.

Verify tensor shapes using the Python script:
```bash
python -c "
import tensorflow as tf
interp = tf.lite.Interpreter('models/face_landmark_compiled.tflite')
interp.allocate_tensors()
print('Inputs:', interp.get_input_details())
print('Outputs:', interp.get_output_details())
"
```

**Expected output:**
```
Inputs:  [{'shape': [1, 192, 192, 3], 'dtype': float32, ...}]
Outputs: [{'shape': [1, 1434], 'dtype': float32, ...}]   # 478 * 3 = 1434
```

If the output shape is `[1, 1434]` or `[1, 478, 3]`, the model is correct. If it differs, check which variant was exported (FaceMesh has 468-landmark and 478-landmark versions — you need the 478-landmark version with iris).

### Step 6: Push to device

```bash
bash scripts/push_to_device.sh
```

### Step 7: Verify NPU execution

After installing and running the app, check Logcat:
```bash
adb logcat | grep "GazeBoard"
# Expected: [GazeBoard] Accelerator: NPU
# Bad:      [GazeBoard] WARNING: Running on CPU, not NPU!
```

---

## Method 2: Manual MediaPipe FaceMesh + JIT Compilation (Fallback)

Use this if Qualcomm AI Hub export fails or produces incorrect tensor shapes.

### Download base model

```bash
# MediaPipe FaceMesh with iris (478 landmarks):
curl -L -o models/face_landmark.tflite \
    "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
```

Note: The `.task` file is a MediaPipe bundle. Extract the `.tflite` from it:
```bash
unzip -p models/face_landmarker.task "*/face_landmarks_detector.tflite" \
    > models/face_landmark.tflite
```

### JIT compilation via LiteRT at runtime

When using a non-AOT model, `CompiledModel.create()` with `Accelerator.NPU` will attempt JIT compilation on the device. This is slower on first load (~2–5 seconds) but works correctly.

Update `FaceLandmarkModel.kt` to handle the longer first-load time:
```kotlin
// Show "Loading..." overlay during first model compilation
val options = CompiledModel.Options.Builder()
    .setAccelerator(Accelerator.NPU)
    .build()
// First call may take 2-5 seconds for JIT NPU compilation
model = CompiledModel.create(context.assets, "face_landmark.tflite", options)
```

The NPU badge will still show "NPU" if JIT compilation succeeds. JIT is acceptable for the demo; AOT is preferred for the judging narrative.

---

## Pushing Models to App Assets

The `CompiledModel` API loads models from the app's `assets/` folder. After acquiring the model:

```bash
# Copy to Android assets directory
cp models/face_landmark_compiled.tflite \
    app/src/main/assets/face_landmark_compiled.tflite
```

The file name in assets must match exactly what's used in `FaceLandmarkModel.kt`:
```kotlin
CompiledModel.create(context.assets, "face_landmark_compiled.tflite", options)
```

---

## Model Details

### Input Specification
- Shape: `[1, 192, 192, 3]`
- Type: `float32`
- Range: `[0.0, 1.0]`
- Color order: RGB (not BGR)
- Expected content: full face, roughly centered, any orientation

### Output Specification
- Shape: `[1, 1434]` or `[1, 478, 3]` (equivalent, model-dependent)
- Type: `float32`
- Layout: `[x0, y0, z0, x1, y1, z1, ..., x477, y477, z477]`
- Coordinate system: normalized to [0,1] relative to 192×192 input
- Landmark 468 = left iris center (primary gaze signal)
- Landmark 473 = right iris center (primary gaze signal)

### Key Landmark Indices for GazeBoard
```
468: Left iris center      ← CRITICAL
469-472: Left iris contour
473: Right iris center     ← CRITICAL
474-477: Right iris contour
33:  Left eye outer corner
133: Left eye inner corner
159: Left eye upper lid
145: Left eye lower lid
362: Right eye outer corner
263: Right eye inner corner
386: Right eye upper lid
374: Right eye lower lid
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `CompiledModel.create()` throws exception | Wrong model file or wrong accelerator | Verify model shape matches above; try `Accelerator.GPU` as intermediate step |
| Badge shows "CPU" instead of "NPU" | JIT compilation failed, fell back | Check Logcat for NPU error; try AOT compilation via AI Hub |
| Landmarks all zero | Model loaded wrong input | Verify FloatBuffer is 192×192×3 and values are [0,1] |
| Output shape mismatch | Wrong FaceMesh variant | Need 478-landmark (with iris) version; 468-landmark variant lacks iris indices |
| App crashes on `runInference()` | ImageProxy not closed before next frame | Ensure `imageProxy.close()` is always called |
| NPU library not found | Missing `litert-qualcomm` dependency | Verify `build.gradle.kts` includes `com.google.ai.edge.litert:litert-qualcomm:2.1.0` |
