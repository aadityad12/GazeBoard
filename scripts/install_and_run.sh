#!/bin/bash
set -e

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"

echo "Building GazeBoard APK..."
./gradlew assembleDebug

echo "Installing on device..."
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk

echo "Launching GazeBoard..."
"$ADB" shell am start -n com.gazeboard/.MainActivity

echo ""
echo "Check accelerator: $ADB logcat -s GazeBoard"
echo "Look for: 'EyeGaze model loaded on NPU' or 'CPU'"
