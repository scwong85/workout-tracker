# Workout Tracker — Android

Native Kotlin + Jetpack Compose rewrite of the web app, following
[`docs/03-ANDROID-IMPLEMENTATION-PLAN.md`](../docs/03-ANDROID-IMPLEMENTATION-PLAN.md) and the
redesign in [`docs/02-UX-REDESIGN.md`](../docs/02-UX-REDESIGN.md).

## Build

Requirements: JDK 17, Android SDK (compileSdk 35). From this directory:

```bash
./gradlew :domain:test            # pure-Kotlin logic tests (no Android SDK needed)
./gradlew :app:testDebugUnitTest  # app JVM tests
./gradlew :app:lintDebug
./gradlew :app:assembleDebug      # → app/build/outputs/apk/debug/
```

CI (`.github/workflows/android.yml`) runs the same steps but is currently **manual only**
(Actions → Android CI → Run workflow).

## Modules

| Module | What lives there |
|---|---|
| `:domain` | Pure Kotlin, no Android. Models, presets/templates, the `WorkoutEngine` state machine, plan edits, stats/records/progress, backup + web-app importer. Everything with real logic is here so it can be unit-tested on the JVM. |
| `:app` | Android: Room database, DataStore settings, repositories, Compose UI, foreground service, alarms and notifications. |

```
app/src/main/kotlin/com/workouttracker/app/
 ├─ AppContainer.kt        manual DI (repositories, scopes, background glue)
 ├─ data/db                Room entities, DAOs, mappers
 ├─ data/repo              Exercise/Plan/Session/ActiveWorkout/Settings/Backup repositories
 ├─ service                Foreground service, notification, exact alarms, alerts
 └─ ui                     today · plans · library · player · progress · settings · components · theme
```

### Key design decisions

- **Active workout is persisted on every transition** (`active_workout` table, JSON of
  `ActiveWorkout`). Killing the app mid-set and reopening resumes exactly where you were.
- **Timers are absolute wall-clock timestamps** (`endsAt`), never decrementing counters, so they
  don't drift and survive process death. The notification uses the platform chronometer, which
  keeps counting even while the CPU sleeps; an exact alarm wakes the app at rest end to alert.
- **All transitions are pure functions** in `WorkoutEngine`; the repository serialises them with
  a mutex and the UI, notification buttons and alarms all go through the same path.
- **Gestures accelerate, buttons guarantee**: every swipe on the set card has a visible button,
  a TalkBack custom action, and an Undo snackbar.

## Milestones

| | Milestone | Status |
|---|---|---|
| M0 | Project, Room schema, theme tokens, nav shell, CI | ✅ built green on CI |
| M1 | Library, exercise editor, plans list, full-screen plan editor, exercise picker | ✅ built green on CI |
| M2 | Workout engine, persisted active workout, player (swipe + buttons + undo, hold timer, rest, queue, add exercise, complete screen), mini-player | ✅ code complete |
| M3 | Foreground service, ongoing notification with actions, exact alarms, sound/vibration alerts, notification permission | ✅ code complete |
| M4 | Progress overview (KPIs + deltas, charts, muscle split, PRs), history calendar, session detail, exercise insights | ✅ code complete |
| M5 | Today dashboard, settings, backup/restore, web-app import, starter plans, onboarding | ✅ code complete |
| M6 | Hardening: review pass, mapper tests, service start race fix, docs | ✅ partial — see below |

### How M2–M6 were verified

CI was switched to manual after M1, and the development container can't download the Android
SDK, so the Android build itself has **not** been run since M1. What was checked instead:

- `:domain` — 45 unit tests, all passing (engine transitions, the web app's defects as regression
  tests, stats, progress, backup/import with a fixture shaped like the web export).
- All UI and data code type-checked against the real Compose 1.7 / Material 3 1.3 APIs (desktop
  artifacts), with small stubs only for Android/Room/DataStore/Navigation types.
- `MappersTest` (Room entity ⇄ domain) passing.
- `service/` and `MainActivity` (Android-only APIs) reviewed by hand, not compiled.

**Before merging, run the manual CI workflow (or `./gradlew :app:lintDebug :app:assembleDebug`
locally)** and fix anything it reports.

### Deviations from the plan

| Plan | Implemented | Why |
|---|---|---|
| Hilt | Manual `AppContainer` | Small app; fewer moving parts and faster builds |
| Vico charts | Canvas charts in `ui/components/Charts.kt` | Three simple chart types didn't justify a dependency |
| Drag-to-reorder | Up/down buttons (plan editor and queue) | Accessible by default; drag can be layered on later |
| `elapsedRealtime` timers | Wall-clock epoch ms | Must survive reboot/process death and be persisted |
| `skippedSets` imported | Dropped on web import | The web app's count was unreliable (see `01-FUNCTIONALITY.md` §9 #4) |

### Known gaps / next steps

- Instrumented tests (Room DAO, Compose UI) and screenshot tests aren't written yet.
- Baseline profile, crash reporting, Play listing and privacy policy (plan Phase 6).
- Very large font scales can clip the player's numbers; add auto-sizing.
- New plan items use fixed defaults (3 × 10, 30 s); seeding from "last time" would be nicer.
