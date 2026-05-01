# GazeBoard

**Eye-gaze AAC communication for people with ALS — powered by on-device AI**

GazeBoard turns a Samsung Galaxy S25 Ultra into a free, portable AAC device for people with ALS, locked-in syndrome, or severe motor disabilities. Using Qualcomm's EyeGaze neural network on the Snapdragon NPU via Google's LiteRT CompiledModel API, GazeBoard detects where the user is looking and converts eye dwells into speech — completely offline.

Inspired by Microsoft Research's [GazeSpeak](https://dl.acm.org/doi/10.1145/3025453.3025790) (CHI 2017) and Google's [SpeakFaster](https://www.nature.com/articles/s41467-024-53873-3) (Nature Communications 2024).

**Built for the Qualcomm × LiteRT Developer Hackathon, April 30–May 1, 2026.**

---
## Team Members
Aaditya Desai - <aaditya.desai@sjsu.edu>
Nishanth Nagesh - <nishanth.nagesh@sjsu.edu>
Sheel Shah - <shahxsheel@gamil.com>

---
## The Problem

Dedicated AAC devices cost $8,000–$15,000, require months of insurance approval, and don't work outdoors. Over 500,000 people in the US alone could benefit from gaze-controlled AAC. Every S25 Ultra owner already has the hardware.

---

## How It Works

### Quick Phrases Screen (Home)

Look at a quadrant for 1 second — the phone speaks it.

```
┌─────────────┬─────────────┐
│     YES     │     NO      │
├─────────────┼─────────────┤
│    HELP     │   MORE ►   │
└─────────────┴─────────────┘
```

### Spell Mode

Look at letter groups to narrow your word (GazeSpeak layout):

```
┌─────────────┬─────────────┐
│  A B C D    │  H I J K   │
│    E F G    │    L M      │
├─────────────┼─────────────┤
│  N O P Q    │  T U V W   │
│    R S      │  X Y Z      │
└─────────────┴─────────────┘
```

After 2–4 gestures, the ALS-focused word predictor narrows candidates (~300 curated words covering medical, comfort, and communication needs). When ≤3 remain, they appear in quadrants for direct selection.

---

## Architecture

```
Front Camera → ML Kit FaceDetector → eye crop
  → EyeGaze model (LiteRT CompiledModel, Snapdragon NPU)
  → pitch/yaw → CalibrationEngine.mapToQuadrant()
  → dwell timer (1s) → TriePredictor → TTS
```

**LiteRT CompiledModel API (hackathon eligibility gate):**
```kotlin
// NPU only — no CPU/GPU fallback
val model = CompiledModel.create(
    context.assets, "eyegaze.tflite",
    CompiledModel.Options(Accelerator.NPU)
)
model.run(inputBuffers, outputBuffers)
val (pitch, yaw) = outputBuffers[2].readFloat()
```

---

## Setup

### Prerequisites
- Android Studio Meerkat or later
- Samsung Galaxy S25 Ultra with USB debugging enabled

### Build & Run
```bash
git clone https://github.com/aadityad12/GazeBoard.git && cd GazeBoard
bash scripts/install_and_run.sh
```

Or manually:
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.gazeboard/.MainActivity
```

### First Launch
1. Grant camera permission
2. Calibrate: look at each of 4 corner targets for 1.5 seconds (~15 seconds total)
3. Calibration is saved to SharedPreferences — no need to recalibrate after restart

---

## Privacy

Fully offline. No internet, no data storage, no analytics. Works in airplane mode.

---

## References

- Zhang et al. (2017). GazeSpeak: Smartphone-Based Gaze Gesture Communication. *CHI '17*. https://doi.org/10.1145/3025453.3025790
- Cai et al. (2024). Using LLMs to accelerate AAC for ALS. *Nature Communications*, 15, 9449.
- qualcomm/EyeGaze: https://huggingface.co/qualcomm/EyeGaze

---

## License

Apache 2.0 — see [LICENSE](LICENSE)
