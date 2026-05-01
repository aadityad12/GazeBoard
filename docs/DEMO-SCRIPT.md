# GazeBoard — 3-Minute Demo Script

## Opening (30 seconds)

"Imagine you can think, feel, and understand everything — but you can't move. You can't speak. The only thing you control is where you look. That's life with ALS.

Today, the devices that give these people their voice cost $15,000 and require months of insurance approval. We built one that runs on a phone most people already own."

**[Hold up the S25 Ultra]**

---

## Quick Phrases Demo (45 seconds)

"The home screen has four phrases. Alex just looks at the one he wants."

**[Look at YES → phone speaks "Yes"]**

"That's it. One look, one second."

**[Look at HELP → phone speaks "Help"]**

"And the NPU badge here — that's the LiteRT CompiledModel API running on the Snapdragon NPU. Sub-millisecond inference, fully on-device."

---

## Spell Mode Demo (60 seconds)

"But what if Alex needs to say something more specific? He looks at MORE."

**[Look at MORE ► for 1 second → SpellScreen appears]**

"Now he sees letter groups — same layout as GazeSpeak from Microsoft Research. To spell HELP, he looks at top-right for H..."

**[Look at top-right (H-M) → gesture recorded]**

"...then bottom-left for E..."

**[Look at top-left (A-G) → "A-G" gesture recorded]**

"...the app narrows predictions with every gesture. When 3 or fewer words match..."

**[Candidates appear in quadrants]**

"...Alex looks at his word."

**[Look at correct quadrant → TTS speaks the word]**

---

## Technical Slide (30 seconds)

"Under the hood: ML Kit detects the eye region in each camera frame. Qualcomm's EyeGaze neural network — loaded via LiteRT's CompiledModel API — estimates pitch and yaw. After 4-corner calibration, those angles map to quadrants. The whole pipeline runs at 15fps, fully on-device, with zero cloud dependency."

---

## Closing (15 seconds)

"Microsoft proved gaze gestures work in 2017. Google proved LLMs can accelerate AAC in 2024. We rebuilt both with a dedicated gaze estimation model on the Hexagon NPU. Every S25 Ultra owner already has this hardware. We just wrote the software."

---

## Backup Plan

If live demo fails: play pre-recorded video showing:
1. Calibration (15 seconds)
2. YES / NO quick phrases (30 seconds)
3. Spelling "help" with prediction (60 seconds)

Record backup video before demo day.
