# LocalClip 0.3.0 — automatic Gboard copying and screenshot fix

Checked 21 October 2026 (Asia/Kuala_Lumpur). Repository: https://github.com/fearthedear/localclip

## What changed

- Optional, own-source ADB helper automatically sends new text and image copies while Gboard stays selected and another app remains in front. It runs as shell UID 2000, without root or a replacement keyboard.
- The APK and helper use a separate, mutually authenticated AES-256-GCM loopback session. The authorized launcher pipes a temporary app-private key through `run-as`; the Mac pairing secret stays in the APK. Disabling or stopping sharing closes the channel and deletes the temporary key. Restarting rotates it.
- Android's automatic-copying card shows waiting/active status independently of the Mac connection. Manual background Share remains available.
- LocalClip's main window allows screenshots. The prior main-window FLAG_SECURE protection caused black screenshots before any transfer. A full LocalClip screenshot now retains visible pixels through image conversion and the Mac round trip. The transparent, short-lived Share receiver still uses a secure window.

## Android 17 / Gboard results

Official Android 17/API 37 arm64 Google APIs image, revision 6, fingerprint:

`google/sdk_gphone64_arm64/emu64a:17/CE2A.260420.019/15611780:userdebug/dev-keys`

Gboard stayed selected: `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`.

**33 helper integration assertions passed** through the real Swift test peer, including:

- A new independent bridge key exists; a wrong key fails authentication.
- The packaged Mac launcher starts the helper; the app independently confirms its authenticated session.
- No existing clipboard is read/sent when starting the helper.
- A new Unicode copy from another application reaches the Mac, with exact echoed contents and the source application remaining in front. Copying identical text again sends another event.
- Sensitive-marked text and pairing codes are skipped.
- An image URI from a non-exported source provider is read by the shell helper and automatically delivered; decoded PNG dimensions and pixels survive the Mac round trip.
- Mac-to-phone updates do not loop back through the helper; the selected keyboard remains unchanged.
- LocalClip screenshots are permitted, contain visible pixels, and remain non-black with unchanged dimensions after a full image round trip.
- Disabling closes the helper channel, deletes its temporary key and stops further sends. Reenabling waits for an explicit restart; the new key differs. Disconnect stops both network and helper channel.

The **packaged `.command` launcher** was run against the test emulator, rather than only exercising a test-specific shell launch. It reported API 37 active and exited successfully when the option was disabled. The helper contains no clipboard-content/URI/hash logging.

**50 Android regression assertions passed** on API 37, covering Keystore pairing, invalid pairing, text/image/selected-text Share, exact PNG bytes and pixels, sensitive/size exclusions, ordinary background-read denial, background Mac clipboard writes, transparent external text/image shares, reconnect-and-send, connected/disconnected/unpaired indicators, normal keyboard retention, focused automatic sending and echo prevention. LocalClip screenshots were captured without clearing a production security flag. These regression checks preceded the final layout-only move of the automatic-copying card above manual sending; the 33 helper checks, including UI screenshot verification, were rerun against the final APK.

## Mac and protocol verification

Rerun successfully on this source:

- Java protocol: **18 tests**.
- Swift protocol: **18 tests**.
- Java ↔ Swift TCP: **116 assertions**, including Unicode, PNG, 8 MiB payload, fragmented frames, tamper/replay/oversize rejection, wrong secret and reconnect.
- Mac listener start/pause/restart: **30 cycles**.
- Mac private-pasteboard integration: **7 peer assertions plus local checks**, including actual text/PNG clipboard methods and automatic sending. The user's general Mac clipboard was preserved.

The APK development signature and version were verified. The Mac build is Apple silicon, ad-hoc signed and unnotarized; its clean installation ZIP is preferred. Build tools: Android platform/build tools 36/36.0.0, JDK 21.0.8, Swift 6.4 in Swift 5 mode, Xcode 27.1. Android tests used dedicated disposable AVD data, not the user's phone or existing AVD.

## Artifacts and compatibility

- `android/LocalClip-debug.apk`: `org.localclip.android`, **0.3.0 (4)**; min API 29, target API 36. Optional helper requires API 34+; integrated path tested on API 37.
- `LocalClip-adb-bridge.zip`: helper JAR, executable Mac launcher and complete setup instructions. The helper's bootstrap requires this debuggable APK.
- `macos/LocalClip-macOS-arm64.zip` / `macos/LocalClip.app`: Mac **0.3.1**, updated help text and clipboard-and-transfer icon. Existing Mac 0.2.x remains protocol-compatible; no Mac update is needed for automatic copying or the screenshot patch.
- Android signing certificate is unchanged; install over previous versions to preserve pairing. SHA-256: `ff2edaf2ef95ce5af3c71c008e114b6b8345ec2c36fca78ff721015c15820b98`.
- `LocalClip-source.zip`: MIT source, scripts, tests and docs; excludes private signing keys, production pairing data and Git internals. `SHA256SUMS.txt`, test logs and parent `LocalClip-MVP.zip` accompany the artifacts.

## Limits

Physical Pixel 11 Pro XL runtime, wireless-debugging setup, USB-unplug survival, long idle/Doze, screen-lock behavior on a physical phone, reboot recovery, Tailscale and every destination app remain unverified. Keep the launcher running; restart it after phone reboot/service stop. No boot auto-start, persistent clipboard queue, work-profile support, QR scanner, forward secrecy, independent security audit or notarization. Hidden Android interfaces can change; the helper fails visibly if its required signatures are unavailable. Other apps still need to expose readable text/image clipboard content. Taking a screenshot is not necessarily copying it.

## Mac 0.3.1 icon update — 23 October 2026

Added editable vector artwork and a native multi-resolution ICNS, declared by CFBundleIconFile and bundled by the Mac build script. Native icon resolutions range from 16 to 1024 pixels. The new Mac app was built, its ad-hoc signature verified, installed in /Applications, reopened successfully and sharing restored to its previous listening state. The icon was visually inspected. Android and transport code did not change; their earlier results above were not rerun for this asset-only update. Android remains 0.3.0; the Mac is now 0.3.1 (4). The Mac ZIP contains the updated icon, and the source includes the renderer and ICNS.
