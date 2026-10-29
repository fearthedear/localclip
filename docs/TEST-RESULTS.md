# LocalClip 0.5.0 — Tailscale first and tailnet discovery

Checked 29 October 2026 (Asia/Kuala_Lumpur). Both apps: 0.5.0 (9).

## Implemented

The Mac reads its installed Tailscale client's actual local status and prefers its verified Tailscale address/MagicDNS name. While sharing, it sends source-bound HMAC discovery messages over UDP to online visible tailnet peers. The paired Android foreground service receives and validates them, connects back with the existing authenticated encrypted clipboard protocol, promotes Tailscale over an active Wi-Fi fallback and remembers the authenticated route without re-pairing. Recent duplicates and unpaired / relayed messages are rejected. No admin API token, new cloud service, VPN bypass or ACL change.

The Android 0.4.3 image resizing fix is included. Source images up to 32 MiB / 16 megapixels can be prepared for the unchanged 8 MiB wire format. Fitting PNGs are unchanged; oversized conversions are resized with aspect ratio retained and a sender notice. Existing system-bar/cutout/keyboard fixes remain.

## Current-build automated checks

**133 Android assertions passed on the isolated Android 17/API 37 emulator:**

- **18 tailnet discovery:** real UDP reception using test-only 100.90.0.1/.2 loopback aliases; paired-key, source binding, duplicate and closed-listener rejection; Tailscale-first route selection; automatic migration from an active fallback; encrypted Swift text round trip; encrypted route persistence retaining the key and LAN backup; restart and reconnect without another beacon.
- **55 clipboard integration:** text/images, transparent Share, automatic reconnect, unchanged Gboard, privacy exclusions and exact encrypted Swift round trips for both a preserved large PNG and an automatically resized JPEG conversion.
- **32 Wi-Fi discovery:** native NSD, changed endpoints, stale addresses, wrong TXT identity, diagnostics and pairing-preserving address edits.
- **17 image preparation:** valid compact PNG preserved, oversized JPEG and PNG sources resized under the transfer limit, valid pixels/dimensions/aspect ratio, bounds, sender notice metadata and unchanged protocol serialization.
- **11 native window-inset:** portrait, landscape, actual system bars/cutout measurements and keyboard-open viewport spacing.

**Mac / transport checks passed:**

- **5 tailnet checks:** real response-shape parsing with synthetic fixtures, interface verification, online / address filtering, rejection of unexpected data and exact native UDP transmission.
- **5 Bonjour checks:** real publication, resolution, identity/TXT, withdrawal and restart.
- Java protocol **26** and Swift protocol **21**, including an independently computed shared tailnet beacon vector and source/tamper rejection.
- Java ↔ Swift TCP **116 assertions**, including an 8 MiB payload, invalid secrets, tampering, replay, fragmentation and reconnect.
- Listener lifecycle **30 cycles**.
- Mac native clipboard **7 peer assertions plus local checks**: a 7,656,169-byte resized PNG was accepted by the production receive path and written byte-for-byte to an isolated named pasteboard. The user's general pasteboard was preserved.

## Physical verification

Both signed/debug APK and runnable Mac app were installed over the existing copies, retaining pairing. The Pixel discovered the Mac automatically over the real tailnet and established a connection between their Tailscale addresses. No manual address entry or re-pairing was performed.

The user then disabled Wi-Fi on the Pixel. Android's network diagnostics showed CELLULAR|VPN, and LocalClip remained connected over Tailscale. Debugger inspection read routing/status fields only and confirmed a fresh authenticated Mac response approximately two seconds before observation. Accepted discovery message count advanced from 7 to 21 during the cellular check, confirming fresh tailnet discovery as well as cached-route connectivity. Clipboard contents, pairing material and Tailscale credentials were not read. This is a real cross-network connectivity check; a physical text/image clipboard transfer was not driven during that check. Synthetic exact-byte image transfers are covered above.

## Artifacts, signing and limits

Android signing certificate SHA-256 remains `ff2edaf2ef95ce5af3c71c008e114b6b8345ec2c36fca78ff721015c15820b98`. Mac is Apple silicon, ad-hoc signed and unnotarized, installed in Applications. The optional ADB helper binary is unchanged and compatible. Existing pairing keys were retained; macOS required the user to allow access to its existing Keychain item.

Discovery currently uses Tailscale IPv4, up to 64 online peers from the local client, with UDP 47390 Mac→phone and TCP 47391 phone→Mac. Require Tailscale installed/connected on both devices, appropriate tailnet policy, Mac sharing/awake, Android connection service running. Cached tailnet routes and Wi-Fi fallback remain available. No guarantees for sleeping/offline devices, expired sessions, restrictive ACLs, stopped services or disabled Tailscale. API 29–36 runtimes, prolonged Doze and wireless-debugging/helper survival were not rerun for this release.

APK, Mac ZIP, editable source, optional helper, logs, setup instructions and SHA-256 checksums accompany the release. Private keys, user screenshots, production pairing material and private tailnet peer lists are excluded.
