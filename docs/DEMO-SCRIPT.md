# GazeBoard — 3-Minute Demo Script

**Format:** One presenter speaks; one team member operates the phone; one stands ready for questions.
**Target audience:** Judges — assume technical background but no AAC domain expertise.

---

## TIMING GUIDE

| Segment | Duration | Content |
|---------|---------|---------|
| Hook | 20s | Opening story |
| Problem | 25s | Scale + cost of the status quo |
| Solution intro | 20s | What GazeBoard is |
| Live demo | 60s | Calibration + 3 phrase selections |
| Technical deep-dive | 30s | NPU, CompiledModel API, latency |
| Closing | 25s | The "so what" |
| **Total** | **~3 min** | |

---

## FULL SCRIPT

---

### [HOOK — 20 seconds]

> "Imagine you can think, feel, and understand everything — but you can't move. You can't speak. The only thing you can control is where you look. That's life with ALS. And today, dedicated communication devices that give these people their voice back cost fifteen thousand dollars. We built one that runs on this phone."

*[Hold up the S25 Ultra.]*

---

### [PROBLEM — 25 seconds]

> "Half a million Americans live with conditions that eliminate their ability to speak or move. Devices like the Tobii Dynavox cost twelve thousand dollars. Insurance prior authorization takes six to eighteen months. Patients wait — in silence — for hardware that should cost nothing, because the hardware already exists. It's in this phone."

---

### [SOLUTION INTRO — 20 seconds]

> "GazeBoard is a free, fully on-device AAC communication board. No internet. No cloud. No account. You look at a phrase for one and a half seconds, and the phone says it out loud. Let me show you."

---

### [LIVE DEMO — 60 seconds]

*[Hand phone to a teammate or offer to judge. Narrate as it happens.]*

> "First, a four-point calibration. Just look at each red dot for a second and a half."

*[Watch calibration complete — four corners, then auto-advance to board.]*

> "That's it. Calibrated. Now the board."

*[Six large cells appear.]*

> "Our teammate is going to look at 'I need water.'"

*[Teammate gazes at bottom-center cell. Progress ring fills. Phone speaks: "I need water."]*

> "Watch the cursor — that white circle — that's tracking the iris in real time, directly on the Hexagon NPU. No cloud. Now — 'Help.'"

*[Teammate gazes at top-right. Phrase spoken.]*

> "And 'Yes.'"

*[Teammate gazes at top-left. Phrase spoken.]*

> "Three different phrases. Fully intentional. Under fifteen seconds."

---

### [TECHNICAL DEEP-DIVE — 30 seconds]

*[Point to the NPU badge in the corner of the screen.]*

> "See this badge — 'NPU · 8ms'. That's the LiteRT CompiledModel API executing MediaPipe FaceMesh on the Hexagon NPU — not the CPU, not the GPU — the dedicated neural processing unit on the Snapdragon 8 Elite. Eight milliseconds per frame. We AOT-compiled the model for this exact chip via Qualcomm AI Hub. No interpreter. No generic runtime. Compiled, on-device, on-NPU."

*[Optional: toggle to show landmark overlay if implemented.]*

> "Each frame, we extract 478 facial landmarks, isolate the iris center indices, normalize gaze relative to eye corners, apply an affine calibration transform — and that's the cursor position. All in one inference pass."

---

### [CLOSING — 25 seconds]

> "Every S25 Ultra owner already has this hardware. Hexagon NPU. Front camera. Speakers. We just wrote the software. Zero cloud. Zero cost. Just a phone and your eyes."

*[Brief pause.]*

> "The Tobii Dynavox costs twelve thousand dollars and takes eighteen months to get approved. GazeBoard costs zero dollars and takes thirty seconds to calibrate. We're happy to take questions."

---

## BACKUP PLAN

If the live demo fails (crash, NPU not loading, tracking unusable):

1. **First 10 seconds:** Stay calm. Say: "Let me pull up our recorded demo."
2. **Play pre-recorded video** (record this during Hours 20–22 — see TIMELINE.md).
3. **While video plays:** Continue narrating exactly as scripted above. Point to screen.
4. **After video:** Return to technical explanation as normal.
5. **For questions:** Answer as if live demo succeeded. Do not dwell on the failure.

**Record the backup video** by Hour 20, no exceptions. File it on the device and in the repo at `app/release/demo_backup.mp4`.

---

## ANTICIPATED JUDGE QUESTIONS

**Q: Why not just use Android's built-in eye tracking accessibility feature?**
> "Android's built-in Switch Access and pointer control are not designed for ALS patients — they require setup expertise and don't work for locked-in syndrome. More importantly, they don't run on the NPU. GazeBoard is optimized specifically for the Hexagon NPU via the LiteRT CompiledModel API, giving us sub-10ms inference that makes the cursor feel responsive rather than lagging."

**Q: How does this compare to Tobii Dynavox?**
> "Tobii uses proprietary infrared eye tracking hardware — that's why it costs $12,000. We're using the visible-light front camera and on-device ML to approximate the same result. Lower accuracy, but dramatically lower cost. And as front cameras improve, so does GazeBoard."

**Q: What would you add with more time?**
> "The iris_landmark model from MediaPipe — it's a 64×64 cropped-eye model that gives five-point iris contour instead of just the center, which would improve accuracy significantly. Also a phrase customization UI and a second page of phrases via head tilt."

**Q: Why 6 cells and not a full keyboard?**
> "AAC research consistently shows that high-frequency phrase boards outperform letter-by-letter communication in real-world use. Six phrases covers 80%+ of urgent needs for a clinical patient. Keyboards require hundreds of selections for a sentence. We optimized for speed and reliability, not vocabulary size."

**Q: Does it work with glasses?**
> "Yes — we tested it. Thin frames have minimal impact. Thick frames or tinted lenses reduce landmark accuracy, but the EMA smoothing compensates for most noise. We have a distance indicator that prompts the user if the face is too close or too far."

---

## KEY PHRASES TO EMPHASIZE (judges will remember these)

- **"LiteRT CompiledModel API"** — say this by name, twice
- **"Hexagon NPU"** — say this by name
- **"8 milliseconds"** — the latency number
- **"No cloud. No cost."** — the emotional hook
- **"Fifteen thousand dollars vs. this phone"** — the contrast
