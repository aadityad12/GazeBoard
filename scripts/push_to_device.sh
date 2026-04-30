#!/bin/bash
# push_to_device.sh — Push compiled TFLite model to Samsung Galaxy S25 Ultra via ADB
# Usage: bash scripts/push_to_device.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MODEL_NAME="face_landmark_compiled.tflite"
MODEL_PATH="$PROJECT_DIR/models/$MODEL_NAME"
DEVICE_PATH="/sdcard/Download/$MODEL_NAME"

echo "============================================================"
echo "GazeBoard — Push Model to Device"
echo "============================================================"

# 1. Check adb is available
if ! command -v adb &> /dev/null; then
    echo "ERROR: 'adb' not found in PATH."
    echo "Install Android SDK Platform Tools:"
    echo "  https://developer.android.com/tools/releases/platform-tools"
    exit 1
fi

# 2. Check device is connected
echo ""
echo "[1/4] Checking ADB device connection..."
DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$")

if [ -z "$DEVICES" ]; then
    echo "ERROR: No ADB devices found."
    echo "Steps to fix:"
    echo "  1. Connect S25 Ultra via USB"
    echo "  2. Enable Developer Options: Settings → About Phone → tap Build Number 7 times"
    echo "  3. Enable USB Debugging: Settings → Developer Options → USB Debugging"
    echo "  4. Accept the 'Allow USB debugging?' prompt on device"
    exit 1
fi

DEVICE_COUNT=$(echo "$DEVICES" | wc -l)
if [ "$DEVICE_COUNT" -gt 1 ]; then
    echo "WARNING: Multiple devices connected. Using first device."
    echo "  To target a specific device: adb -s <serial> ..."
fi

echo "  Device(s) found:"
echo "$DEVICES" | while IFS= read -r line; do echo "    $line"; done

# 3. Check model file exists
echo ""
echo "[2/4] Checking model file..."
if [ ! -f "$MODEL_PATH" ]; then
    echo "ERROR: Model not found at: $MODEL_PATH"
    echo ""
    echo "Run the export script first:"
    echo "  python scripts/export_models.py"
    echo ""
    echo "Or manually download and place the file at:"
    echo "  $MODEL_PATH"
    exit 1
fi

MODEL_SIZE=$(du -sh "$MODEL_PATH" | cut -f1)
echo "  Found: $MODEL_NAME ($MODEL_SIZE)"

# 4. Push to device
echo ""
echo "[3/4] Pushing model to device..."
echo "  Source: $MODEL_PATH"
echo "  Dest:   $DEVICE_PATH"

adb push "$MODEL_PATH" "$DEVICE_PATH"

# 5. Verify push succeeded
echo ""
echo "[4/4] Verifying push..."
REMOTE_SIZE=$(adb shell stat -c %s "$DEVICE_PATH" 2>/dev/null || echo "0")
LOCAL_SIZE=$(stat -f%z "$MODEL_PATH" 2>/dev/null || stat -c%s "$MODEL_PATH" 2>/dev/null || echo "0")

if [ "$REMOTE_SIZE" -eq "$LOCAL_SIZE" ] && [ "$LOCAL_SIZE" -gt 0 ]; then
    echo "  ✓ Verified: remote size matches local ($REMOTE_SIZE bytes)"
else
    echo "  WARNING: Size mismatch — local=$LOCAL_SIZE, remote=$REMOTE_SIZE"
    echo "  The push may have been incomplete. Try again."
    exit 1
fi

echo ""
echo "============================================================"
echo "SUCCESS — Model pushed to device."
echo ""
echo "The Android app loads the model from assets/, not from"
echo "/sdcard/. This push is for manual verification only."
echo ""
echo "To include the model in the app bundle:"
echo "  cp models/$MODEL_NAME app/src/main/assets/$MODEL_NAME"
echo "  ./gradlew assembleDebug"
echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To verify NPU execution after installing:"
echo "  adb logcat | grep GazeBoard"
echo "  Look for: [GazeBoard] Accelerator: NPU"
echo "============================================================"
