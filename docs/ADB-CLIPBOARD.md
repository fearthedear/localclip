# Automatic copying with Gboard — LocalClip 0.4.0

**Implemented and tested on Android 17/API 37 with Gboard in an arm64 emulator.** A separate, rootless ADB shell helper listens for actual clipboard changes. Copying supported text or an image in another app sends it through LocalClip's encrypted Mac connection without Share or opening LocalClip. Gboard stays selected. This observes clipboard changes, not keyboard keystrokes.

The ordinary APK cannot read arbitrary background copies by itself. The helper is optional and requires explicit debugging authorization. Physical Pixel 11 Pro XL behavior, long idle/Doze and survival after unplugging USB remain unverified.

## Setup on your phone and Mac

1. Install `LocalClip-debug.apk` 0.4.0 over the existing version. Pair and **Connect to Mac** as usual. The existing Mac app remains compatible.
2. In Android LocalClip, enable **Allow automatic copying through the ADB helper**. Its status initially says **Waiting for ADB helper**.
3. Extract `LocalClip-adb-bridge.zip` on the Mac. Keep its launcher, JAR and README together. Install Google's [SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) if `adb` is not already installed. The launcher also finds Android Studio's usual Mac SDK location.
4. For USB setup: enable **Developer options → USB debugging**, connect the phone, unlock it, and accept this Mac's debugging prompt. These are Android debugging permissions, separate from LocalClip pairing.
5. Double-click **Start automatic copying.command** on the Mac. The Terminal should report **Automatic copying active on Android API 37**. The Android app independently shows **Automatic copying active** when the encrypted helper session is established.
6. Return to another phone app, copy new text, and paste on the Mac. Copying an image works when the source app places a readable image URI on the clipboard. Taking a screenshot alone is not a copy; use the screenshot's Copy action or Share → LocalClip.

The launcher remains running in Terminal. Keep it open for this MVP. If macOS requires its normal approval to open the downloaded launcher, use the system's Open/Open Anyway flow. No macOS Accessibility or screen-recording permission is required.

### Wireless debugging instead of USB

On the same Wi-Fi, enable **Developer options → Wireless debugging → Pair using pairing code**. Use the displayed pairing address with `adb pair IP:PAIRING_PORT` and enter the phone's code. Verify the device appears in `adb devices`; if needed, use `adb connect IP:DEBUGGING_PORT` with the address shown on the main Wireless debugging screen. The two ports differ. Then run the same launcher. See Google's [ADB wireless setup](https://developer.android.com/tools/adb#wireless-android11-command-line).

Wireless debugging setup and reconnect behavior were documented from Google, but were not exercised on a physical phone. LocalClip's clipboard connection can independently use Wi-Fi or Tailscale; the helper runs locally on the phone and does not route clipboard data through ADB.

### Stop and restart

- Disable the automatic-copying option, or tap **Disconnect / pause sharing** in LocalClip. The app closes the helper connection, deletes its temporary key file and the helper exits.
- After a phone reboot, LocalClip force-stop, connection-service stop, or helper termination: open LocalClip, Connect, and run the launcher again. There is no boot auto-start.
- Mac network loss does not require relaunching a still-connected helper: LocalClip retries the Mac connection. Copies made while offline or locked are dropped, not queued for later.
- To revoke debugging itself, use Android's debugging settings to forget this Mac/revoke debugging authorizations.

For multiple attached devices, select explicitly from Terminal:

```sh
ANDROID_SERIAL=YOUR_DEVICE_SERIAL './Start automatic copying.command'
```

`ADB=/absolute/path/to/adb` selects a nonstandard Platform Tools installation. Only the main Android user (0) is supported; work profiles and secondary users are excluded. The helper requires Android 14/API 34 or newer and checks its required hidden interfaces at startup. The integrated path was validated on API 37; other Android builds can change these interfaces.

## Implementation and authentication

The APK listens only on **127.0.0.1:47392**, while the option and connection service are running. Each listener start creates an independent, random 256-bit bridge key in an app-private file. The authorized ADB launcher pipes that file to the shell helper using `run-as` on this **debuggable development APK**. It does not print the key or put it in command arguments, and it never reads the Mac pairing key. A non-debuggable production APK would need a different bootstrap mechanism; this launcher deliberately requires the supplied debug build.

The app and helper mutually authenticate with fresh nonce/HMAC proofs and encrypt every local frame with AES-256-GCM, reusing the tested framing primitives with the separate bridge key. The normal Mac connection retains its own independent pairing secret and encryption. Other ordinary apps cannot read LocalClip's private bootstrap file; a wrong key is rejected before clipboard events are accepted. Authorized debugging access is privileged access to the device.

An initial Unix-domain socket design was rejected during API 37 runtime testing: SELinux denied shell-to-app `connectto`. The shipped design uses authenticated encrypted loopback TCP and requires no SELinux changes, root, replacement keyboard, Accessibility service, Shizuku installation or added APK permissions.

The helper reads only new clipboard-change events, skips `IS_SENSITIVE` and `localclip1:` pairing codes, ignores LocalClip-labeled incoming clips to prevent loops, and does not log clipboard contents, hashes or URI paths. A small bounded worker queue drops excess bursts. Encoded inputs are capped at 8 MiB; the app validates images, caps them at 16 megapixels, and converts to PNG. Shell image reads use Android's external provider API with correct shell attribution and release the provider reference afterward.

For primary platform context, Android's [clipboard service](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/clipboard/ClipboardService.java) exempts privileged background readers before applying ordinary focus restrictions; the [shell package](https://android.googlesource.com/platform/frameworks/base/+/android16-release/packages/Shell/AndroidManifest.xml) requests `READ_CLIPBOARD_IN_BACKGROUND`. It is a privileged permission, not one an ordinary APK can request at runtime. Exact interface lookup and actual background text/image delivery were tested on API 37. The earlier API 34 read-only probe remains in `experiments/adb-clipboard` for historical reproduction; the production helper is in `adb-bridge`.

## Build and reproduce

```sh
./scripts/build-android.sh
./scripts/build-adb-bridge.sh
./scripts/build-android-tests.sh
adb push ../adb-bridge/clipboard-bridge.jar /data/local/tmp/localclip-bridge.jar
# Start the synthetic Swift AndroidTestServer as described in README, install both APKs:
adb shell am instrument -w org.localclip.tests/localclip.BridgeTests
```

Use a disposable emulator. Tests configure a public test pairing key and remove it afterward; never point the test APK at a production clipboard. See `docs/TEST-RESULTS.md` for the checks and limits.
