#!/bin/bash
# download_models.sh — Acquire face landmark .tflite model for GazeBoard
#
# Model acquisition priority order:
#   1. On-site provided (ask Qualcomm/Google engineers at the event)
#   2. LiteRT HuggingFace Model Zoo (huggingface.co/litert-community)
#   3. litert-samples repo bundled models
#   4. Qualcomm pre-exported models on HuggingFace (no account needed)
#   5. Stock MediaPipe .tflite via JIT (last resort)
#
# The CompiledModel API JIT-compiles any .tflite for the Hexagon NPU on first
# launch and caches the result. Launch the app once BEFORE the demo to warm it.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_DIR/app/src/main/assets"
MODELS_DIR="$PROJECT_DIR/models"
OUTPUT_NAME="face_landmark.tflite"

echo "============================================================"
echo "GazeBoard — Model Acquisition"
echo "============================================================"
echo ""
echo "Priority order:"
echo "  1. Ask on-site Qualcomm/Google engineers for pre-compiled model"
echo "  2. LiteRT HuggingFace Model Zoo"
echo "  3. Qualcomm models on HuggingFace (no account needed)"
echo "  4. Stock MediaPipe .tflite (JIT on first launch)"
echo ""
echo "============================================================"
echo ""

mkdir -p "$ASSETS_DIR" "$MODELS_DIR"

# --- Option 1: Check if model was already placed manually ---
if [ -f "$ASSETS_DIR/$OUTPUT_NAME" ]; then
    SIZE=$(du -sh "$ASSETS_DIR/$OUTPUT_NAME" | cut -f1)
    echo "[✓] Model already present in assets: $OUTPUT_NAME ($SIZE)"
    echo "    Skipping download."
    echo ""
    print_next_steps
    exit 0
fi

# --- Option 2: Try LiteRT HuggingFace Model Zoo ---
echo "[1/3] Attempting download from LiteRT HuggingFace community..."
LITERT_HF_URL="https://huggingface.co/litert-community/MediaPipe-Face-Landmarker/resolve/main/face_landmarker.tflite"

if curl -fsSL --connect-timeout 10 -o "$ASSETS_DIR/$OUTPUT_NAME" "$LITERT_HF_URL" 2>/dev/null; then
    SIZE=$(du -sh "$ASSETS_DIR/$OUTPUT_NAME" | cut -f1)
    echo "  [✓] Downloaded from LiteRT HuggingFace ($SIZE)"
    cp "$ASSETS_DIR/$OUTPUT_NAME" "$MODELS_DIR/$OUTPUT_NAME"
    print_next_steps
    exit 0
else
    echo "  [ ] LiteRT HuggingFace — model not found at expected URL"
    rm -f "$ASSETS_DIR/$OUTPUT_NAME"
fi

# --- Option 3: Try Qualcomm pre-exported model on HuggingFace (no account needed) ---
echo "[2/3] Attempting download from Qualcomm HuggingFace (no account needed)..."
QUALCOMM_HF_URL="https://huggingface.co/qualcomm/MediaPipe-Face-Detection/resolve/main/MediaPipeFaceDetection.tflite"

if curl -fsSL --connect-timeout 10 -o "$ASSETS_DIR/$OUTPUT_NAME" "$QUALCOMM_HF_URL" 2>/dev/null; then
    SIZE=$(du -sh "$ASSETS_DIR/$OUTPUT_NAME" | cut -f1)
    echo "  [✓] Downloaded from Qualcomm HuggingFace ($SIZE)"
    echo "  NOTE: This is a face DETECTION model (bounding box), not a landmark model."
    echo "  It will NOT produce iris landmark indices 468/473."
    echo "  Use only as a placeholder until you get the correct model on-site."
    cp "$ASSETS_DIR/$OUTPUT_NAME" "$MODELS_DIR/$OUTPUT_NAME"
    print_next_steps
    exit 0
else
    echo "  [ ] Qualcomm HuggingFace — model not found or network error"
    rm -f "$ASSETS_DIR/$OUTPUT_NAME"
fi

# --- Option 4: Stock MediaPipe FaceMesh (JIT fallback, guaranteed to work) ---
echo "[3/3] Downloading stock MediaPipe FaceMesh .tflite (JIT NPU compilation on first launch)..."

# MediaPipe FaceLandmarker task bundle — contains the .tflite inside
MEDIAPIPE_URL="https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
TASK_FILE="$MODELS_DIR/face_landmarker.task"

if curl -fsSL --connect-timeout 30 --progress-bar -o "$TASK_FILE" "$MEDIAPIPE_URL"; then
    echo "  Downloaded .task bundle — extracting .tflite..."

    # The .task file is a zip archive
    if unzip -p "$TASK_FILE" "face_landmarks_detector.tflite" > "$ASSETS_DIR/$OUTPUT_NAME" 2>/dev/null; then
        SIZE=$(du -sh "$ASSETS_DIR/$OUTPUT_NAME" | cut -f1)
        echo "  [✓] Extracted face_landmarks_detector.tflite ($SIZE)"
        cp "$ASSETS_DIR/$OUTPUT_NAME" "$MODELS_DIR/$OUTPUT_NAME"
        rm -f "$TASK_FILE"
    else
        echo "  Extraction failed — trying alternate entry names in .task archive..."
        # List contents and try to find any .tflite
        TFLITE_ENTRY=$(unzip -l "$TASK_FILE" 2>/dev/null | grep "\.tflite" | awk '{print $NF}' | head -1)
        if [ -n "$TFLITE_ENTRY" ]; then
            unzip -p "$TASK_FILE" "$TFLITE_ENTRY" > "$ASSETS_DIR/$OUTPUT_NAME"
            SIZE=$(du -sh "$ASSETS_DIR/$OUTPUT_NAME" | cut -f1)
            echo "  [✓] Extracted $TFLITE_ENTRY ($SIZE)"
            cp "$ASSETS_DIR/$OUTPUT_NAME" "$MODELS_DIR/$OUTPUT_NAME"
            rm -f "$TASK_FILE"
        else
            echo ""
            echo "  ERROR: Could not extract .tflite from .task bundle."
            print_manual_instructions
            exit 1
        fi
    fi
else
    echo ""
    echo "  ERROR: All automatic download attempts failed."
    print_manual_instructions
    exit 1
fi

print_next_steps

# --- Helper functions ---

print_next_steps() {
    echo ""
    echo "============================================================"
    echo "Model ready: app/src/main/assets/$OUTPUT_NAME"
    echo ""
    echo "IMPORTANT: LiteRT JIT-compiles this model for the Hexagon NPU"
    echo "on first launch. Run the app ONCE before your demo to warm"
    echo "the compilation cache. Subsequent launches use the cached"
    echo "compiled model — no JIT delay."
    echo ""
    echo "Next steps:"
    echo "  bash scripts/install_and_run.sh   # build, install, launch (warms JIT)"
    echo "  adb logcat | grep GazeBoard        # watch for 'Accelerator: NPU'"
    echo "============================================================"
}

print_manual_instructions() {
    echo "============================================================"
    echo "MANUAL MODEL ACQUISITION — do one of the following:"
    echo ""
    echo "Option A (fastest — at the hackathon):"
    echo "  Ask the Qualcomm or Google engineers on-site for a"
    echo "  face landmark .tflite compatible with CompiledModel API."
    echo "  Copy it to: app/src/main/assets/face_landmark.tflite"
    echo ""
    echo "Option B (litert-samples repo):"
    echo "  git clone https://github.com/google-ai-edge/litert-samples"
    echo "  Find any .tflite in the NPU sample apps"
    echo "  Copy to: app/src/main/assets/face_landmark.tflite"
    echo ""
    echo "Option C (HuggingFace manual search):"
    echo "  Browse: https://huggingface.co/litert-community"
    echo "  Browse: https://huggingface.co/qualcomm"
    echo "  Download any face landmark .tflite"
    echo "  Copy to: app/src/main/assets/face_landmark.tflite"
    echo ""
    echo "After placing the file, run:"
    echo "  bash scripts/install_and_run.sh"
    echo "============================================================"
}
