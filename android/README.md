# Workout Tracker — Android

Native Kotlin + Jetpack Compose rewrite of the web app, following
[`docs/03-ANDROID-IMPLEMENTATION-PLAN.md`](../docs/03-ANDROID-IMPLEMENTATION-PLAN.md) and the
redesign in [`docs/02-UX-REDESIGN.md`](../docs/02-UX-REDESIGN.md).

## Building the app

### Option A — Android Studio (easiest)

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug or newer). It bundles
   JDK 17+ and installs the Android SDK on first launch.
2. **File → Open…** and choose the **`android/`** folder (not the repo root).
3. Let Gradle sync finish. If prompted, install **Android SDK Platform 35**.
4. Pick a device (a phone with USB debugging, or an emulator from Device Manager) and press **Run ▶**.

### Option B — Command line

**Requirements**

- JDK 17 or newer (`java -version`)
- Android SDK with **Platform 35** and **Build-Tools 35**. With only the command-line tools:
  ```bash
  sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
  ```
- Tell Gradle where the SDK is, either by setting `ANDROID_HOME`, or by creating
  `android/local.properties` (gitignored):
  ```properties
  sdk.dir=/path/to/Android/sdk
  ```

The Gradle wrapper downloads the right Gradle version itself; there's no need to install Gradle.

**Build and install** (from the `android/` directory):

```bash
cd android
./gradlew :app:assembleDebug      # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug       # build + install on the connected phone/emulator
```

Or install a built APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
On Windows, run the commands from the `android` folder with `.\gradlew.bat` instead of
`./gradlew`. PowerShell needs the leading `.\`; in Command Prompt plain `gradlew` works:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

The debug build installs as **Workout Tracker** with id `com.workouttracker.app.debug`, so it can
sit alongside a future release build.

**Checks**

```bash
./gradlew :domain:test            # pure-Kotlin logic tests (no Android SDK needed)
./gradlew :app:testDebugUnitTest  # app JVM tests
./gradlew :app:lintDebug          # report → app/build/reports/lint-results-debug.html
```

### Installing on your phone

1. On the phone: **Settings → About phone →** tap **Build number** 7 times to enable Developer options.
2. **Settings → System → Developer options →** turn on **USB debugging**
   (or **Wireless debugging** and pair with `adb pair`).
3. Connect the phone, accept the "Allow USB debugging?" prompt, check `adb devices` lists it,
   then run `./gradlew :app:installDebug`.

Without a computer: copy `app-debug.apk` to the phone and open it. Android asks you to allow
"Install unknown apps" for your file manager or browser the first time.

### Building on GitHub instead

The workflow `.github/workflows/android.yml` runs the tests, lint, and a debug build, then
uploads the APK. It's currently **manual only**: **Actions → Android CI → Run workflow**, choose
the branch, and when it finishes download the **`workout-tracker-debug-apk`** artifact (a zip
containing `app-debug.apk`).

### Release build

```bash
./gradlew :app:assembleRelease    # → app/build/outputs/apk/release/app-release-unsigned.apk
```

Release builds are shrunk with R8 and are **unsigned**, because no signing config is committed.
Sign one for side-loading:

```bash
keytool -genkeypair -v -keystore release.jks -alias workout -keyalg RSA -keysize 2048 -validity 10000
$ANDROID_HOME/build-tools/35.0.0/zipalign -p 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
$ANDROID_HOME/build-tools/35.0.0/apksigner sign --ks release.jks --out workout-tracker.apk aligned.apk
```

Keep `release.jks` and its password safe and out of git: every update must be signed with the
same key. For the Play Store, build an App Bundle with `./gradlew :app:bundleRelease` and use
Play App Signing.

### Troubleshooting

| Problem | Fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME` or create `local.properties` as above |
| `Unsupported class file major version` / JDK errors | Use JDK 17+; in Android Studio set **Settings → Build Tools → Gradle → Gradle JDK** |
| `Failed to find target android-35` | `sdkmanager "platforms;android-35"` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | An APK signed with a different key is installed; uninstall it first |
| Gradle runs out of memory | Raise `org.gradle.jvmargs` in `gradle.properties` |
| No rest alerts with the screen off | Allow notifications for the app; on Android 12+ grant **Alarms & reminders**; on Samsung/Xiaomi exempt the app from battery optimisation |

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
