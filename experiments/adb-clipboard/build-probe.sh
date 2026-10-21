#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BUILD="${BUILD_DIR:-$ROOT/.build/adb-probe}"
rm -rf "$BUILD/classes" "$BUILD/dex"
mkdir -p "$BUILD/classes" "$BUILD/dex"
javac -source 8 -target 8 -Xlint:-options -cp "$SDK/platforms/android-36/android.jar" -d "$BUILD/classes" "$ROOT/experiments/adb-clipboard/ClipboardProbe.java"
find "$BUILD/classes" -name '*.class' | sort > "$BUILD/classes.txt"
"$SDK/build-tools/36.0.0/d8" --min-api 29 --lib "$SDK/platforms/android-36/android.jar" --output "$BUILD/dex" @"$BUILD/classes.txt"
(cd "$BUILD/dex" && zip -q "$BUILD/clipboard-probe.jar" classes.dex)
echo "$BUILD/clipboard-probe.jar"
