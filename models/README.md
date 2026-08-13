# EyeGaze model

GazeBoard includes `app/src/main/assets/eyegaze.tflite`, attributed to Qualcomm's [EyeGaze model](https://huggingface.co/qualcomm/EyeGaze). The upstream model card describes EyeNet gaze-direction prediction from 96 x 160 grayscale eye images.

## Checked-in artifact

| Property | Value |
| --- | --- |
| Path | `app/src/main/assets/eyegaze.tflite` |
| Size | 10,079,708 bytes |
| SHA-256 | `312ca60290f07ad78181886e6fd7a47b1a270958ca3cc1d6620f7f335d27a94f` |
| Runtime requested by this app | LiteRT 2.1.1 `CompiledModel` with `Accelerator.NPU` |

The application assumes this tensor contract:

| Tensor | Shape | Use in GazeBoard |
| --- | --- | --- |
| Input | `[1, 96, 160]` float32 grayscale in `[0, 1]` | Left-eye crop |
| Output 0 | `[1, 3, 34, 48, 80]` | Allocated by LiteRT, not read by application code |
| Output 1 | `[1, 34, 2]` | Allocated by LiteRT, not read by application code |
| Output 2 | `[1, 2]` | Read as pitch and yaw |

The repository does not contain an independent model-conversion record or a script that reproduces this exact TFLite file from a pinned upstream revision. The checksum above identifies the artifact that was inspected and built.

## Acquisition and licensing

The model is already committed as an Android asset, so a normal build does not download or copy it. The legacy `scripts/download_models.sh` helper is not required and currently exits with an error after locating the asset.

The upstream Hugging Face entry uses an `other` license label and directs readers to the original implementation's license. Do not assume the repository's Apache 2.0 license grants rights to the model artifact. Review the upstream model and source-model terms before redistribution.

Upstream performance tables are not GazeBoard measurements. This repository contains no target-device benchmark or gaze-accuracy dataset.
