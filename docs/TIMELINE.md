# Original 24-hour build timeline

> **Historical planning document:** This was the team's execution plan for April 30 to May 1, 2026. Planned checkpoints are not proof that a feature passed, and the Git history is the source of record for work that was committed.

## Planned phases

| Time | Intended focus |
| --- | --- |
| Hours 0 to 1 | Project setup, model acquisition, camera and Compose scaffolding |
| Hours 1 to 3 | Eye preprocessing, LiteRT model loading, frame analysis, UI skeleton |
| Hours 3 to 6 | Camera-to-model integration, calibration, state flow, text-to-speech |
| Hours 6 to 8 | End-to-end checkpoint and calibration tuning |
| Hours 8 to 12 | Interaction tuning, lighting checks, and fallback decision |
| Hours 12 to 16 | Edge cases, lifecycle checks, and release-build preparation |
| Hours 16 to 20 | Documentation and demo preparation |
| Hours 20 to 24 | Rehearsal, packaging, and submission |

## Intended integration checkpoint

The central interface was a per-frame gaze result passed from the ML pipeline to the ViewModel. The checkpoint was intended to verify this sequence:

```text
front camera -> eye crop -> gaze model -> calibrated quadrant
             -> dwell selection -> state transition -> speech
```

That path exists in the current source, but this repository does not contain the target-device logs or recorded test results needed to reconstruct which historical checkpoints passed. Current build and validation results are documented in the [README](../README.md#validation-status).

Team ownership and the interface contract are preserved in [`AGENTS.md`](../AGENTS.md).
