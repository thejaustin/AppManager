#!/usr/bin/env bash
# Compile-only check: verifies Java+Kotlin compile without producing an APK.
# Fast with a warm Gradle daemon (~20s). Used by PostToolUse hook.
set -euo pipefail
cd "$(dirname "$0")/../.."
./gradlew :app:compileDebugJavaWithJavac :app:compileDebugKotlin "$@"
echo "Compile OK"
