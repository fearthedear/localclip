# Changelog

## 0.5.0

- Prefer Tailscale routes and addresses on both apps, retaining local Wi-Fi fallback.
- Mac reads the installed Tailscale client’s actual Self/Peer status, verifies its local address and sends authenticated unicast discovery messages to up to 64 online visible peers every 15 seconds while sharing.
- Android accepts only source-bound, paired-key discovery messages from tailnet addresses, ignores recent duplicate nonces, and still authenticates every clipboard TCP connection. No Tailscale API token, embedded VPN or new cloud service.
- Promote a live Wi-Fi fallback to a discovered Tailscale route; remember that route only after successful authentication without changing the pairing key.
- Retain the image resizing and system-bar fixes.

## 0.4.3

- Automatically resize oversized Android PNG conversions instead of rejecting them. Preserve aspect ratio and report when resolution is reduced.
- Accept shared image inputs up to 32 MiB while keeping the 16 megapixel decode and 8 MiB wire limits. Existing fitting PNGs stay unchanged.

## 0.4.2

- Preserve validated PNG screenshot bytes instead of re-encoding them, which could expand a valid image past the 8 MiB limit and produce “Cannot encode PNG”. No resizing or image-quality loss for existing PNGs.
- Distinguish conversion overflow from encoder failure for non-PNG images; retain the 8 MiB / 16 megapixel limits.
- Respect Android system bars, camera cutouts and keyboard insets, including landscape.
- Reproduce both reported failures on the previous APK; add large-image encrypted transfer and native window-inset regressions.

## 0.4.1

- Fix discovered Wi-Fi connections failing with `EPERM` when Tailscale or another non-bypassable VPN is active. Use Android’s normal routing while a VPN is active; retain discovered-network binding otherwise.
- Show discovered addresses, the current attempt, and per-route connection failures separately from the saved backup address. Discovery alone is not a connected state.
- Refresh stale route snapshots when discovery changes, and retain authentication failures instead of hiding them behind a later unreachable saved IP.
- Add a read-only service diagnostic dump with routing/status only, excluding clipboard content and pairing material.

## 0.2.1

- Share from Android’s clipboard popup or another app without opening the main LocalClip screen.
- Use a transparent share receiver, stage URI data before its grant ends, and hand bytes to the foreground service for background delivery/reconnection.
- Report shared-item delivery/failure in the ongoing notification; show a short explanation instead of opening the main screen when unpaired.
- Test external private-image URI grants and both connected/disconnected shares from a separate app.
- Add a separate API 34 ADB-shell research probe: automatic text-copy callbacks and image reads with the normal keyboard unchanged. Not integrated into the APK’s sending path.

## 0.2.0

- Keep Gboard: remove the replacement keyboard and all keyboard-switching prompts.
- Put connection status and clipboard/photo sending at the top of the Android app.
- Show connected, disconnected, connecting, reconnecting, stale-response checking, unpaired, and pairing-rejected states. Show the age of the last authenticated Mac response.
- Explain Android Share → LocalClip separately from copying and explicitly sending the clipboard.
- Reconnect automatically for a shared item or the Connect & send clipboard action, with one in-memory item and a 30-second expiry.
- Collapse pairing controls once paired and retain the existing selected keyboard.
- Keep transport, pairing format, app ID, and Android signing certificate compatible with the initial build.
- Update Mac instructions and setup documentation for the Gboard workflow.

## 0.1.0

Initial local clipboard MVP with authenticated encrypted transport, text and PNG transfer, Mac clipboard monitoring, Android Share/clipboard actions, and platform integration tests.
