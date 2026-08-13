# Original product requirements

> **Historical planning document:** This file records the product direction at the start of the April 30 to May 1, 2026 build. It is not a description of the current application and does not contain verified outcome or performance data. See the [README](../README.md) for current status.

## Intended problem

The project explored whether a consumer Android phone could provide a simple gaze-controlled communication interface for people who cannot reliably use touch input. The intended interaction used large targets, dwell selection, and speech output so that a user could communicate without a hand gesture.

GazeBoard is a prototype. The project plan did not include the clinical validation, accessibility research, reliability work, or regulatory review needed for assistive-device use.

## Initial MVP plan

The original plan specified:

- a 2 x 3 board with six fixed phrases;
- a front-camera gaze pipeline;
- four-point calibration;
- an on-screen gaze cursor;
- a 1.5-second dwell selector;
- Android text-to-speech output;
- visible accelerator and inference telemetry;
- local operation without an application server.

The plan treated phrase customization, blink selection, head-tilt navigation, and usage analytics as stretch ideas. Those ideas should not be interpreted as implemented features.

## How the implementation changed

| Initial plan | Current `main` implementation |
| --- | --- |
| Six-cell fixed phrase board | Four quadrants: Yes, No, Help, and More |
| Phrase board only | Quick phrases plus gesture-based word selection |
| 1.5-second communication dwell | 1-second communication dwell and 1.5-second calibration dwell |
| Affine screen-coordinate mapping | Two pitch and yaw midpoint thresholds |
| Visible gaze cursor | `GazeCursor` exists but is not rendered |
| NPU preferred with fallback | NPU required, with an error screen and no fallback |

The implementation-level description is maintained in [ARCHITECTURE.md](ARCHITECTURE.md).
