# Changelog

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
