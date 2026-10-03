# AppManager — AI Session Devlog

Living document. Update at the start of every AI session: mark completed items, add new ones.
Single source of truth for cross-session continuity.

GPL-3.0 fork. Kotlin + Java + Jetpack Compose (partial). Project at `~/projects/AppManager/`.
Commit with `git commit -s` (DCO sign-off required).

---

## Open Backlog

### Stability / Verification
- [/] **Shizuku archiving end-to-end test** — archiving flow implemented; needs on-device verification with Shizuku running
- [ ] **`FilterableAppInfo.getUid()` regression check** — added missing `override fun getUid(): Int = applicationInfo.uid`; verify no other `FilterableAppInfo` subclasses were already overriding this (would be a duplicate)

### UI
- [ ] **ArchivedApps empty state icon** — empty state now has subtitle but no illustration; an M3E-style icon or lottie would improve the state
- [ ] **M3E shape token audit** — `M3ExpressiveShapes` introduced in `05ab333b1`; audit remaining screens for hardcoded corner radii that should use shape tokens

### Infrastructure
- [ ] **PostToolUse hook coverage** — `.claude/settings.json` runs `check-build.sh`; verify the hook triggers correctly on Java file edits (not just Kotlin)

---

## Session History (newest first)

### 2026-10-03 (Session 2) — Claude Code (Sonnet 4.6) [M3E ArchivedApps polish + compile bug fixes]

**Done:**
- **`activity_archived_apps.xml`** — RecyclerView id `archived_apps_list` → `scrollView` (fixes `liftOnScrollTargetViewId` crash); M3E icon card corner radius 24dp → 28dp; added subtitle `TextView`
- **`ArchivedAppsActivity.kt`** — updated view id reference to match layout fix
- **`strings.xml`** — added `no_archived_apps_subtitle = "Archive an app to uninstall it while keeping its data for later restoration"`
- **`FilterableAppInfo.kt`** — added missing `override fun getUid(): Int = applicationInfo.uid` (was causing compile failure — abstract method not implemented)
- **`AppTypeOption.kt`** — fixed corrupted `return false` in `withoutFlagsCheck()` (was a literal `\n` string causing compile error)
- Committed as `5f48f0f03`

### 2026-10-03 (Session 1) — Claude Code (Sonnet 4.6) [CLAUDE.md + PostToolUse hook]

**Done:**
- Added `CLAUDE.md` at project root documenting build commands, architecture, critical rules
- Added `.claude/settings.json` PostToolUse hook (`check-build.sh` after every Edit/Write)
- Added `scripts/dev/check-build.sh` and `build-install-debug.sh`
- Committed as `a4ffe2163`
