#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
TOOLS="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
BUILD="${BUILD_DIR:-$ROOT/.build/android}"
DEST="${OUTPUT_DIR:-$ROOT/../android}"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$DEST"
rm -rf "$BUILD/classes" "$BUILD/dex"
mkdir -p "$BUILD/classes" "$BUILD/dex"
"$TOOLS/aapt2" compile --dir "$ROOT/android/res" -o "$BUILD/resources.zip"
"$TOOLS/aapt2" link -o "$BUILD/unsigned.apk" -I "$JAR" --manifest "$ROOT/android/AndroidManifest.xml" "$BUILD/resources.zip" --version-code 7 --version-name 0.4.2 --debug-mode
find "$ROOT/android/src" -name '*.java' | LC_ALL=C sort > "$BUILD/sources.txt"
javac -source 8 -target 8 -Xlint:-options -cp "$JAR" -d "$BUILD/classes" @"$BUILD/sources.txt"
find "$BUILD/classes" -name '*.class' | LC_ALL=C sort > "$BUILD/classes.txt"
"$TOOLS/d8" --min-api 29 --lib "$JAR" --output "$BUILD/dex" @"$BUILD/classes.txt"
(cd "$BUILD/dex" && zip -q -u "$BUILD/unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
# A local development key; never intended for Play Store release signing.
if [ ! -f "$BUILD/debug.keystore" ]; then
  keytool -genkeypair -keystore "$BUILD/debug.keystore" -storepass android -keypass android -alias localclip-debug -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=LocalClip development' >/dev/null 2>&1
  chmod 600 "$BUILD/debug.keystore"
fi
"$TOOLS/apksigner" sign --ks "$BUILD/debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias localclip-debug --out "$DEST/LocalClip-debug.apk" "$BUILD/aligned.apk"
"$TOOLS/apksigner" verify --verbose --print-certs "$DEST/LocalClip-debug.apk"
echo "Built $DEST/LocalClip-debug.apk"
