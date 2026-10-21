# LocalClip 0.2.0 — tested behavior

Built and tested on 21 October 2026, Asia/Kuala_Lumpur. Public source: https://github.com/fearthedear/localclip

## Android update

- Prominent green Connected to Mac / red Disconnected / amber Connecting and Reconnecting indicator. A stale Mac response becomes Checking connection instead of remaining green.
- Last authenticated Mac response age, paired address, and explicit connection/pause controls.
- Clipboard and photo actions at the top, with Gboard-compatible Share instructions.
- Share and Connect & send clipboard reconnect automatically, holding one explicit item in memory for up to 30 seconds while the current screen has focus.
- Replacement keyboard and keyboard-switching prompts removed. The app does not change the selected keyboard.
- Pairing controls collapse once paired. The Last transfer card distinguishes sending from confirmed delivery.

## Passing checks

| Check | Result |
|---|---|
| Android platform integration | **31 assertions passed** on an isolated Android 14/API 34 arm64 emulator. |
| Java protocol tests | 18 passed. |
| Swift protocol tests | 18 passed. |
| Java ↔ Swift encrypted TCP | 116 assertions passed, including Unicode, PNG framing, 8 MiB payload, fragmentation, wrong secret, replay, tampering, oversize rejection and reconnect. About 0.23 seconds total on localhost; not a phone latency benchmark. |
| Mac listener lifecycle | 30 start/pause/restart cycles passed. |
| Mac clipboard integration | 7 peer assertions plus local clipboard checks passed, using a private named pasteboard and preserving the user's general Mac clipboard. |
| Android packaging | APK signature verified; package, version, min/target SDK, debuggable status and service declarations inspected. |
| UI | Connected/disconnected Android screens visually inspected using test-only screenshots. Production keeps FLAG_SECURE; instrumentation temporarily clears and restores it for screenshots. |
| Mac packaging | Apple silicon .app built and ad-hoc signature verified; clean ZIP created before copying to the synced output folder. Not notarized. |

The 31 Android assertions include:

- Encrypted Keystore-backed pairing persistence and invalid-code rejection.
- Authenticated transport and OS clipboard round trip with Unicode text.
- Share text, Share image, selected-text action, PNG provider URI/MIME, exact image bytes and decoded pixels.
- Sensitive content, pairing code and oversize image exclusion.
- Background Mac-to-phone writing while another app has focus.
- Background phone clipboard reads denied with the normal keyboard selected.
- Connected, disconnected and unpaired UI states driven by actual service/authentication state.
- Share while disconnected reconnects and delivers without another Send tap.
- Clipboard button while disconnected reconnects and delivers.
- Selected keyboard remains unchanged and no replacement IME is registered.
- Focused automatic sending with the normal keyboard, plus incoming echo suppression.

The test emulator uses AOSP's built-in Latin keyboard. Gboard itself is not installed on that emulator; the tested app paths preserve the chosen keyboard and do not depend on any Gboard-specific API. The user reports that the initial build works on their actual devices; physical Pixel behavior was not independently re-tested here.

## Install compatibility and artifacts

Android: `org.localclip.android`, version 0.2.0 (2), min API 29, target API 36. Install the new APK over the initial build to preserve pairing. The package ID, Keystore alias, pairing format, wire protocol and signing certificate are unchanged. Development certificate SHA-256:

```
ff2edaf2ef95ce5af3c71c008e114b6b8345ec2c36fca78ff721015c15820b98
```

The APK is development-signed and debuggable. Mac: `org.localclip.mac`, 0.2.0, Apple silicon, minimum macOS 13, ad-hoc signed and not notarized. The existing Mac app remains wire-compatible; the new Mac build updates its Gboard instructions.

Artifacts in the local delivery folder: `android/LocalClip-debug.apk`, `macos/LocalClip-macOS-arm64.zip`, `macos/LocalClip.app`, `LocalClip-source.zip`, `SHA256SUMS.txt`, and passing `test-logs/`. The parent outputs folder contains `LocalClip-MVP.zip`. Source, setup instructions and test scripts are in the repository. Release downloads are at https://github.com/fearthedear/localclip/releases/latest . Private signing keys and production pairing data are not published.

Tools: macOS 27.0.1, Swift 6.4 in Swift 5 mode, Xcode 27.1, Temurin JDK 21.0.8, Android SDK/build tools 36/36.0.0. Existing user AVD data was not modified; tests ran in a dedicated disposable data directory.

## Remaining limits

No automatic background phone-to-Mac reading with Gboard. Use Share → LocalClip, selected-text processing, or open LocalClip and Send clipboard. Focused automatic sending is optional and applies only while LocalClip is on screen.

Physical Pixel OS variants/API 35–37 runtime behavior, overnight Doze/battery reliability, physical lock/unlock, Wi-Fi roaming, Tailscale and third-party image-pasting compatibility remain unverified. No independent security audit, forward secrecy, QR scanner, or notarization. Images are limited to 8 MiB/16 megapixels and destination-app support. Locked-phone deliveries are skipped, with no durable queue.

A synced/file-provider folder can attach Finder metadata to an unpacked .app and interfere with strict signature checks. The clean ZIP is the recommended Mac installation artifact; extract it to Applications. macOS may require its normal Open Anyway and clipboard/local-network permission flows.
