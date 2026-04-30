#!/bin/bash
# install_and_run.sh — Build, install, and launch GazeBoard on connected S25 Ultra.
#
# First launch warms the LiteRT JIT compilation cache for the Hexagon NPU.
# Run this BEFORE the demo so the compiled model is cached and ready.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
APK_DEBUG="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.gazeboard"
MAIN_ACTIVITY=".MainActivity"

echo "============================================================"
echo "GazeBoard — Build, Install & Launch"
echo "============================================================"

# 1. Check ADB
echo ""
echo "[1/4] Checking ADB device connection..."
if ! command -v adb &> /dev/null; then
    echo "  ERROR: 'adb' not found. Install Android SDK Platform Tools."
    exit 1
fi

DEVICES=$(adb devices | grep -v "List of devices" | grep "device$")
if [ -z "$DEVICES" ]; then
    echo "  ERROR: No ADB device found."
    echo "  Make sure S25 Ultra is connected, USB debugging is enabled,"
    echo "  and you accepted the 'Allow USB debugging?' prompt on device."
    exit 1
fi
echo "  Device connected: $(echo "$DEVICES" | head -1 | awk '{print $1}')"

# 2. Verify model asset exists
echo ""
echo "[2/4] Checking model asset..."
MODEL_ASSET="$PROJECT_DIR/app/src/main/assets/face_landmark.tflite"
if [ ! -f "$MODEL_ASSET" ]; then
    echo "  ERROR: Model not found at app/src/main/assets/face_landmark.tflite"
    echo "  Run first: bash scripts/download_models.sh"
    exit 1
fi
MODEL_SIZE=$(du -sh "$MODEL_ASSET" | cut -f1)
echo "  Found: face_landmark.tflite ($MODEL_SIZE)"

# 3. Build debug APK
echo ""
echo "[3/4] Building debug APK..."
cd "$PROJECT_DIR"
./gradlew :app:assembleDebug --quiet
if [ ! -f "$APK_DEBUG" ]; then
    echo "  ERROR: Build failed — APK not found at expected path."
    exit 1
fi
APK_SIZE=$(du -sh "$APK_DEBUG" | cut -f1)
echo "  Build successful: app-debug.apk ($APK_SIZE)"

# 4. Install and launch
echo ""
echo "[4/4] Installing and launching on device..."
adb install -r "$APK_DEBUG"
echo "  Installed."

# Launch the app — this triggers LiteRT JIT compilation on first run
adb shell am start -n "$PACKAGE/$PACKAGE$MAIN_ACTIVITY"
echo "  Launched GazeBoard."
echo ""
echo "  Watching Logcat for NPU initialization..."
echo "  (Ctrl+C to stop watching, app continues running)"
echo ""
adb logcat -s "GazeBoard" --line-buffered 2>/dev/null | head -20 &
LOGCAT_PID=$!
sleep 8
kill $LOGCAT_PID 2>/dev/null || true

echo ""
echo "============================================================"
echo "Done. GazeBoard is running on device."
echo ""
echo "First launch warms the LiteRT NPU compilation cache."
echo "The app is now ready for demo — launch again from the"
echo "device home screen when it's time to present."
echo ""
echo "To watch live logs during demo:"
echo "  adb logcat | grep GazeBoard"
echo ""
echo "Expected: [GazeBoard] Accelerator: NPU"
echo "Warning:  [GazeBoard] WARNING: Running on CPU"
echo "============================================================"
