# LocalClip Android 0.4.2 — screenshot sharing, system insets and VPN discovery

Checked 26 October 2026 (Asia/Kuala_Lumpur). Mac binary remains 0.4.0.

## Screenshot fix and reproduced failure

On the previous APK, a valid 3200 × 2200 indexed PNG of 7,045,193 bytes fits the 8 MiB wire limit. Android's native re-encoder expands it past the limit, and converts the bounded output stream exception into a false result. The previous app then reported **Cannot encode PNG**. The reproduction log records that exact failure.

The patched app validates dimensions and decodability, then preserves existing PNG bytes. This preserves resolution, pixels and PNG color metadata without re-encoding. Other image formats still convert to PNG. Oversized input or converted output reports the 8 MiB size limit; the 16 megapixel limit remains. No automatic downscaling or quality reduction is introduced.

## Status bar fix and reproduced failure

The previous APK fails the portrait status-bar/cutout clearance check. The patched app applies native system-bar, cutout and keyboard insets to its scrolling viewport. Android 17 portrait, landscape and keyboard-open layouts pass; captured test screenshots were visually inspected. Android 10 retains the platform fitsSystemWindows path.

## Included connection fix

On 23 October, the physical Pixel 11 Pro XL resolved the paired Mac at its current address but Network.bindSocket failed with **EPERM** while Tailscale's non-bypassable VPN was active. The APK now uses normal Android routing with a VPN active, retaining discovered-network binding otherwise. It does not modify or bypass VPN policy.

The card separates discovered addresses, current attempts, the saved backup address and the authenticated connection, and exposes per-route failures. Existing pairing and the signing certificate are retained. Physical post-fix VPN verification was not captured; a later USB check is still needed.

## Final APK validation

**105 Android assertions passed on the isolated Android 17/API 37 emulator:**

- **10 image preparation assertions:** the exact old error is reproduced; the same PNG passes with identical bytes, pixels and dimensions; ordinary non-PNG conversion works; oversized converted output has a clear size error.
- **11 native layout assertions:** portrait, landscape and keyboard-open spacing, including actual system-bar and IME inset measurements.
- **52 clipboard integration assertions:** includes the large compact PNG shared from a separate provider through the transparent Activity, encrypted transfer to the real Swift peer, exact returned-byte fingerprint, and no MainActivity foregrounding. Text/images, Share reconnect, Gboard retention, screenshots and privacy exclusions also pass.
- **32 discovery assertions:** stale address recovery, changed endpoints, real NSD, encrypted Swift round trips, diagnostics and VPN route policy.

**Mac native clipboard integration: 7 peer assertions plus local checks passed**, using the same 7,045,193-byte PNG. The production receive path accepted it and wrote exact bytes to an isolated named pasteboard; Mac automatic image/text responses and acknowledgements passed. The user's general Mac clipboard was preserved.

Logs include separate **before** reproduction files whose FAIL results are expected, and passing final-build logs. No production pairing material or user screenshots are included.

## Artifacts and remaining limits

Android **0.4.2 (7)**, min API 29, target API 36. Signature SHA-256 remains `ff2edaf2ef95ce5af3c71c008e114b6b8345ec2c36fca78ff721015c15820b98`. Install over the existing APK to retain pairing. The Pixel was not attached during this final build/test run, so physical screenshot-preview/Photos behavior, its actual failing screenshot and physical insets have not been certified.

Mac and optional ADB helper binaries are unchanged from 0.4.0. Earlier protocol/helper checks remain in Git history and were not repeated for this patch. Cross-network Tailscale delivery, API 29–36 runtimes, prolonged idle/Doze and wireless debugging remain unverified. The new APK, source, logs, setup instructions and checksums are packaged with the compatible Mac app and helper.
