# Target-device demo checklist

This checklist is for manually exercising the current prototype. It does not replace a test suite and should not be used to claim accuracy or performance results.

## Before starting

1. Use a Qualcomm SM8750 Android device with the front camera unobstructed.
2. Install with `./gradlew :app:installDebug` so the device-targeted NPU feature is included.
3. Start `adb logcat -s GazeBoard` and launch `com.gazeboard/.MainActivity`.
4. Confirm that the app receives camera permission.
5. Confirm that Logcat reports successful `CompiledModel` creation on the requested NPU. If it does not, record the full error instead of continuing with a performance claim.

## Manual flow

1. Complete all four displayed calibration targets.
2. Hold gaze on **Yes**, **No**, and **Help** separately. For each selection, check the dwell progress, spoken output, and sentence display.
3. Select **More** to enter spelling mode.
4. Enter the quadrant sequence for a word present near the top of `app/src/main/assets/words.txt`.
5. When two or three candidates remain, select one and confirm that the word is spoken.
6. Open settings, enable the debug overlay, and record the displayed values only as observations from this device and run.
7. Recalibrate and confirm that the four-target flow starts again.

## Evidence to retain

For a reproducible device-validation record, capture:

- device model, system-on-chip, Android version, and build SHA;
- whether the app was a debug or release build;
- Logcat from model loading through the final selection;
- raw observations, failures, and number of attempts;
- the exact method used for any latency or accuracy calculation.

No such device-validation record is currently checked into the repository.
