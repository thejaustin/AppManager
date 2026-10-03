#!/usr/bin/env bash
# Build universal debug APK and install via ADB loopback.
# Usage: bash scripts/dev/build-install-debug.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

ADB_SERIAL="${ADB_SERIAL:-127.0.0.1:5555}"

echo "▸ Connecting ADB ($ADB_SERIAL)..."
adb connect "$ADB_SERIAL" 2>/dev/null || true

echo "▸ Building universal debug APK..."
./gradlew :app:packageDebugUniversalApk

APK=$(find app/build/outputs/universal_apk/debug -name '*.apk' 2>/dev/null | head -1)
if [[ -z "$APK" ]]; then
  # Fall back to standard debug APK
  ./gradlew :app:assembleDebug
  APK="app/build/outputs/apk/debug/app-debug.apk"
fi

echo "▸ Installing ($APK)..."
adb -s "$ADB_SERIAL" install -r -d "$APK"
echo "✓ Installed AppManager debug APK"
