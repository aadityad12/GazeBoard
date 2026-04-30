# GazeBoard — 24-Hour Execution Timeline

**Start:** April 30, 2026 — hackathon kickoff
**End:** May 1, 2026 — demo time

---

## Hours 0–1: Foundation (ALL THREE START PARALLEL)

### Objectives
- Working build on S25 Ultra from litert-samples base
- Model acquired and pushed to device
- Each person has their workspace ready

### Person A
- [ ] `eyegaze.tflite` is already in `app/src/main/assets/` — confirm with `ls app/src/main/assets/`
- [ ] Verify GazeBoard builds without errors (`./gradlew assembleDebug`)
- [ ] Run `bash scripts/install_and_run.sh` — first launch warms LiteRT JIT NPU cache for EyeGaze
- [ ] Watch Logcat for `[GazeBoard] EyeGaze: confirmed NPU execution via CompiledModel API`
- [ ] Stub test: load EyeGazeModel, call `load()`, log that it completes without crash
- [ ] Clone `https://github.com/google-ai-edge/litert-samples` as CompiledModel API reference if needed

### Person B
- [ ] Set up new Android project (or fork from Person A's base)
- [ ] Confirm CameraX preview working on S25 Ultra front camera
- [ ] Add `ImageAnalysis` use case alongside preview
- [ ] Print frame timestamps to Logcat (confirms pipeline is alive)

### Person C
- [ ] Create Jetpack Compose skeleton project
- [ ] Implement `BoardScreen.kt` with 6 hardcoded phrase cells (no gaze yet)
- [ ] Verify phrases are readable on S25 Ultra screen at arm's length
- [ ] Scaffold `TtsManager.kt` — button triggers TTS for hardcoded phrase

**Hour 1 Checkpoint:** Person A: model loads without crash. Person B: camera frames in Logcat. Person C: board renders, TTS works.

---

## Hours 1–3: Core Inference & UI (PARALLEL)

### Person A
- [ ] Implement `EyeDetector.detectAndCrop()` using Android FaceDetector — test with Logcat output
- [ ] Wire `CompiledModel.create()` with `Accelerator.NPU` (exact API, not Interpreter)
- [ ] Implement eye crop preprocessing — 160×96 grayscale to `FloatBuffer[15360]`
- [ ] Call `model.run()` and print pitch/yaw to Logcat
- [ ] Verify outputs include heatmaps, landmarks, and `gaze_pitchyaw`
- [ ] Log inference time per frame

### Person B
- [ ] Set up `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST`, `RGBA_8888` output
- [ ] Implement `imageProxy.toBitmap()` conversion
- [ ] Stub call to `EyeGazeModel.runInference()` (can use dummy `GazeAngles`)
- [ ] Implement pitch/yaw to screen mapping path (see ARCHITECTURE.md) — test with dummy data
- [ ] Implement EMA smoothing function

### Person C
- [ ] Implement `PhraseCell.kt` with dwell animation (animate progress ring 0→1)
- [ ] Implement `CalibrationScreen.kt` skeleton (4 corners, captures tap for now)
- [ ] Wire `GazeBoardViewModel.kt` with `AppState` sealed class
- [ ] Implement `NpuBadge.kt` composable (static placeholder text for now)

**Hour 3 Checkpoint:** Person A: inference printing numbers to Logcat. Person B: gaze formula tested on dummy data. Person C: calibration flow navigates to board.

---

## Hours 3–6: Integration Prep (PARALLEL CONVERGENCE)

### Person A
- [ ] Confirm NPU execution (log `model.accelerator.name`)
- [ ] Add null-check: return null if `FaceDetector` cannot produce a valid eye crop
- [ ] Allocate and verify all EyeGaze output buffers
- [ ] Verify pitch changes when looking up/down and yaw changes when looking left/right
- [ ] Hand off final `EyeGazeModel.kt` API to Person B

### Person B
- [ ] Integrate Person A's actual `runInference()` call into CameraX pipeline
- [ ] Integrate smoothed pitch/yaw into calibration capture
- [ ] Implement `CalibrationEngine.kt`:
  - `addCalibrationPoint(screenPoint, gazePoint)`
  - `computeAffineTransform()` — 4-point least squares
  - `applyCalibration(rawGaze): PointF`
- [ ] Implement `mapToCell(calibratedGaze: PointF): Int?`

### Person C
- [ ] Implement `GazeCursor.kt` Canvas overlay — circle at given PointF
- [ ] Connect `GazeState.gazePoint` to cursor position
- [ ] Connect `GazeState.dwellProgress` to ring animation
- [ ] Connect `GazeState.hoveredCell` to cell highlight state
- [ ] Finalize `TtsManager.kt` with pre-warm in `Application.onCreate()`

---

## Hour 6: INTEGRATION CHECKPOINT ★

**This is the most critical milestone of the 24 hours.**

### Integration Test Sequence
1. Person A hands `EyeGazeModel.kt` to Person B
2. Person B integrates into live CameraX pipeline
3. Verify end-to-end: **move eyes → pitch/yaw changes → gaze point moves → cursor moves on screen**
4. Person C connects ViewModel StateFlow to all UI components
5. Full flow test: launch → calibration → board → look at cell → dwell → TTS speaks

### Pass/Fail Criteria at Hour 6
| Test | Pass | Fail |
|------|------|------|
| NPU inference active | badge shows "NPU" | badge shows "CPU" → debug |
| Pitch/yaw tracking | cursor moves with eyes | cursor frozen -> check EyeGaze output parsing |
| Dwell selection | TTS fires after 1.5s gaze | never fires → check timer |
| Calibration | 4 points captured | crashes on corner capture |

### If any test fails: **fix it before continuing.** Nothing is more important than this integration.

---

## Hours 6–8: Calibration & Polish

### All Persons
- [ ] 4-point calibration screen completed with affine transform computation
- [ ] Verify calibration actually improves cell selection accuracy vs uncalibrated
- [ ] Add "face not detected" overlay when face leaves frame
- [ ] Add distance indicator ("move closer" / "move back") based on IPD
- [ ] NPU profiler badge: real inference time, real accelerator name

---

## Hour 8: GO/NO-GO DECISION ★

**Meeting: All three team members, 15 minutes.**

**Test:** Sit in front of S25 Ultra, complete calibration, attempt to select each of the 6 cells intentionally. Score = correct / total attempts.

| Score | Decision |
|-------|---------|
| ≥ 70% | ✅ Continue with iris tracking. Move to polish phase. |
| 50–70% | ⚠️ Try re-calibrating and adjusting EMA alpha. One more hour. |
| < 50% | 🔄 Pivot to head pose fallback. Person B starts pivot immediately. |

**Head pose pivot estimate:** 2 hours to implement + 1 hour to tune. Done by Hour 11.

---

## Hours 8–12: Polish & Tuning

- [ ] Tune EMA alpha (try 0.2, 0.3, 0.4) — find sweet spot per person
- [ ] Tune dwell threshold (try 1.2s, 1.5s, 2.0s) — configurable in ViewModel
- [ ] Test in different lighting conditions (bright, dim, backlit)
- [ ] Test with glasses on/off
- [ ] Test at different distances (arm's length, 18 inches, 24 inches)
- [ ] Add cooldown visual (brief flash/dim on selected cell)
- [ ] Ensure no ANR/crash in 10 minutes of continuous use

---

## Hours 12–16: Bug Fixes & Edge Cases

- [ ] Face not detected → show overlay, resume tracking when face returns
- [ ] No face / invalid eye crop -> pause dwell timer, don't select
- [ ] Face too close/far → show distance indicator, graceful degradation
- [ ] Screen rotation: lock to portrait
- [ ] Memory leak audit: ImageProxy always closed, model not leaking
- [ ] Battery drain check: is NPU power draw acceptable? (should be 200–400mW)
- [ ] Build release APK: `./gradlew assembleRelease`
- [ ] Install release APK, verify it works (debug vs release sometimes differ)

---

## Hours 16–20: Documentation & Demo Prep

### Person C (lead), assisted by all
- [ ] Complete `README.md` — all sections, setup instructions work from scratch
- [ ] Add code comments to `EyeGazeModel.kt`, `EyeDetector.kt`, and `GazeEstimator.kt` (highest-value files)
- [ ] Finalize `docs/DEMO-SCRIPT.md` — each team member reads through once
- [ ] Draft Devpost submission text (title, description, video placeholder)
- [ ] Prepare NPU latency screenshot for presentation
- [ ] Create one-slide "architecture diagram" for verbal explanation during demo

---

## Hours 20–22: Demo Rehearsals

- [ ] Each team member presents the 3-minute demo solo (simulate judge asking questions)
- [ ] Record backup video: full flow from launch to 3 successful phrase selections
- [ ] Practice the "let the judge try it" moment — position the phone, explain calibration
- [ ] Prepare answer to: "Why not use the phone's built-in accessibility features?"
- [ ] Prepare answer to: "How does this compare to EyeGaze or Tobii?"
- [ ] Prepare answer to: "What would you add with more time?"

---

## Hours 22–24: Submit & Final Commit

- [ ] Final Devpost submission (all required fields complete)
- [ ] Final `git commit` and tag: `git tag v1.0-demo`
- [ ] Upload backup video to Devpost
- [ ] Push final APK to `app/release/` in repo
- [ ] Verify GitHub repo is public
- [ ] Get 2 hours of sleep if possible (at least rest)

---

## Time Budget Summary

| Phase | Hours | Description |
|-------|-------|-------------|
| Foundation | 0–1 | Repo, model, base build |
| Core building | 1–6 | Inference + gaze math + UI (parallel) |
| Integration | 6–8 | End-to-end integration + calibration |
| Go/No-Go | Hour 8 | Iris vs head pose decision |
| Polish | 8–12 | Tuning, edge cases, lighting |
| Hardening | 12–16 | Bug fixes, release build |
| Docs & prep | 16–20 | README, comments, demo script |
| Rehearsal | 20–22 | Practice + backup video |
| Submit | 22–24 | Devpost + final commit |
