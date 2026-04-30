# GazeBoard — Model Acquisition Guide

## How LiteRT CompiledModel API Works (No AOT Required)

We use `CompiledModel.create()` with `Accelerator.NPU, Accelerator.GPU`. LiteRT **JIT-compiles the model for the Hexagon NPU on first launch** and caches the result in the app's private storage. Subsequent launches use the cached compiled model — no JIT delay.

**Action required before demo:** Install the app and launch it once. Let it sit on the calibration screen for ~10 seconds. This warms the JIT cache. All subsequent launches will use the pre-compiled NPU model.

---

## Required Model

| File | Input | Output |
|------|-------|--------|
| `face_landmark.tflite` | `[1, 192, 192, 3]` float32 | `[1, 1434]` float32 (478 landmarks × XYZ) |

Place the file at: `app/src/main/assets/face_landmark.tflite`

---

## Acquisition — Priority Order

### 1. On-Site Provided (Highest Priority — Zero Risk)

At the hackathon venue, Qualcomm and Google engineers will be present. **Ask them directly** for a face landmark .tflite model that works with `CompiledModel` API on the S25 Ultra. They almost certainly have one. This is the fastest path with the best-optimized model.

Questions to ask:
- "Do you have a MediaPipe FaceMesh or face landmark .tflite model optimized for the Hexagon NPU on SM8750?"
- "Is there a sample app that does face landmark inference via CompiledModel API we can reference?"
- "Which models in the litert-samples repo work best for face inference on the S25 Ultra?"

### 2. litert-samples Repo

```bash
git clone https://github.com/google-ai-edge/litert-samples
# Check bundled models in any NPU sample app:
find litert-samples -name "*.tflite" | head -20
```

The image segmentation NPU sample app is the closest reference to our pipeline. It may include a face or object detection model that confirms the CompiledModel API setup works, even if we need a different model for landmarks.

### 3. Automated Download (Try First)

```bash
bash scripts/download_models.sh
```

This script tries in order:
1. LiteRT HuggingFace community models
2. Qualcomm HuggingFace pre-exported models (no account needed)
3. Stock MediaPipe FaceMesh .tflite as final fallback

### 4. Manual HuggingFace Search

Browse these sources (no account required to download):
- `https://huggingface.co/litert-community` — LiteRT-optimized models
- `https://huggingface.co/qualcomm` — Qualcomm pre-exported models
- `https://huggingface.co/google` — Google model releases including MediaPipe variants

Search terms: `face landmark tflite`, `face mesh tflite`, `mediapipe face tflite`

### 5. Stock MediaPipe .tflite (Last Resort)

```bash
# Download MediaPipe FaceLandmarker task bundle
curl -L -o /tmp/face_landmarker.task \
    "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"

# Extract the .tflite from the bundle (it's a zip)
unzip -p /tmp/face_landmarker.task "face_landmarks_detector.tflite" \
    > app/src/main/assets/face_landmark.tflite
```

This works via JIT — `CompiledModel.create()` will NPU-compile it on first launch.

---

## Verifying the Model

After placing the file, verify tensor shapes. Requires `tflite-runtime`:

```bash
pip install tflite-runtime

python3 - <<'EOF'
import tflite_runtime.interpreter as tflite

interp = tflite.Interpreter("app/src/main/assets/face_landmark.tflite")
interp.allocate_tensors()
print("Input: ", interp.get_input_details()[0]['shape'])
print("Output:", interp.get_output_details()[0]['shape'])
EOF
```

**Expected output:**
```
Input:  [1 192 192 3]
Output: [1 1434]   # 478 landmarks × 3 coords — iris tracking works
```

**Acceptable output:**
```
Output: [1 1404]   # 468 landmarks × 3 — no iris landmarks 468-477
```
→ In this case the head pose fallback will be used instead of iris tracking. Document this for demo.

---

## Installing and Warming the JIT Cache

```bash
bash scripts/install_and_run.sh
```

This builds the APK, installs it, and launches the app. The first launch triggers LiteRT JIT compilation for the Hexagon NPU. Watch Logcat:

```bash
adb logcat | grep GazeBoard
```

Expected: `[GazeBoard] Confirmed NPU execution via CompiledModel API`

If you see `WARNING: Running on GPU` — the app is still warming or a few ops fell back to GPU. Relaunch; it should show NPU on the second run once the cache is populated.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Badge shows "CPU" on every launch | JIT compilation failed for NPU | Check Logcat for error; try GPU-only: `Accelerator.GPU` to confirm model loads |
| `CompiledModel.create()` throws | Wrong model format or corrupt file | Verify file is valid TFLite (check magic bytes: first 4 bytes should be `1C 00 00 00`) |
| Output shape mismatch | Wrong model variant | Need 478-landmark (iris) version; see above |
| First launch takes 10+ seconds | JIT compilation in progress | Normal — subsequent launches are instant |
| App crashes immediately | Model not in assets | Confirm file is at `app/src/main/assets/face_landmark.tflite` |
