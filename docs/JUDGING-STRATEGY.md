# Judging Strategy — Point-by-Point

## Eligibility Gate 1: Theme & API Fit ✓
- S25 Ultra target device ✓
- Track 2: Classical Models — Vision & Audio ✓
- On-device AI, no cloud ✓

## Eligibility Gate 2: LiteRT Integration ✓
- Show `CompiledModel.create()` in code review
- Show NPU badge in UI during demo
- Say "LiteRT CompiledModel API" by name (say it twice)
- Even CPU fallback uses CompiledModel, not Interpreter ✓

## Technological Implementation (40 pts)
**Show:**
- NPU badge: "LiteRT: NPU · 8ms" or "LiteRT: CPU · 40ms"
- Frame rate: 15fps @ 640×480, KEEP_ONLY_LATEST backpressure
- ML pipeline: ML Kit (CPU) → EyeGaze (NPU) → EMA smoothing → quadrant mapping
- Calibration: 4-point corner calibration saved to SharedPreferences

**Say:**
- "The EyeGaze model runs via LiteRT's CompiledModel API with NPU+GPU preference"
- "We use KEEP_ONLY_LATEST backpressure — no queue buildup under load"
- "EMA smoothing at α=0.7 balances responsiveness and jitter"

## Application Use-Case & Innovation (25 pts)
**Lead with:** "$15,000 device replaced by a free app on a phone you already own"
**Cite:** GazeSpeak (CHI 2017), SpeakFaster (Nature 2024) — judges respect cited prior art
**Why us:** GazeSpeak + Qualcomm NPU gaze model + T9-style word prediction

## Deployment & Accessibility (20 pts)
- Single APK, no server, no account, airplane mode works
- Let a judge try it during Q&A (calibrate in advance)
- Calibration takes 15 seconds; saved for the session

## Presentation & Documentation (15 pts)
- Clean README with full setup instructions
- Well-commented Kotlin
- Rehearsed 3-minute demo script
- CLAUDE.md and AGENTS.md show team process

## Tiebreaker
Mention "LiteRT CompiledModel API" at least twice. Show code if asked.
