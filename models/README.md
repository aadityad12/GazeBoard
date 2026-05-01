# Models

## EyeGaze (qualcomm/EyeGaze)

- **Source**: https://huggingface.co/qualcomm/EyeGaze
- **File**: `eyegaze.tflite` (copy to `app/src/main/assets/eyegaze.tflite`)
- **Architecture**: EyeNet — predicts gaze pitch/yaw from eye images
- **Input**: `[1, 96, 160]` float32 grayscale [0, 1]
- **Output 0**: `[1, 3, 34, 48, 80]` heatmaps (unused)
- **Output 1**: `[1, 34, 2]` landmarks (unused)
- **Output 2**: `[1, 2]` **pitch, yaw in radians** ← this is what we use
- **Loaded via**: LiteRT CompiledModel API with `Accelerator.NPU, Accelerator.GPU`

```
pitch > 0 = looking down,  pitch < 0 = looking up
yaw   > 0 = looking right, yaw   < 0 = looking left
```

The model is already in `app/src/main/assets/eyegaze.tflite`. Do not re-download.
