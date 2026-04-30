# GazeBoard - Model Guide

## Runtime Model

GazeBoard uses `eyegaze.tflite` through LiteRT's `CompiledModel` API. The app loads it from:

```text
app/src/main/assets/eyegaze.tflite
```

The first app launch may spend about 2-5 seconds JIT-compiling the model for the Hexagon NPU. LiteRT caches the compiled artifact in app-private storage, so subsequent launches should be faster.

---

## Required Tensor Contract

| Tensor | Shape | Type | Meaning |
|--------|-------|------|---------|
| Input | `[1, 96, 160]` or equivalent flat length `15360` | float32 | Grayscale eye crop normalized to `[0,1]`, row-major height x width |
| Output 0 | `[1, 3, 34, 48, 80]` | float32 | Heatmaps |
| Output 1 | `[1, 34, 2]` | float32 | Eye landmarks |
| Output 2 | `[1, 2]` | float32 | `gaze_pitchyaw`: pitch and yaw in radians |

`EyeDetector.detectAndCrop()` owns preprocessing and returns the `FloatBuffer[15360]` input. `EyeGazeModel.runInference()` reads output 2 and returns `GazeAngles(pitch, yaw)`.

---

## Verifying the Model

If Python TFLite tooling is available, inspect tensor metadata before a demo:

```bash
python3 - <<'EOF'
import tensorflow as tf

interp = tf.lite.Interpreter(model_path="app/src/main/assets/eyegaze.tflite")
interp.allocate_tensors()
print("Inputs:")
for t in interp.get_input_details():
    print(t["name"], t["shape"], t["dtype"])
print("Outputs:")
for t in interp.get_output_details():
    print(t["name"], t["shape"], t["dtype"])
EOF
```

Expected model behavior:

```text
Input:  length 15360, interpreted as 96x160 grayscale
Output: heatmaps, landmarks, gaze_pitchyaw
```

The app also logs tensor behavior indirectly during load and inference. Watch:

```bash
adb logcat | grep GazeBoard
```

---

## Installing and Warming the JIT Cache

```bash
bash scripts/install_and_run.sh
```

This builds the APK, installs it, and launches the app. Leave the app open briefly on first launch so LiteRT can compile and cache the NPU model. The `NpuBadge` should report the active accelerator and recent inference latency.

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| App reports model load failure | Missing or corrupt asset | Confirm `app/src/main/assets/eyegaze.tflite` exists and is a valid TFLite file |
| `CompiledModel.create()` throws | Delegate/model incompatibility | Check Logcat, then try the GPU fallback path to confirm the model itself loads |
| Badge shows `GPU` or `CPU` | NPU delegate rejected one or more ops | Relaunch after cache warm; if still not NPU, inspect delegate errors in Logcat |
| Inference returns null | Model not loaded, tensor mismatch, or runtime error | Check `GazeBoard` logs around `EyeGaze inference error` |
| Face is visible but no inference occurs | `FaceDetector` did not find a valid face or eye crop | Improve lighting, center the face, and keep the phone at arm's length |
| Pitch/yaw barely changes | Eye crop may be poorly framed | Recheck `EyeDetector` crop sizing and test with a stable front-facing pose |

---

## Notes

- No network access is required at runtime.
- No Qualcomm AI Hub account is required for the current path.
- The demo target is NPU execution on the Samsung Galaxy S25 Ultra. GPU/CPU fallback is useful for diagnosis but should be visible in the badge and logs.
