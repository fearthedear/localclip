# LocalClip 0.4.0 — address-change recovery

Checked 23 October 2026 (Asia/Kuala_Lumpur). Repository: https://github.com/fearthedear/localclip

## Implemented and built

Mac sharing publishes its current listener through Bonjour; Android identifies the already-paired Mac and resolves its current endpoints. A stale saved Wi-Fi address can recover without re-pairing. Android shows its authenticated active route and has a pairing-preserving address editor. Mac can select its verified Tailscale MagicDNS name and remembers the chosen host. The native app icon, Gboard ADB helper, background Share and screenshot fix remain included.

No new cloud service, subnet scan, runtime library or Android permission was added. New optional pairing route fields are backward compatible, and the clipboard handshake/encryption is unchanged. See NETWORKING.md for design and setup.

## New network checks

**5 Mac Bonjour integration assertions passed:**

- Real publication and browser resolution with the actual listener port.
- Domain-separated paired identity and TXT version survive DNS-SD.
- Advertisements contain no raw pairing secret.
- Pausing withdraws the advertisement.
- Restarting publishes a new port with the same identity.

**20 Android DNS-SD integration assertions passed on Android 17/API 37:**

- Starts with a stale saved address; rejects a matching service name with the wrong TXT identity.
- Uses actual Android NSD registration/resolution to find the right advertisement.
- Automatically connects through a discovered route while preserving the legacy saved pairing.
- Completes an authenticated encrypted clipboard round trip to the real Swift peer, with exact returned text.
- Withdraws the old advertisement and reconnects to a changed advertised endpoint; clipboard transfer succeeds with the same key.
- Editing a MagicDNS host retains the encrypted pairing key; URL-shaped input is rejected.
- Disconnect shuts down the service/discovery lifecycle.

The Android DNS-SD fixture advertises inside the emulator and forwards test-only TCP bytes to the Mac Swift peer. Mac publication is tested separately with a real Mac Bonjour browser. These checks cover both platform APIs and transport integration; they do not prove multicast delivery from a physical Pixel to this Mac.

## Regression checks rerun on this source

- Android clipboard integration: **50 assertions**, including text/images, external transparent Share, reconnect/send, privacy exclusions, background Mac clipboard writes, connection UI, no replacement IME, selected keyboard unchanged, screenshot permission and echo prevention.
- Optional ADB helper: **33 assertions**, including the actual packaged Mac launcher, new background text/image copies, exact image pixels, sensitive/pairing exclusions, no initial clipboard read, repeated copies, no echo, screenshot round trip, temporary-key rotation and disable/disconnect behavior.
- Android emulator: API 37, official Google APIs arm64 revision 6, fingerprint `google/sdk_gphone64_arm64/emu64a:17/CE2A.260420.019/15611780:userdebug/dev-keys`; Gboard selected and retained.
- Java protocol: **19 tests**; Swift protocol: **19 tests**, including a matching fixed discovery-ID vector.
- Java ↔ Swift TCP: **116 assertions**; listener start/pause/restart: **30 cycles**.
- Mac private-pasteboard integration: **7 peer assertions plus local checks**. The user's general Mac clipboard was preserved by these test suites.

The DNS-SD suite was rerun against the final APK after tightening address-input validation; the other Android regressions preceded that validation-only adjustment.

Total Android assertions in this run: **103**. Pure transport tests do not certify network discovery or destination-app compatibility beyond their stated scope.

## Artifacts and compatibility

- Android `org.localclip.android`: **0.4.0 (5)**, min API 29, target API 36, development-signed and debuggable. Signature SHA-256 remains `ff2edaf2ef95ce5af3c71c008e114b6b8345ec2c36fca78ff721015c15820b98`. Install over the previous APK to retain pairing.
- Mac: **0.4.0 (5)**, Apple silicon, ad-hoc signed, unnotarized. Installed in /Applications and available as `macos/LocalClip-macOS-arm64.zip`. macOS may ask the updated app to access its existing Keychain pairing key; that protected prompt requires user interaction.
- Optional helper JAR/launcher remain protocol-compatible. Build source includes editable icon artwork, native ICNS, discovery implementation, tests, scripts and documentation.
- `LocalClip-source.zip`, `LocalClip-adb-bridge.zip`, `SHA256SUMS.txt`, test logs and parent `LocalClip-MVP.zip` accompany the apps. Private signing keys, production pairing secrets and Git internals are excluded.

## Remaining limits

API 29–36 runtimes were not exercised for this discovery release. A physical Pixel still needs to install this APK and verify recovery on its actual Wi-Fi. AP isolation/multicast blocking can prevent discovery; direct saved addresses and Tailscale names remain available. Existing broken connections may take up to their heartbeat/receive timeout to detect network loss. Cross-network Tailscale delivery, phone wireless-debugging setup, USB-unplug survival, long idle/Doze and physical lock behavior remain unverified. No boot auto-start, persistent queue, work-profile support, QR scanner, forward secrecy, independent security audit or notarization.
