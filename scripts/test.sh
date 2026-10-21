#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BUILD_DIR:-$ROOT/.build/tests}"
mkdir -p "$BUILD/java"
javac -d "$BUILD/java" "$ROOT/android/src/localclip/Wire.java" "$ROOT/tests/ProtocolTests.java" "$ROOT/tests/InteropTests.java"
VECTOR="$(python3 - <<'PY'
import hmac,hashlib
print(hmac.new(bytes(range(32)),b'LC01/client'+bytes(range(32,64))+bytes(range(64,96)),hashlib.sha256).hexdigest())
PY
)"
java -cp "$BUILD/java" ProtocolTests "$VECTOR"
swiftc -swift-version 5 "$ROOT/macos/Protocol.swift" "$ROOT/tests/ProtocolTests.swift" -o "$BUILD/swift-tests"
"$BUILD/swift-tests" "$VECTOR"
swiftc -swift-version 5 "$ROOT/macos/Protocol.swift" "$ROOT/macos/Server.swift" "$ROOT/tests/ServerLifecycleTests.swift" -o "$BUILD/lifecycle-tests"
"$BUILD/lifecycle-tests"
swiftc -swift-version 5 "$ROOT/macos/Protocol.swift" "$ROOT/macos/Server.swift" "$ROOT/tests/TestServer.swift" -o "$BUILD/test-server"
# Public test fixture key, never a production pairing key.
KEY=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f
PORT="${TEST_PORT:-47492}"
"$BUILD/test-server" "$PORT" "$KEY" > "$BUILD/server.log" 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT
python3 - "$PORT" <<'PY'
import socket,sys,time
for attempt in range(100):
    try:
        with socket.create_connection(('127.0.0.1',int(sys.argv[1])),.2): break
    except OSError: time.sleep(.05)
else: raise SystemExit('Test server failed to start')
PY
java -cp "$BUILD/java" InteropTests 127.0.0.1 "$PORT" "$KEY"
