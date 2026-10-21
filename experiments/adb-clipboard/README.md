# Clipboard access research probe

See [the investigation](../../docs/ADB-CLIPBOARD.md). This is an API 34 emulator experiment, not the LocalClip automatic-sync implementation. It runs as ADB shell UID 2000, has no network code, and is not compiled into the APK. Use only synthetic clipboard data on an isolated emulator. It demonstrates clipboard-change callbacks and reading copied text/image data while the normal keyboard remains selected.
