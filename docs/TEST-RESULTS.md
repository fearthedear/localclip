# LocalClip Android 0.2.1 — background Share patch and ADB research

Checked on 21 October 2026 (Asia/Kuala_Lumpur). Repository: https://github.com/fearthedear/localclip

## Implemented in the APK

Android's clipboard-popup **Share → LocalClip**, app Share actions and selected-text actions resolve to a transparent `ShareActivity`, rather than the main screen. The receiver reads and converts the explicitly shared payload while its temporary image URI grant is valid, hands bounded bytes to the foreground service, and finishes. It is excluded from Recents, has no main-app UI or launch preview, and leaves the previous app visible.

The service sends while connected, or retains one explicit share in memory for up to 30 seconds while reconnecting. New pending shares replace older ones. Delivery/failure is reported in the connection notification. An unpaired phone receives a short Toast asking it to pair first; the main screen is not opened automatically. Android still briefly launches a share-target Activity; there is no claim of zero Activity lifecycle/focus transition.

## Android regression results

**48 assertions passed** on the isolated Android 14/API 34 arm64 emulator, including:

- Keystore pairing, invalid-code rejection, authenticated connection, Unicode clipboard round trip.
- Text Share, selected text, image Share, PNG URI/MIME, exact bytes and decoded pixels.
- Sensitive/pairing-code exclusions and image size cap.
- Mac-to-phone writing while another app has focus; ordinary background reads remain denied.
- Actual intent-filter resolution to the transparent receiver.
- Text shared from a separate application: receiver closes, data arrives, main screen stays in the background.
- Image shared from a separate application's **non-exported provider**, with an explicit temporary URI grant: bytes are staged, receiver closes, service delivers afterward, and the main screen remains in the background.
- Disconnected Share from another app starts/reconnects the service, closes the receiver and delivers while the previous app stays in front. The exact returned text was then checked explicitly.
- Connected, disconnected and unpaired indicators, and the in-app reconnect/send action.
- Selected keyboard unchanged; no replacement IME registered; focused automatic sending and echo suppression.
- Unpaired Share closes without opening the main screen or starting a connection.

Tests used AOSP's normal Latin keyboard, not Gboard-specific APIs. The actual Pixel clipboard-popup interaction was not re-tested on physical hardware. The production window remains FLAG_SECURE; test-only UI screenshots temporarily clear and restore it.

The unchanged transport/Mac sources retain their earlier passing suites: 18 Java tests, 18 Swift tests, 116 Java↔Swift TCP assertions, 30 listener restart cycles and Mac private-pasteboard integration (7 peer assertions plus local checks). The sharing patch was validated through the real Swift peer and Android platform integration above; these earlier pure-protocol suites were not repeated solely for the UI/dispatch patch.

## ADB experiment: observed, not integrated

A separately launched process ran as **ADB shell UID 2000, not root**, with the normal keyboard still selected and the source app in front. It automatically received clipboard-change callbacks and read synthetic text, then read a complete 107-byte PNG from the private source provider after an image copy. No Share action was used for those probe events.

The final probe logs contain only metadata, not raw clipboard contents. The final source compiled and was run again after the attribution/provider fixes. This demonstrates the access mechanism on API 34; **it does not send to the Mac**, and the APK does not enable ADB or Shizuku or include the helper. Hidden Binder signatures were inspected on the emulator and the research code refuses other API levels. Pixel OS variants, reboot/Doze longevity and an authenticated helper-to-app handoff still need engineering and validation before auto-sync can ship.

See [ADB investigation](https://github.com/fearthedear/localclip/blob/main/docs/ADB-CLIPBOARD.md) for primary Android/Shizuku sources, errors encountered, tested provider access, source code and reproduction instructions. The normal APK still cannot read arbitrary background copies with Gboard selected.

## Artifacts and compatibility

- `android/LocalClip-debug.apk`: Android package `org.localclip.android`, **0.2.1 (3)**, min API 29, target API 36, development-signed and debuggable.
- Install over 0.1.0/0.2.0 to preserve pairing. Package ID, Keystore alias, pairing format, wire protocol and signing key are unchanged. Certificate SHA-256:
  `ff2edaf2ef95ce5af3c71c008e114b6b8345ec2c36fca78ff721015c15820b98`.
- The existing **Mac 0.2.0** app is compatible and unchanged. `macos/LocalClip-macOS-arm64.zip` remains the Apple silicon, ad-hoc signed, unnotarized Mac build. No Mac update is required for this patch.
- `LocalClip-source.zip`: complete MIT-licensed source, including tests and the separate experiment. No private signing keys, production pairing data or Git internals.
- `SHA256SUMS.txt`, `test-logs/`, and the parent `LocalClip-MVP.zip` contain checksums, test evidence and the complete delivery bundle.

The APK signature was verified and its manifest/version inspected. Build tools: Android SDK/build tools 36/36.0.0, Temurin JDK 21.0.8. Tests used the dedicated disposable AVD data directory; existing user AVDs and physical phone settings were not changed.

## Remaining limits

No automatic Gboard background-copy sending in this APK; only the separate ADB access prototype was demonstrated. No independent security audit, forward secrecy, notarization, QR scanner, persistent queue, guaranteed Doze/reboot reliability or Tailscale validation. Physical Pixel/API 35–37 behavior and destination-app image compatibility remain unverified by this automated run. The prior working Mac transport remains unchanged.
