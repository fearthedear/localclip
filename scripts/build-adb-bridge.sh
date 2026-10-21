#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BUILD="${BUILD_DIR:-$ROOT/.build/adb-bridge}"
DEST="${OUTPUT_DIR:-$ROOT/../adb-bridge}"
rm -rf "$BUILD/classes" "$BUILD/dex"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$DEST"
javac -source 8 -target 8 -Xlint:-options -cp "$SDK/platforms/android-36/android.jar" -d "$BUILD/classes" "$ROOT/adb-bridge/src/localclip/bridge/ClipboardBridge.java" "$ROOT/android/src/localclip/Wire.java"
find "$BUILD/classes" -name '*.class' | LC_ALL=C sort > "$BUILD/classes.txt"
"$SDK/build-tools/36.0.0/d8" --min-api 34 --lib "$SDK/platforms/android-36/android.jar" --output "$BUILD/dex" @"$BUILD/classes.txt"
(cd "$BUILD/dex" && zip -q "$BUILD/clipboard-bridge.jar" classes.dex)
cp "$BUILD/clipboard-bridge.jar" "$DEST/clipboard-bridge.jar"
cp "$ROOT/adb-bridge/Start automatic copying.command" "$DEST/Start automatic copying.command"
chmod +x "$DEST/Start automatic copying.command"
echo "Built $DEST/clipboard-bridge.jar"
