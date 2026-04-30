# GazeBoard — Product Requirements Document

## Problem Statement

Dedicated AAC (Augmentative and Alternative Communication) devices that give a voice to people with ALS, locked-in syndrome, or severe motor disabilities cost **$8,000–$15,000**. Devices like the Tobii Dynavox cost $12,000+, require insurance prior authorization (often taking 6–18 months), and arrive as bulky wheelchair-mounted hardware. In the meantime, patients wait in silence.

**500,000+ people in the United States** — and millions more globally — live with conditions that eliminate their ability to speak or move, including:
- ALS (Amyotrophic Lateral Sclerosis)
- Locked-in syndrome
- Cerebral palsy (severe)
- Late-stage MS
- Brainstem stroke

Every Samsung Galaxy S25 Ultra owner already has a front-facing camera, a powerful Hexagon NPU, and speakers. The hardware exists in millions of pockets. Only the software is missing.

---

## Solution

**GazeBoard** is a free, fully on-device, eye-gaze-controlled communication board that runs on a consumer Android phone. No internet. No cloud. No $15,000 device. Just a phone and your eyes.

The user looks at one of 6 large phrase cells for approximately 1.5 seconds. The app detects the face with Android `FaceDetector`, crops the eye region, and runs `eyegaze.tflite` on the Hexagon NPU via LiteRT's `CompiledModel` API. The model outputs gaze pitch/yaw angles, which are calibrated to screen coordinates. When the dwell threshold is reached, the phrase is spoken aloud via Android TTS.

**This is not a keyboard. It is not a sentence builder.** It is the fastest possible communication primitive: a small vocabulary of high-frequency phrases, selectable by gaze alone, on hardware a family can already afford.

---

## User Persona

**Alex, 42, ALS patient — diagnosed 14 months ago**

Alex was an architect. He can still think, read, and feel. He understands every word of a conversation. But he cannot move his hands, arms, or face. His voice is gone. The muscles that control his eyes are — for now — intact.

Alex currently uses a $12,000 Tobii Dynavox mounted on his wheelchair, funded after 11 months of insurance battles. His family cannot afford a second unit for home use. He cannot take it to a restaurant. When the battery dies in the middle of a sentence, he has no backup.

GazeBoard gives Alex a second device. Or a first device for the person who is still waiting for insurance approval.

---

## Core User Flow

```
1. Launch GazeBoard
        ↓
2. Calibration Screen
   → Red dot appears at top-left corner
   → "Look at the dot" prompt
   → User holds gaze for 1.5s → dot captured
   → Repeats for top-right, bottom-left, bottom-right
   → "Calibration complete!" → auto-advance
        ↓
3. Communication Board (full screen, 2×3 grid)
   → 6 large cells fill the screen
   → Gaze cursor (white circle) tracks eye position
   → User looks at "I need water"
   → Progress ring fills over 1.5 seconds
   → Phone speaks: "I need water"
   → 0.5s cooldown → board resets → ready again
```

Total time from launch to first utterance: **< 30 seconds** (including calibration).

---

## MVP Feature Set (Must-Have for Demo)

| Feature | Description | Owner |
|---------|-------------|-------|
| 2×3 phrase grid | 6 full-screen cells, readable at arm's length | Person C |
| Gaze cursor overlay | Circular cursor following calibrated gaze point | Person B + C |
| Dwell timer with progress ring | 1.5s threshold, animated ring per cell | Person C |
| TTS output | Speaks selected phrase, pre-warmed on launch | Person C |
| 4-point calibration screen | Corner calibration, computes affine transform | Person B + C |
| NPU profiler overlay | Shows accelerator type (NPU/GPU/CPU) + inference ms | Person A + C |
| Face-not-detected indicator | Shows warning overlay when face leaves frame | Person B + C |
| Offline operation | Zero network calls, works in airplane mode | All |

---

## Stretch Features (implement only if MVP complete before Hour 16)

| Feature | Priority | Description |
|---------|----------|-------------|
| Improved eye crop tuning | P1 stretch | Better crop heuristics or a stronger eye detector if Android `FaceDetector` is unreliable |
| Head tilt page scroll | P2 stretch | Tilt head left/right to reveal second page of 6 phrases |
| Phrase customization UI | P2 stretch | Long-press a cell to edit its phrase text |
| Blink-to-select mode | P3 stretch | Double-blink (EAR < 0.2 twice within 0.5s) as alternative to dwell |
| Usage analytics overlay | P3 stretch | Show phrase frequency heatmap for caregivers |

---

## Non-Goals (Explicitly Out of Scope)

- Full QWERTY keyboard
- Sentence composition or word prediction
- Multi-language TTS or phrase sets
- Cloud sync or user accounts
- Firebase, analytics, or any network component
- Support for devices other than Samsung Galaxy S25 Ultra (during hackathon)
- Wheelchair mounting adapter or accessibility hardware integration

---

## Default Phrase Set

The 6 default phrases are selected for maximum real-world frequency in clinical and home settings:

| Cell | Phrase |
|------|--------|
| 0 (top-left) | Yes |
| 1 (top-center) | No |
| 2 (top-right) | Help |
| 3 (bottom-left) | Thank you |
| 4 (bottom-center) | I need water |
| 5 (bottom-right) | Call nurse |

---

## Success Criteria

**Minimum viable demo success:** A judge sits in front of the S25 Ultra, completes 4-point calibration in under 10 seconds, and successfully selects 3 different phrases in under 60 seconds without assistance.

**Strong demo success:** All of the above, plus the judge can see the NPU inference badge showing sub-10ms latency, and the gaze cursor visibly tracks their eyes in real time.

**Winning demo success:** All of the above, plus a judge with zero prior familiarity successfully uses the app independently within 2 minutes of first contact.

---

## Constraints

- **Hardware:** Samsung Galaxy S25 Ultra only (Snapdragon 8 Elite, SM8750)
- **Model runtime:** LiteRT `CompiledModel` API, `Accelerator.NPU` preferred with visible GPU/CPU fallback
- **Connectivity:** None — must work in airplane mode
- **Install:** Single APK, no setup wizard, no account creation
- **Time:** 24-hour build window (April 30–May 1, 2026)
