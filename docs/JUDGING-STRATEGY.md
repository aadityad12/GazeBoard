# Original event notes

> **Historical planning document:** GazeBoard was developed in the context of a Qualcomm and LiteRT event in April and May 2026. This file originally contained presentation tactics and target numbers. Those statements were plans, not repository-backed measurements, so they have been replaced with this archival note.

The event constraint that still shapes the code is explicit use of LiteRT's `CompiledModel` API with a Qualcomm NPU dynamic-feature runtime. The current implementation requests `Accelerator.NPU` and shows an error screen if model creation fails.

For current technical evidence, use:

- [README.md](../README.md) for status, setup, validation, and limitations;
- [ARCHITECTURE.md](ARCHITECTURE.md) for the implementation data flow;
- [`EyeGazeModel.kt`](../app/src/main/java/com/gazeboard/ml/EyeGazeModel.kt) for runtime creation and model execution;
- the Git history for contributor and development history.

Do not present upstream model benchmarks, UI telemetry, or planned targets as measured GazeBoard results.
