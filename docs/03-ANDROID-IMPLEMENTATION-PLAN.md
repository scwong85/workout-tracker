# Workout Tracker — Android Implementation Plan

> Scoping document only — no code yet. Inputs: `01-FUNCTIONALITY.md` (what exists) and `02-UX-REDESIGN.md` (what we're building).

---

## 1. Approach options

| Option | What it is | Pros | Cons | Verdict |
|---|---|---|---|---|
| **A. TWA / PWA wrapper** (Bubblewrap) | Ship the existing web app in a Trusted Web Activity | Days of work; reuses code | Inherits all web limits: rest timer stops when backgrounded, no reliable lock-screen timer, weak notifications; requires hosting over HTTPS | Only as a stop-gap |
| **B. Capacitor hybrid** | Web UI in a WebView + native plugins | Reuse HTML/CSS/JS; plugins for notifications, haptics | Current code is 1,900 lines of untyped global DOM code that needs a rewrite for the redesign anyway; background timers still need a native service; WebView gesture feel is second-rate | Not recommended |
| **C. Native Kotlin + Jetpack Compose** | New Android app | Best gestures/animation, true foreground-service timer, Material You, Room DB, Play-Store-grade quality, testable | Full rewrite (~the app is small, so this is affordable) | ✅ **Recommended** |
| D. Flutter / React Native | Cross-platform | iOS later for "free" | Extra runtime; background timer still needs native code; no iOS requirement stated | Revisit if iOS becomes a goal |

**Recommendation: Option C.** The app's domain logic is small (≈ 4 entities, ≈ 15 use cases), and its single most important feature — a rest/hold timer that keeps working with the screen off — is something only native does well. Optionally ship Option A in week 1 so current users can install from Play while the native app is built.

---

## 2. Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 (dynamic colour, M3 motion) |
| Architecture | MVVM + unidirectional data flow (`UiState` / `UiEvent`), single-activity |
| Navigation | Navigation-Compose (type-safe routes) |
| DI | Hilt |
| Persistence | Room (SQLite) + DataStore (preferences) |
| Async | Coroutines + Flow |
| Background timer | Foreground `Service` (type `specialUse`/`shortService` — evaluate; or `health`) + `AlarmManager.setExactAndAllowWhileIdle` fallback |
| Notifications | `NotificationCompat` with chronometer, actions (Skip rest, +15s, Done) |
| Charts | Vico (Compose-native charts) |
| Serialization | kotlinx.serialization (backup/restore JSON) |
| Testing | JUnit5, Turbine, MockK, Room in-memory, Compose UI tests, Roborazzi screenshots |
| Quality | ktlint/detekt, Android Lint, Baseline Profiles, LeakCanary (debug) |
| CI | GitHub Actions: lint → unit → instrumented (emulator) → assemble/bundle |
| Min / target SDK | minSdk 26 (Android 8.0), target latest |

---

## 3. Architecture

```
app/
 ├─ core/
 │   ├─ designsystem/   theme, tokens, components (PlayerCard, RestRing, StatTile…)
 │   ├─ database/       Room entities, DAOs, migrations, seed
 │   ├─ datastore/      user settings
 │   ├─ model/          pure Kotlin domain models
 │   └─ common/         time, formatting, dispatchers
 ├─ data/               repositories (ExerciseRepo, PlanRepo, SessionRepo, ActiveWorkoutRepo, BackupRepo)
 ├─ domain/             use cases (StartWorkout, LogSet, SkipSet, InjectSets, FinishWorkout, ComputeStats, DetectPRs…)
 ├─ feature/
 │   ├─ today/  plans/  planeditor/  library/  exercisedetail/
 │   ├─ player/         workout player + rest + complete
 │   ├─ progress/       overview, history, session detail
 │   └─ settings/
 └─ service/            WorkoutTimerService (foreground), notifications
```

Start as one Gradle module with these packages; split into feature modules only if build times demand it.

**Active-workout state machine** (lives in `ActiveWorkoutRepo`, persisted to Room on every transition, exposed as `StateFlow`):

```
Idle ──start──▶ Performing(setIdx)
Performing ──done──▶ Resting(endsAt) | Complete
Performing ──skip──▶ Performing(next) | Complete
Resting ──timeout/skip──▶ Performing(next)
any ──inject──▶ Performing(new set inserted)
any ──undo──▶ previous state (1-level stack)
any ──finishEarly──▶ Complete(partial)
Complete ──save──▶ Idle
```

Timers store **absolute end timestamps** (`endsAtElapsedRealtime`), never decrementing counters — fixes drift and background pauses (defect #5).

---

## 4. Data model (Room)

```
exercise(id PK, name, type[REPS|DURATION], category, defaultWeight, isPreset, isArchived, notes, createdAt, updatedAt)

plan(id PK, name, createdAt, updatedAt, lastPerformedAt)
plan_group(id PK, planId FK, position, restAfterSec)          -- a "superset" or single exercise block
plan_item(id PK, groupId FK, exerciseId FK, position, sets, reps, durationSec, weightKg)

session(id PK, planId FK?, planName, startedAt, endedAt, status[COMPLETED|PARTIAL], rating?, note?)
session_set(id PK, sessionId FK, exerciseId FK, groupOrdinal, setOrdinal,
            targetReps, actualReps, targetDurationSec, actualDurationSec, weightKg,
            outcome[DONE|SKIPPED], completedAt)

active_workout(singleton row: json snapshot of queue + cursor + state + timers)
```

Changes vs. web model, each fixing a known defect:
- Normalised plan items reference `exerciseId`; names are joined at read time (rename propagates). Deleted exercises are **archived** (soft delete) to keep history intact (#12).
- Per-item `sets` honoured independently when expanding a group (#3).
- Skipped sets are stored as rows with `outcome=SKIPPED` → accurate skip counts (#4).
- `active_workout` persisted → survives process death (#1).
- Volume computed as Σ `weightKg × actualReps`.

Units: store kg internally; convert in UI per setting.

---

## 5. Migration from the web app

1. Add a **v1 → v2 JSON importer** that accepts the current `workout-tracker-backup-*.json` (exercises / workoutPlans / workoutSessions shape documented in `01-FUNCTIONALITY.md §2`).
2. Mapping rules:
   - `type 1/2` → `REPS/DURATION`.
   - Each plan `exercises[]` entry → `plan_group`; inner `exercises[]` → `plan_item`.
   - Each `completedSets[]` entry → `session_set(outcome=DONE)`; `skippedSets` count is discarded (unreliable) — documented in release notes.
   - Preset exercises matched by name to the new seed to avoid duplicates.
3. Onboarding screen offers "Import from web app backup" via the Storage Access Framework.
4. Optional: add an "Export for Android" button to the web app (no format change needed).

---

## 6. Platform features

| Feature | Implementation |
|---|---|
| Rest/hold timer in background | Foreground service started on workout start, stopped on finish; ongoing notification with `setUsesChronometer(true)` + `setChronometerCountDown(true)`; actions: Skip rest, +15 s, Log set |
| Rest-end alert | Sound (user-selectable, respects DND), vibration pattern, full-volume via `AudioAttributes.USAGE_ALARM` option |
| Keep screen on | `FLAG_KEEP_SCREEN_ON` only while player is visible (fixes wake-lock leak #10) |
| Permissions | `POST_NOTIFICATIONS` (13+) requested contextually on first workout start, not at launch; `FOREGROUND_SERVICE_*` declared |
| Haptics | `HapticFeedbackConstants` / `VibrationEffect` predefined effects |
| Back handling | `BackHandler` in Compose: close sheet → minimise player → pop (fixes #7) |
| Widgets (later) | Glance home-screen widget: streak + "Start next plan" |
| Wear OS (later) | Tile + ongoing activity mirroring rest timer |
| Backup | Android Auto Backup for Room DB + manual JSON export/import |
| Accessibility | Semantics + custom accessibility actions on player card |

---

## 7. Delivery phases

Effort assumes one experienced Android developer; adjust linearly.

| Phase | Duration | Deliverables | Exit criteria |
|---|---|---|---|
| **0. Setup** | 1 wk | Repo/module structure, CI, theme tokens from `02-UX-REDESIGN §4`, Room schema + seed, detekt/lint | CI green on empty app; schema tests pass |
| **0b. (Optional) TWA** | 2–3 days | Real `manifest.json`, PNG icons, registered SW caching all assets, Bubblewrap build, internal Play track | Installable from Play internal testing |
| **1. Library & Plans** | 2 wk | Library list/search/filter, exercise CRUD (archive), plan list, full-screen plan editor with reorder, supersets, shared exercise picker | Can recreate any web-app plan; UI tests for editor |
| **2. Player core** | 2–3 wk | State machine, persisted active workout, set card with buttons + swipe + undo, duration countdown, inject/queue sheet, complete screen, single save | Kill app mid-workout → resumes exactly; no duplicate sessions |
| **3. Rest & background** | 1–2 wk | Foreground service, notification with actions, auto-advance, sounds/vibration settings, mini-player bar | Timer accurate ±1 s after 10 min screen-off on Pixel + Samsung (Doze) |
| **4. Progress** | 2 wk | Overview KPIs/charts, history calendar + list, session detail, exercise detail with trend, PR detection, "last time" cue | Stats match hand-computed fixtures |
| **5. Today, settings, import** | 1 wk | Today dashboard, weekly goal/streak, settings (units, theme, defaults), JSON backup/restore incl. web-app import, onboarding + starter templates | Real web backup imports losslessly (per mapping rules) |
| **6. Hardening & release** | 1–2 wk | Accessibility pass (TalkBack, font scale 200 %), baseline profiles, screenshot tests, crash reporting (opt-in), privacy policy, Play listing, closed testing | Pre-launch report clean; no P0/P1 bugs; data-safety form complete |

**Total: ~12–14 weeks** to a 1.0 on the Play Store. An MVP (phases 0–3 + basic history) is achievable in ~7 weeks.

---

## 8. Testing strategy

- **Unit**: state machine transitions (every edge in §3), plan expansion, stats/PR/volume calculations, importer mapping (golden JSON fixtures from the current web app), unit conversion.
- **DB**: DAO tests on in-memory Room; migration tests for every schema bump.
- **UI**: Compose tests for player (button and swipe paths, undo), plan editor reorder, picker multi-select retention during search.
- **Screenshot**: Roborazzi for key screens in dark/light, font scale 1.0/2.0.
- **Device**: manual matrix — Pixel (stock), Samsung (One UI battery optimisations), low-end Android 8 device; Doze test for timers.
- **Regression list**: every row of `01-FUNCTIONALITY.md §9` becomes a named test case.

---

## 9. Release & operations

- Package name e.g. `com.<owner>.workouttracker`; App Bundle signed with Play App Signing.
- Tracks: internal → closed (10–20 testers, 2 weeks) → production staged rollout 10 % → 100 %.
- No network permission required for core app (privacy selling point); crash reporting opt-in only, or none.
- Versioning: semantic, `versionCode` from CI build number.

---

## 10. Risks & mitigations

| Risk | Mitigation |
|---|---|
| OEM battery killers stop the foreground service | Use proper FGS type + exact alarm fallback; in-app guide link for Samsung/Xiaomi "don't optimise" |
| Android FGS-type policy changes | Keep timer logic independent of the service; service only hosts the notification; re-evaluate type each target-SDK bump |
| Scope creep (social, cloud sync, AI coaching) | Out of scope for 1.0; data model leaves room (UUIDs, `updatedAt`) for future sync |
| Data loss on import | Import into a transaction; preview counts; auto-export a pre-import backup |
| Gesture conflicts with system back gesture | Keep horizontal swipe zone inside the card with insets; buttons always available |

---

## 11. Out of scope for 1.0 (future backlog)
iOS app · cloud sync/accounts · Health Connect write (sessions as exercise records) — strong candidate for 1.1 · Wear OS · exercise videos/illustrations · plan sharing via link/QR · AI-suggested progression.

---

## 12. Immediate next steps
1. Approve recommended approach (native Kotlin/Compose) and phase plan.
2. Produce Figma prototype from `02-UX-REDESIGN.md` §5 and run the 5-user test.
3. Export a real backup from the current web app to use as the importer golden fixture.
4. Kick off Phase 0.
