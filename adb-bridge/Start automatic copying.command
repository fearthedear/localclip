#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
fail(){ echo "$*" >&2; exit 1; }
ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  if command -v adb >/dev/null 2>&1; then ADB="$(command -v adb)"
  elif [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then ADB="$HOME/Library/Android/sdk/platform-tools/adb"
  else fail "Install Android SDK Platform Tools, or set ADB to its adb executable."; fi
fi
if [ -n "${ANDROID_SERIAL:-}" ]; then SERIAL="$ANDROID_SERIAL"
else
  DEVICES="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
  [ -n "$DEVICES" ] || fail "No authorized phone found. Connect USB and accept USB debugging, or pair Wireless debugging first."
  [ "$(printf '%s\n' "$DEVICES" | wc -l | tr -d ' ')" = 1 ] || fail "Multiple devices found. Set ANDROID_SERIAL to your phone's serial."
  SERIAL="$DEVICES"
fi
USER_ID="$("$ADB" -s "$SERIAL" shell am get-current-user | tr -d '\r')"
[ "$USER_ID" = 0 ] || fail "Only the main Android user (0) is supported."
SDK="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$SDK" =~ ^[0-9]+$ ]] && [ "$SDK" -ge 34 ] || fail "Android 14 or newer is required."
PACKAGE="$("$ADB" -s "$SERIAL" shell cmd package list packages -U --user 0 org.localclip.android | tr -d '\r')"
[[ "$PACKAGE" =~ ^package:org\.localclip\.android\ uid:([0-9]+)$ ]] || fail "Install the LocalClip APK in the main user first."
APP_UID="${BASH_REMATCH[1]}"
[ -f clipboard-bridge.jar ] || fail "Keep this launcher next to clipboard-bridge.jar."
echo "On the phone: Connect to Mac, then enable Automatic copying with Gboard."
echo "Starting the helper. Disable that option or disconnect LocalClip to stop it. Restart after a phone reboot."
"$ADB" -s "$SERIAL" push clipboard-bridge.jar /data/local/tmp/localclip-bridge.jar
"$ADB" -s "$SERIAL" shell "run-as org.localclip.android cat files/adb-bridge-token | CLASSPATH=/data/local/tmp/localclip-bridge.jar app_process /system/bin localclip.bridge.ClipboardBridge"
