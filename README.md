# Workout Tracker

A mobile-first workout tracker: build plans from exercises (supersets included), run them as
swipeable set cards with rest timers, add exercises mid-workout, and review history, records and
progress. Everything is stored on the device; there's no account and no server.

The repo contains two apps:

| | Where | Status |
|---|---|---|
| **Android app** (Kotlin + Jetpack Compose) | [`android/`](android/) | New native app; recommended |
| **Web app / PWA** (vanilla HTML/CSS/JS) | `workout.html`, `scripts.js`, `styles.css` | The original version |

Planning docs live in [`docs/`](docs/): what the web app does ([01](docs/01-FUNCTIONALITY.md)),
the UX redesign ([02](docs/02-UX-REDESIGN.md)), and the Android plan ([03](docs/03-ANDROID-IMPLEMENTATION-PLAN.md)).

---

## Android app

### Build and install (quick start)

**With Android Studio:** open the **`android/`** folder, wait for Gradle sync, choose a device and
press **Run ▶**.

**From the command line** (JDK 17+ and Android SDK Platform 35 installed, `ANDROID_HOME` set or an
`android/local.properties` with `sdk.dir=...`):

```bash
cd android
./gradlew :app:assembleDebug     # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug      # build and install on a connected phone or emulator
```

**Without a local setup:** on GitHub go to **Actions → Android CI → Run workflow**, then download
the `workout-tracker-debug-apk` artifact and open the APK on your phone.

Full instructions, covering SDK setup, installing on a phone, signed release builds and
troubleshooting, are in [`android/README.md`](android/README.md#building-the-app).

### Features

- **Workout player:** one card per set. Tap **Done/Skip** or swipe (right = done, left = skip,
  up = add exercise), with **Undo** after each. Timed exercises get a countdown, and supersets
  show together on one card.
- **Rest timer:** keeps running with the screen off. The notification shows the countdown with
  +15s / Skip buttons, and you get sound and vibration when rest ends. Rest can also start the
  next set automatically.
- **Never lose a workout:** progress is saved after every set and survives the app being closed.
- **Plans:** full-screen editor with reordering, supersets, and per-exercise sets/reps/time/weight
  and rest. Five starter plans are included.
- **Progress:** weekly goal and streak, stats with change vs the previous period, workout chart,
  muscle-group split, personal bests, history calendar, and per-exercise trends.
- **Data:** JSON backup and restore, and **import from the web app's export**.

---

## Web app (PWA)

### Run locally

Serve the folder over HTTP; opening the file directly with `file://` limits PWA features:

```bash
python -m http.server 5173
# then open http://localhost:5173/workout.html
```

On Android Chrome you can use **Menu → Install app** to add it to the home screen.

### Features

- Swipe cards: right = complete set, left = skip, up = inject exercises; rest timer with ±30s.
- Exercises (reps- or duration-based, optional weight, 7 categories; 60 calisthenics presets).
- Plans with multi-exercise sets; duplicate, edit and delete.
- History and statistics (Chart.js) with date filters.
- IndexedDB storage, with JSON export/import and delete-all under **Settings**.

Known limitations and bugs are listed in [`docs/01-FUNCTIONALITY.md` §9](docs/01-FUNCTIONALITY.md#9-known-defects--risks-found-during-review).
The Android app fixes them.

### Moving your data to the Android app

1. In the web app: **Settings → Export Data** (saves `workout-tracker-backup-….json`).
2. Copy the file to your phone.
3. In the Android app: **Settings → Restore from a file** (or **Import a backup** on first launch)
   and choose **Merge** or **Replace**.

---

## License

Personal project. Add a license before distributing it publicly.
