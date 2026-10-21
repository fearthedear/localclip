#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
TOOLS="$SDK/build-tools/36.0.0"
BUILD="${BUILD_DIR:-$ROOT/.build/android}"
mkdir -p "$BUILD/test-classes" "$BUILD/test-dex"
"$TOOLS/aapt2" link -o "$BUILD/test-unsigned.apk" -I "$SDK/platforms/android-36/android.jar" --manifest "$ROOT/tests/android/AndroidManifest.xml" --debug-mode
find "$ROOT/tests/android" -name '*.java' | sort > "$BUILD/test-sources.txt"
javac -source 8 -target 8 -Xlint:-options -cp "$SDK/platforms/android-36/android.jar:$BUILD/classes" -d "$BUILD/test-classes" @"$BUILD/test-sources.txt"
find "$BUILD/test-classes" -name '*.class' | sort > "$BUILD/test-classes.txt"
"$TOOLS/d8" --min-api 29 --lib "$SDK/platforms/android-36/android.jar" --classpath "$BUILD/classes" --output "$BUILD/test-dex" @"$BUILD/test-classes.txt"
(cd "$BUILD/test-dex" && zip -q -u "$BUILD/test-unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f 4 "$BUILD/test-unsigned.apk" "$BUILD/test-aligned.apk"
"$TOOLS/apksigner" sign --ks "$BUILD/debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias localclip-debug --out "$BUILD/LocalClip-tests.apk" "$BUILD/test-aligned.apk"
echo "Built $BUILD/LocalClip-tests.apk (test fixture only)"
