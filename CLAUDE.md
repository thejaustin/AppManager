# AppManager — Claude Code Guidelines

Fork of [MuntashirAkon/AppManager](https://github.com/MuntashirAkon/AppManager) with Shizuku integration and M3 Expressive enhancements.
Path: `/data/data/com.termux/files/home/projects/AppManager/`
Package: `io.github.muntashirakon.AppManager`

## Build commands

Local Android SDK configured (`local.properties` → `sdk.dir=/data/data/com.termux/files/home/android-sdk`). Local builds work.

| Task | Command |
|------|---------|
| Compile check (fast) | `bash scripts/dev/check-build.sh` |
| Build + install debug | `bash scripts/dev/build-install-debug.sh` |
| Full debug APK | `./gradlew :app:assembleDebug` |
| Unit tests | `./gradlew test` |

CI (GitHub Actions): `tests.yml`, `lint.yml`, `release.yml`, `release_build.yml`, `codeql.yml`

## Module structure

| Module | Purpose |
|--------|---------|
| `:app` | Main app (Java + Kotlin, Hilt DI) |
| `:libcore:common` | Shared utilities |
| `:libcore:compat` | Android API compatibility |
| `:libcore:io` | I/O utilities |
| `:libcore:ui` | Shared UI components |
| `:libopenpgp` | OpenPGP signing |
| `:libserver` | Privileged server (runs as root/ADB via Shizuku) |

## Critical rules

| Rule | Why |
|------|-----|
| All commits MUST be signed off | Upstream requires `Signed-off-by` line. Use `git commit -s` always. Never omit. |
| License headers in new files | Apache-2.0 AND GPL-3.0-or-later. Copy from existing files. |
| `:libserver` runs privileged | Code in libserver executes as root or ADB shell. No UI code, no Context-dependent calls, no Android framework APIs that require user context. |
| Shizuku binder check | Always guard with `ShizukuApiConstants.BINDER_TRANSACTION` / `Shizuku.pingBinder()` before any Shizuku IPC call. |

## On-device testing

ADB loopback: `adb connect 127.0.0.1:5555`

Fastest path: `bash scripts/dev/build-install-debug.sh`

Most features require root or ADB shell. For ADB-only testing:
- Shizuku must be running: `rish -c "am start-service io.shizuku.manager/moe.shizuku.manager.ShizukuService"` or just use the ShizukuPlus app.
- Test privileged ops: `adb -s 127.0.0.1:5555 shell` then run commands manually.

## Shizuku integration (added to fork)

`ShizukuUtils.java` handles Shizuku-related operations. `BatchOpsManager.opArchive()` uses Shizuku to execute `pm uninstall -k` when available, falling back to stock approach otherwise.

## Key files

| Area | File |
|------|------|
| Main activity | `app/src/main/java/.../MainActivity.java` |
| App info | `app/src/main/java/.../details/AppInfoFragment.java` |
| Batch ops | `app/src/main/java/.../batchops/BatchOpsManager.java` |
| Shizuku utils | `app/src/main/java/.../utils/ShizukuUtils.java` |

## Git / commits

- Sign every commit: `git commit -s`
- Stage files by name, never `git add -A`
- Never amend published commits
