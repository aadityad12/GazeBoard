# GazeBoard — Judging Strategy

## Overview

100 points total. Our target: **85+**. Strategy: lock in the pass/fail gates first, then maximize the 40-point technical category through visible evidence.

---

## Pass/Fail Gates (BOTH must pass — do not risk either)

### Gate 1: Theme & API Fit
**What judges check:** Does this run on Samsung Galaxy S25 Ultra? Is it relevant to LiteRT?

**How we prove it:**
- Demo is conducted on a physical S25 Ultra (not emulator, not another phone)
- README states explicitly: "Requires Samsung Galaxy S25 Ultra (SM8750)"
- App UI includes device info or mention S25 in the NPU badge

**Risk:** None if we have the physical device. Always have the S25 charged and ready.

### Gate 2: LiteRT Integration (CompiledModel API)
**What judges check:** Is `CompiledModel` API used? NOT the old `Interpreter` class?

**How we prove it:**
- `EyeGazeModel.kt` uses `CompiledModel.create()` — visible in code
- `Accelerator.NPU` is passed explicitly
- Demo narration says "LiteRT CompiledModel API" and "Hexagon NPU" by name
- NPU badge shows "NPU" in green during demo

**Risk:** If AOT model fails to load, we must NOT fall back silently to Interpreter. Log the error loudly, display it, and debug. A silent CPU fallback while claiming NPU is disqualification risk.

---

## Category 1: Technological Implementation — 40 Points

**This is the big one. Win here and the rest follows.**

### Performance (latency, FPS)
**What to show:** NPU badge displaying sub-10ms inference time in real time.

**Evidence to prepare:**
- Screenshot of NPU badge showing "NPU · 8ms" — print it or show on second phone
- Mention during demo: "Eight milliseconds per frame — that's the Hexagon NPU via LiteRT CompiledModel API"
- If possible, show CPU vs NPU toggle (add a debug menu that switches accelerator) → dramatic 10ms → 80ms comparison

### Efficiency (energy/resource use)
**What to show:** We run on the NPU, which is purpose-built for neural inference and uses a fraction of CPU power.

**Talking points:**
- "The Hexagon NPU consumes roughly 200mW for this inference — on CPU it would be 2–3W"
- "We use `STRATEGY_KEEP_ONLY_LATEST` backpressure so we never queue frames or waste memory"
- "LiteRT caches the NPU-compiled model after first launch — we pre-warmed it before this demo"

**Evidence:** Show in README that first launch warms LiteRT's JIT cache for `eyegaze.tflite`.

### Optimization (evidence of optimization for target environment)
**What to show:** LiteRT's NPU-targeted JIT compilation via CompiledModel API is the optimization story.

**Evidence to prepare:**
- `EyeGazeModel.kt` uses `Accelerator.NPU, Accelerator.GPU` — visible in code, demonstrates we target the NPU specifically
- NPU badge in UI shows real-time inference time — visual proof of optimization
- `STRATEGY_KEEP_ONLY_LATEST` backpressure in CameraManager — shows we optimized the pipeline to never queue frames
- In code comments, note why the EyeGaze input is 160x96 grayscale and why CameraX uses RGBA frames before eye cropping
- Mention that `CompiledModel` API caches the NPU-compiled model after first launch — "we warmed the cache before this demo"

---

## Category 2: Application Use-Case & Innovation — 25 Points

### Problem Solving
**The $15,000 vs. free contrast is the entire argument.** Use it.

**Script:** "The Tobii Dynavox that Alex's family spent 11 months getting insurance approval for costs $15,000. GazeBoard costs zero dollars and runs on hardware the family already has."

**Evidence:**
- PRD.md documents the problem space with user persona
- README opens with the problem statement
- Demo shows a working app — the proof is in the product

### Creativity/Uniqueness
**Our angle:** We're not just "another ML app." We're using the phone's NPU to enable accessibility that previously required dedicated hardware.

**Talking points:**
- "Eye gaze tracking on consumer hardware, for zero cost" is genuinely novel
- "Runs entirely on Hexagon NPU — no cloud dependency means it works in a hospital room with no WiFi, in a rural home, in a power outage on battery"

### User Experience
**Show, don't tell.** Let a judge use it.

**Strategy:**
- After demo narration, offer: "Would you like to try it? Takes about 10 seconds to calibrate."
- The experience of watching the cursor follow your eyes is immediately impressive
- Large cells are readable at arm's length — accessibility is obvious

---

## Category 3: Deployment & Accessibility — 20 Points

### Ease of Install
**Target:** Judge installs APK from QR code in 30 seconds.

**Prepare:**
- Build and include release APK at `app/release/gazeboard-v1.0.apk`
- Generate QR code linking to APK download (host on GitHub Releases or local server)
- README includes: `adb install gazeboard-v1.0.apk` as one-liner
- Zero setup: no account, no config, no permissions beyond CAMERA and TTS

### Usability/Stability During Demo
**Target:** No crash during 10-minute demo window. Selection succeeds ≥80% of the time.

**Preparation checklist:**
- [ ] Test release APK (not debug) for demo — different performance characteristics
- [ ] Pre-calibrate on demo device 30 minutes before presentation
- [ ] Keep backup video ready (see DEMO-SCRIPT.md)
- [ ] Know the restart procedure if calibration drifts: 3-tap on the NPU badge → recalibrate

---

## Category 4: Presentation & Documentation — 15 Points

### Clarity of Explanation
**The 3-minute demo script (docs/DEMO-SCRIPT.md) covers this.** Key rules:
- Say "LiteRT CompiledModel API" at least twice
- Say "Hexagon NPU" at least once
- Give the latency number ("8 milliseconds")
- Give the cost contrast ("$15,000 vs. free")
- Let the app speak for itself — TTS saying "I need water" is more compelling than any slide

### Code Quality
**Target:** Any judge who opens `EyeGazeModel.kt`, `EyeDetector.kt`, or `GazeEstimator.kt` should immediately understand what it does.

**Standards to maintain:**
- No dead code or commented-out blocks
- Descriptive variable names (`irisLeftCenter` not `lm468`)
- Tensor and pitch/yaw conventions commented with human descriptions
- Function bodies under 30 lines — extract helpers
- No force-unwraps (`!!`) in inference-critical paths

### README/Docs
**README must include (judge checklist):**
- [ ] What the app does (1 paragraph, compelling)
- [ ] Why it matters (the problem + cost comparison)
- [ ] Architecture diagram (text-based is fine)
- [ ] Setup instructions that actually work from a clean machine
- [ ] Build and install instructions
- [ ] Tech stack with versions
- [ ] Team members
- [ ] License (Apache 2.0)

---

## Tiebreaker Positioning

Judges break ties by: LiteRT Usage > Tech Implementation > Use-Case > Deployment > Presentation

**We maximize our tiebreaker position by:**

1. **LiteRT Usage:** CompiledModel API with NPU — verified and visible. Badge in UI. Named explicitly in narration. Code is clean and obvious.

2. **Tech Implementation:** Sub-10ms NPU inference. AOT compilation. STRATEGY_KEEP_ONLY_LATEST backpressure. Affine calibration. EMA smoothing.

3. **Use-Case:** The strongest problem statement in the room. ALS + $15,000 price contrast is viscerally compelling.

---

## Pre-Demo Checklist (30 minutes before)

- [ ] S25 Ultra charged to 100%
- [ ] APK installed (release build)
- [ ] App launched and calibrated on demo area
- [ ] NPU badge shows "NPU" (not "CPU")
- [ ] TTS test: say "I need water" out loud from all 6 cells
- [ ] Backup video playable from Photos app
- [ ] Each presenter knows their segment of the 3-minute script
- [ ] Someone has timed the full demo — it should be 2:45–3:00 exactly

---

## What NOT to Do

- Do NOT mention iris_landmark stretch goal unless asked — it sounds unfinished
- Do NOT apologize for limitations during demo narration
- Do NOT use the word "prototype" — say "app" or "system"
- Do NOT demo on an emulator under any circumstances
- Do NOT let a crash derail the presentation — pivot to backup video without panic
- Do NOT forget to say "LiteRT CompiledModel API" — this is a pass/fail gate AND a tiebreaker
