#!/bin/bash
# download_models.sh — Verify or acquire the EyeGaze .tflite model for GazeBoard
#
# PRIMARY MODEL: qualcomm/EyeGaze (already in models/mediapipe_face-tflite-float/eyegaze.tflite)
# This script copies it to app/src/main/assets/ and verifies the asset is in place.
#
# The CompiledModel API JIT-compiles the model for the Hexagon NPU on first launch
# and caches the result. Run install_and_run.sh once before the demo to warm the cache.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_DIR/app/src/main/assets"
MODELS_DIR="$PROJECT_DIR/models/mediapipe_face-tflite-float"
OUTPUT_NAME="eyegaze.tflite"

echo "============================================================"
echo "GazeBoard — EyeGaze Model Setup"
echo "============================================================"
echo ""

mkdir -p "$ASSETS_DIR"

# --- Primary: copy from models/ directory (already in repo) ---
if [ -f "$MODELS_DIR/$OUTPUT_NAME" ]; then
    echo "[1/2] Found model in models/: $OUTPUT_NAME"
    cp "$MODELS_DIR/$OUTPUT_NAME" "$ASSETS_DIR/$OUTPUT_NAME"
    SIZE=$(du -sh "$ASSETS_DIR/$OUTPUT_NAME" | cut -f1)
    echo "[2/2] Copied to app/src/main/assets/ ($SIZE)"
    print_next_steps
    exit 0
fi

# --- Fallback: already in assets ---
if [ -f "$ASSETS_DIR/$OUTPUT_NAME" ]; then
    SIZE=$(du -sh "$ASSETS_DIR/$OUTPUT_NAME" | cut -f1)
    echo "[✓] Model already in app/src/main/assets/: $OUTPUT_NAME ($SIZE)"
    print_next_steps
    exit 0
fi

echo "  ERROR: eyegaze.tflite not found."
echo "  Expected at: models/mediapipe_face-tflite-float/eyegaze.tflite"
echo "  or:          app/src/main/assets/eyegaze.tflite"
echo ""
echo "  The model should already be in the repo. Run:"
echo "    git status && git pull"
echo ""
print_next_steps

# --- Helper functions ---

print_next_steps() {
    echo ""
    echo "============================================================"
    echo "Model ready: app/src/main/assets/eyegaze.tflite"
    echo ""
    echo "  Model: qualcomm/EyeGaze"
    echo "  Input:  [1, 96, 160] float32 grayscale"
    echo "  Output: gaze_pitchyaw [1, 2] — pitch and yaw in radians"
    echo ""
    echo "IMPORTANT: LiteRT JIT-compiles for the Hexagon NPU on first"
    echo "launch. Run install_and_run.sh ONCE before the demo."
    echo ""
    echo "Next step:"
    echo "  bash scripts/install_and_run.sh"
    echo "  adb logcat | grep GazeBoard  # look for 'confirmed NPU execution'"
    echo "============================================================"
}
