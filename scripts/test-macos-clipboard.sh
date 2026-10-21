#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BUILD_DIR:-$ROOT/.build/tests}"
mkdir -p "$BUILD/java"
swiftc -swift-version 5 -framework AppKit -framework ImageIO -framework Security -framework CryptoKit "$ROOT/macos/Protocol.swift" "$ROOT/macos/Server.swift" "$ROOT/macos/App.swift" "$ROOT/tests/MacClipboardTests.swift" -o "$BUILD/mac-clipboard-tests"
javac -d "$BUILD/java" "$ROOT/android/src/localclip/Wire.java" "$ROOT/tests/MacClipboardPeer.java"
"$BUILD/mac-clipboard-tests" > "$BUILD/mac-clipboard.log" 2>&1 &
TEST_PID=$!
trap 'kill "$TEST_PID" 2>/dev/null || true' EXIT
python3 - <<'PY'
import socket,time
for attempt in range(100):
    try:
        with socket.create_connection(('127.0.0.1',47494),.2):break
    except OSError:time.sleep(.05)
else:raise SystemExit('Mac clipboard test failed to start')
PY
java -cp "$BUILD/java" MacClipboardPeer
cat "$BUILD/mac-clipboard.log"
