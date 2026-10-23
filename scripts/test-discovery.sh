#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BUILD_DIR:-$ROOT/.build/tests}"
mkdir -p "$BUILD"
swiftc -swift-version 5 "$ROOT/macos/Protocol.swift" "$ROOT/macos/Server.swift" "$ROOT/macos/Discovery.swift" "$ROOT/tests/MacDiscoveryTests.swift" -o "$BUILD/discovery-tests"
"$BUILD/discovery-tests"
