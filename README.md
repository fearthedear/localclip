# LocalClip

A small, MIT-licensed clipboard companion for macOS and Android. Text and images move directly between your devices over an authenticated, encrypted connection. No hosted backend, account, analytics, ads, or runtime third-party libraries. Local Wi-Fi works on its own; Tailscale is optional.

**Keep Gboard.** LocalClip does not install or ask you to switch to another keyboard. Optional automatic background copying uses an own-source, rootless ADB helper. Without the helper, use Share or the focused Send button.

## Downloads

[Download the latest release](https://github.com/fearthedear/localclip/releases/latest):

- `LocalClip-debug.apk`: Android 10/API 29 or later, target API 36; signed with a development key, debug-installable.
- `LocalClip-adb-bridge.zip`: optional Gboard-compatible automatic-copy helper, Mac launcher and setup instructions. Requires Android 14+ and authorized USB/wireless debugging; tested on Android 17/API 37.
- `LocalClip-macOS-arm64.zip`: Apple silicon, macOS 13 or later; ad-hoc signed, not notarized. Extract it and move `LocalClip.app` into Applications.

Version 0.3.0 adds automatic background copying through the optional ADB helper and permits screenshots of LocalClip itself. Install over earlier versions to preserve pairing; the signing certificate and Mac wire protocol are unchanged. Your existing Mac app remains compatible.

## Send from Android while using Gboard

### Automatic copying (optional ADB setup)

In LocalClip, Connect to Mac and enable **Allow automatic copying through the ADB helper**. With an authorized USB/wireless debugging connection, extract `LocalClip-adb-bridge.zip` on the Mac and run **Start automatic copying.command**. Once the app says **Automatic copying active**, copies in other apps send automatically with Gboard still selected. Relaunch after a phone reboot or service stop. See the [complete setup and limitations](docs/ADB-CLIPBOARD.md).

### Copy, then send

1. In any Android app, **Copy** text or an image.
2. Open **LocalClip**.
3. Tap **Send clipboard to Mac**. If disconnected, the button says **Connect & send clipboard** and does both.
4. Wait for **Mac clipboard updated — ready to paste**, then paste on your Mac.

You can also tap **Send clipboard** in the connection notification or add the **Send clipboard** Quick Settings tile. Both open LocalClip so Android permits clipboard access.

### Share directly from another app

1. Select text or open a photo.
2. Tap that app's **Share** action. Copy and Share are different actions.
3. Choose **LocalClip** in Android's share sheet. You may need to expand the full app list.
4. LocalClip’s transparent share receiver stages the item, closes, and leaves the previous app visible. The running service connects and sends in the background; its notification reports delivery. No main LocalClip screen or extra Send tap is needed.

You can also use **Share on Android’s small clipboard popup after copying → LocalClip**. Android briefly launches an Activity to receive a share, but it is transparent and excluded from Recents. If you have not paired yet, it shows a short message asking you to open LocalClip and pair; it does not open the main screen for you.

If an app has no Share action, use Copy and the blue clipboard button instead. You can also use **Choose a photo to send** inside LocalClip. Selected text may offer a **LocalClip** action in the text selection menu.

## Read the connection indicator

- **Connected to Mac** (green): authenticated encrypted connection; the card shows how recently the Mac responded.
- **Connecting / Reconnecting** (amber): not ready yet; check the Mac app and reachable Wi-Fi/Tailscale address.
- **Checking connection** (amber): a socket exists but the Mac has not responded recently. LocalClip will reconnect if necessary; a green badge is not shown indefinitely for a stale socket.
- **Disconnected** (red): sharing is paused or the service is stopped. Connect, or send an item to reconnect.
- **Pairing rejected** (red): disconnect, then save a new pairing code from the Mac.
- **Pair with a Mac**: add a pairing code first.

The **Last transfer** card distinguishes waiting for confirmation from a confirmed clipboard update. A connected socket does not guarantee that an individual item was applied: look for the delivery confirmation.

## First-time setup

1. Open `LocalClip.app` on the Mac. Because it is not notarized, macOS may require its normal **Open Anyway** flow in System Settings → Privacy & Security. Allow local network and clipboard access when asked. Recent macOS versions require the **Always Allow** clipboard choice for automatic background polling. No Accessibility or screen recording permission is needed.
2. Keep the Mac and Pixel on reachable Wi-Fi and the Mac awake. Guest networks with client isolation can block direct connections.
3. Install the APK on Android. You may need to allow APK installation from the app used to open it. Alternatively:
   ```sh
   adb install -r LocalClip-debug.apk
   ```
4. In the Mac app, select a reachable Wi-Fi IP in **Mac address**, click **Start sharing**, then **Copy pairing code**. Permit TCP **47391** through the Mac firewall; do not forward this port on your router.
5. Transfer that first pairing code to the phone through a trusted channel. Initial pairing cannot use clipboard sync before it is established. This MVP uses a shared secret and does not include a QR scanner.
6. In Android LocalClip, open **Pair with a Mac**, paste the code, then **Save pairing & connect**. Allow notifications to keep the connection and Send action visible.
7. Look for the green **Connected to Mac** indicator. Copy something new on the Mac and paste it on the unlocked phone; use one of the Android sending methods above to test the other direction.

Treat the pairing code like a password and replace it in both devices' clipboards after saving it. Pairing controls collapse once paired; use **Change Mac pairing** to edit them. Disconnect before replacing a pairing. Mac **Forget phone / rotate secret** revokes the old code; Android **Forget this Mac** removes the local pairing.

## Supported behavior and limits

| Feature | Behavior |
|---|---|
| Mac → Android | New Mac copies arrive automatically while Mac sharing and Android's connection service are running and the phone is unlocked. |
| Android → Mac with Gboard | Automatic new copies with the optional ADB helper; otherwise explicit Share, selected-text action, or focused Send clipboard button. The selected keyboard stays unchanged. |
| Optional ADB helper | Separate shell process, encrypted loopback channel, explicit debugging authorization. API 37/Gboard emulator tested. Restart after reboot/service stop. Main user only; hidden APIs can change. |
| Focused automatic sending | Optional checkbox sends new clipboard changes while LocalClip is on screen. Opening the app alone does not upload the existing clipboard. |
| Images | PNG on the wire; max 8 MiB and 16 megapixels. Android can share/choose other decodable formats and converts them to PNG. Mac PNG/TIFF clipboard images are supported. Pasting depends on the destination app. |
| Disconnected explicit send | A share can wait in the foreground service for up to 30 seconds after its receiver closes. In-app clipboard/photo actions wait in the main screen while it has focus. No persistent queue; an unavailable Mac requires sharing again after expiry. |
| Offline automatic changes | Dropped; reconnect and explicitly send the item again. |
| Locked phone | Incoming clipboard updates are skipped and remain unconfirmed on the Mac. Unlock and send again. |

Only the first clipboard item is sent. Rich text becomes UTF-8 plain text. No file/video transfer, multi-item clipboard, propagated clipboard clearing, rich text formatting, or animated-image playback. Neither app maintains a clipboard history. Up to four received PNGs remain in Android's private cache so clipboard URI recipients can paste them; cache eviction or later images can invalidate an older URI.

Clipboard privacy flags are honored where supplied: Android `IS_SENSITIVE`, Mac `ConcealedType`. Pairing code text is never synced. These flags cannot identify every password/private item; pause sharing when it is not wanted.

Android uses a user-started `connectedDevice` foreground service and 10-second heartbeats. There is no boot receiver, hidden auto-start, wake lock, or battery exemption request. Doze, battery settings, force-stop, reboot, network changes, or process termination may interrupt the connection. Open LocalClip and tap Connect again if needed. The service retries network loss with 1–15 second backoff.

Mac clipboard polling is every 350 ms while sharing is enabled. This is a design interval, not a physical Pixel latency measurement. Mac writes can briefly wait on a slow socket. Simultaneous copies use arrival order; there is no conflict history.

## Optional Tailscale

Enter the Mac's reachable Tailscale IP or MagicDNS name in its address field, then copy and save an updated pairing code on Android. Permit TCP 47391 between the devices in the tailnet policy. Application encryption remains enabled.

LocalClip itself has no cloud service. Tailscale uses coordination infrastructure and can relay encrypted traffic through DERP when a direct connection is unavailable. Omit Tailscale and use Wi-Fi IPs for a local-only installation. Tailscale was not tested during development.

## Build

No Gradle, npm, Maven, CocoaPods, or downloaded runtime libraries are required. Scripts use installed official SDK tools.

macOS: Xcode/Command Line Tools with Swift and a macOS SDK:

```sh
./scripts/build-macos.sh
# Optional Intel build (not runtime-tested):
ARCH=x86_64 OUTPUT_DIR=../macos-intel ./scripts/build-macos.sh
```

Android: JDK 17+ (built with Temurin 21), SDK platform 36, Build Tools 36.0.0:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
./scripts/build-android.sh
./scripts/build-adb-bridge.sh
```

Default outputs are `../macos`, `../android` and `../adb-bridge`; `.build` holds intermediates. Override with `OUTPUT_DIR` and `BUILD_DIR`. Mac signing/archive creation happens in a temporary directory because file-provider metadata can interfere with `.app` signatures in synced folders. Prefer the supplied clean ZIP for installation.

The Android build creates a local development key at `.build/android/debug.keystore`, password `android`. Keep your key privately for compatible subsequent updates. A different signing key requires uninstall/reinstall, removing pairing. The supplied APK's private signing key is not published. The APK is debuggable and is not a Play Store release. Mac rebuilds may prompt for Keychain access again because signing is ad-hoc.

## Test

```sh
./scripts/test.sh
./scripts/test-macos-clipboard.sh
```

These cover Java/Swift crypto and framing, real cross-platform TCP, rejection/reconnect behavior, listener lifecycle, and the Mac app's clipboard methods on a private named pasteboard. They preserve the user's general Mac clipboard.

Android tests use an **isolated disposable emulator**, synthetic clipboard content, and a public test-only key:

```sh
./scripts/build-android.sh
./scripts/build-android-tests.sh
swiftc -swift-version 5 macos/Protocol.swift macos/Server.swift tests/AndroidTestServer.swift -o .build/android-test-server
.build/android-test-server
# In another terminal, with an isolated emulator running:
adb install -r ../android/LocalClip-debug.apk
adb install -r .build/android/LocalClip-tests.apk
adb shell pm grant org.localclip.android android.permission.POST_NOTIFICATIONS
adb shell am instrument -w org.localclip.tests/localclip.AndroidTests
# Optional helper integration tests:
./scripts/build-adb-bridge.sh
adb push ../adb-bridge/clipboard-bridge.jar /data/local/tmp/localclip-bridge.jar
adb shell am instrument -w org.localclip.tests/localclip.BridgeTests
```

The emulator test peer is `10.0.2.2:47493`. Stop it after testing. The tests leave the selected keyboard unchanged. The API 37 emulator uses Gboard. LocalClip's main window permits screenshots; the tests verify visible pixels before and after a full screenshot round trip. Test APKs and fixed fixture keys are never used for actual clipboard sharing.

For automatic background sending with Gboard, see [ADB helper setup, architecture and tests](docs/ADB-CLIPBOARD.md). The separate helper JAR must be launched through ADB; installing the APK alone does not grant background clipboard access.

See [test results](docs/TEST-RESULTS.md), [security notes](SECURITY.md), [wire protocol](docs/PROTOCOL.md), and [platform investigation](docs/PLATFORM-NOTES.md). There is no independent security audit or forward secrecy. The user reports the initial build works on their devices; the current automated checks ran on Android 17/API 37 with Gboard, and do not certify every physical Pixel OS build.
