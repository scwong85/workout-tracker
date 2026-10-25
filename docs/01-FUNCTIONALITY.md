# Workout Tracker — Functional Specification (Current State)

> Scope: describes what the app in this repository **actually does today** (as of commit `7ab8c29`), based on reading `workout.html`, `scripts.js`, `styles.css` and `sw.js`. Where the code differs from `README.md`, the code wins and the difference is flagged.

---

## 1. Overview

Workout Tracker is a single-page, mobile-first web app (vanilla HTML/CSS/JS, no build step) for planning and running bodyweight / strength workouts. Its signature interaction is a **swipeable card** workout player: each set appears as a card, which the user swipes right to complete, left to skip, or up to add ("inject") more exercises.

All data lives on-device in **IndexedDB**. There is no account, backend or sync.

| Item | Value |
|---|---|
| Entry point | `workout.html` |
| Logic | `scripts.js` (~1,900 lines, one global `app` object + free functions) |
| Styling | `styles.css` (dark theme, CSS variables) |
| Charts | Chart.js 4.4.0 from jsDelivr CDN |
| Storage | IndexedDB `WorkoutTrackerDB` v1 |
| PWA | Manifest generated at runtime as a Blob URL; service worker file exists but is **not registered** (see §9) |

---

## 2. Domain model

### 2.1 Exercise (`exercises` store, key `id`, indexes `name`, `category`)

```js
{
  id: string,            // generateId(): base36 timestamp + random
  name: string,
  type: 1 | 2,           // 1 = reps-based, 2 = duration-based (seconds)
  category: 'chest'|'back'|'legs'|'core'|'arms'|'shoulders'|'full-body',
  defaultWeight?: number,// kg, custom exercises only (not used anywhere else)
  isPreset: boolean,
  createdAt: number      // epoch ms
}
```

60 preset calisthenics exercises are seeded on first launch (only if the store is empty), e.g. push-up variants, pull-up variants, planks, squats, lunges, burpees, jump rope.

### 2.2 Workout plan (`workoutPlans` store, key `id`)

A plan is an ordered list of **exercise sets**. One exercise set can hold **several exercises** that are performed together on one card (i.e. a superset/circuit).

```js
{
  id, name, createdAt, updatedAt,
  exercises: [                       // "exercise sets"
    {
      setIndex: number,
      restAfterSet: number,          // seconds
      exercises: [
        { exerciseId, exerciseName, type, sets, reps, duration, weight }
      ]
    }
  ]
}
```

Note: `exerciseName` and `type` are **denormalised** copies — renaming an exercise does not update existing plans.

### 2.3 Workout session (`workoutSessions` store, key `id`, index `startTime`)

```js
{
  id, workoutPlanId | null, workoutPlanName,
  startTime, endTime,
  completedSets: [                    // one entry PER EXERCISE completed
    { exerciseId, exerciseName, type, reps, duration, weight, completedAt }
  ],
  skippedSets: number,
  status: 'completed'
}
```

### 2.4 Settings (`settings` store, key `key`)

Created in the schema but **never read or written**.

### 2.5 In-memory active workout (`app.currentWorkout`)

```js
{
  planId, planName,
  sets: [ { exercises: [...], restAfterSet, originalSetIndex? } ], // expanded queue
  currentSetIndex, completedSets: [...], startTime, isOngoing
}
```

This is held **in memory only**: reloading the page or the OS killing the tab loses the in-progress workout.

---

## 3. Navigation & layout

- Fixed **hamburger button** (top-left) opens a slide-in **sidebar** with an overlay.
- Sidebar pages: 🏠 Home · 📋 Workout Plans · 🏋️ Exercises · 📊 Statistics · 📅 History · ⚙️ Settings.
- A hidden seventh page, `active-workout`, hosts the workout player.
- All forms are centred **modals** (`.modal`): Exercise, Plan, Add Exercise Set, Select Plan, Inject Exercise, and a dynamically created Exercise Stats modal.
- Feedback via a bottom **toast** (3s), `navigator.vibrate` haptics, and native `alert/confirm/prompt` dialogs.

### 3.1 Back-button handling (`setupBackButtonNavigation`)
1. If a modal is open → close it. *(Intended; see §9 — the modal stack is never populated, so this branch never fires.)*
2. If a workout is ongoing and the user is on the player → go to Home and keep the workout resumable.
3. Otherwise load the page from `history.state`, defaulting to Home.

---

## 4. Screens & features

### 4.1 Home
- Greeting "Welcome Back!".
- **Quick Start** card → *Start New Workout* button. If a workout is active it first asks to terminate it.
- **Active workout** card (only if `currentWorkout.isOngoing`): plan name, sets logged, sets queued, *Resume* button.
- **Recent Workouts**: last 5 sessions (plan name, date/time, completed-set count). Not tappable.
- Empty state: "No workouts yet. Start your first workout!"

### 4.2 Select Plan modal (from *Start New Workout*)
- *+ Create New Plan* → navigates to Workout Plans page.
- *Start Empty Workout* → starts a "Custom Workout" with no sets and immediately opens the Inject modal.
- List of saved plans; tapping one starts it.

### 4.3 Workout Plans
- *+ Create New Plan*.
- Each plan card: name, number of exercise sets, and four icon buttons: ▶️ start · 📋 duplicate (adds " (Copy)") · ✏️ edit · 🗑️ delete (confirm).

**Plan editor modal**
- Plan name (required).
- List of exercise sets showing each exercise as `name: N sets × R reps|Ds @ Wkg` and the rest time; each set can be deleted. Sets **cannot be reordered or edited** in place.
- *+ Add Exercise Set* opens the **Add Exercise Set modal**:
  - Searchable, category-grouped checkbox list (selection is preserved while filtering).
  - *Sets* (default 3) and *Rest After Set* (default 60s) — applied to all selected exercises.
  - Per-selected-exercise config block: reps (default 10) **or** duration (default 30s), plus weight.
  - Global *Reps / Duration / Weight* fields are also rendered but **ignored** — only the per-exercise values are saved.
  - *Add to Plan* appends one exercise set containing all selected exercises.

### 4.4 Exercises (library)
- Live search by name.
- *+ Create Custom Exercise* → modal with Name, Type (Reps/Duration), Category, Default Weight.
- List grouped by category; each row shows a Reps/Duration badge and a Preset badge.
- Tapping an exercise opens the **Exercise Stats modal**:
  - Type & category badges.
  - Lifetime totals: sets, total reps + max reps (reps type) or total time (duration type), total & max weight if any.
  - Last 10 sessions containing the exercise (date, sets).
  - Custom exercises: *Edit* and *Delete*. Presets: *Close* only.
  - Can be dismissed by swiping the sheet down >100px.

### 4.5 Active workout player
The player renders exactly one of three cards.

**a) Set card**
- "Set X of Y", title = exercise names joined with " + ".
- For each exercise: big gradient value (reps, or `m:ss` for duration), unit + weight, and −/+ buttons to adjust the target by 1 before completing.
- Gestures (touch only):
  - Swipe **right** (>30% screen width) → **complete**: logs one `completedSets` entry per exercise, advances, starts the rest timer (unless it was the last set).
  - Swipe **left** → **skip**: advances without logging, no rest.
  - Swipe **up** (>100px) → open **Inject** modal.
  - Otherwise snaps back. Card rotates/fades while dragging.
- Duration exercises show the target only — **there is no exercise countdown timer**.

**b) Rest card**
- Purple gradient card with circular progress ring and `m:ss` countdown.
- −30s / +30s buttons.
- Swipe right → end rest early; swipe up → inject.
- At 0: plays an 800 Hz beep (Web Audio) and shows a system Notification "Rest Complete!" if permission was granted. The card **does not auto-advance**; the user must swipe right.

**c) Workout Complete card**
- 🎉, "You completed N sets" (N = number of exercise entries, not cards).
- The session is saved to IndexedDB when this card renders.
- *Back to Home*; for plan-less workouts also *Save as New Plan* (prompt for name; groups identical exercise/rep/weight entries into sets, 60s rest).
- Swipe right → end and go Home; swipe up → inject more exercises and continue.

**Inject modal**
- Same searchable multi-select picker as Add Exercise Set, plus *Rest After*.
- Inserts one new set **before** the current set (it becomes the next card), cancels any running rest, re-renders.

**Device integrations**: Screen Wake Lock requested on workout start (never released); Notification permission requested on app load; vibration patterns on most actions.

### 4.6 Statistics
- Range chips: Last Week · Last 30 Days · Last Year · All Time.
- Tiles: Total Workouts, Total Sets.
- Line chart (Chart.js): completed exercise entries per calendar day.
- **Exercise Breakdown** cards: sets, total/max reps or total time, total/max weight. ("Total weight" is a sum of per-set weights, not volume = weight × reps.)

### 4.7 History
- All sessions, newest first: plan name, date/time, completed-set count, duration in minutes, 🗑️ delete (confirm).
- Sessions are **not openable** — no per-set detail view.

### 4.8 Settings
- **Export Data** → downloads `workout-tracker-backup-<ts>.json` with exercises, plans, sessions.
- **Import Data** → file picker, confirm, then **replaces** exercises/plans/sessions.
- **Delete All Data** → double confirm, clears stores, re-seeds presets.

---

## 5. Key user flows

```
Plan a workout:  Plans → + Create → name → + Add Exercise Set → pick exercises
                 → set sets/rest + per-exercise reps/weight → Add → (repeat) → Save

Run a workout:   Home → Start New Workout → pick plan
                 → [Set card] swipe → [Rest card] wait/swipe → … → [Complete] → Home

Ad-hoc workout:  Home → Start New Workout → Start Empty Workout → Inject → … → Complete
                 → Save as New Plan (optional)

Review:          Statistics (range) | History | Exercises → tap exercise
```

---

## 6. Data lifecycle

| Event | Effect |
|---|---|
| First launch | Seed 60 presets |
| Start plan | Plan is **expanded**: each exercise set is repeated `exercises[0].sets` times into a flat queue |
| Complete set | Append one record per exercise to `completedSets` |
| Reach end of queue | Session written to `workoutSessions` |
| Terminate active workout | Discarded — **no partial session saved** |
| Import | Replace all three main stores |
| Delete all | Clear and re-seed |

---

## 7. Non-functional characteristics

- **Platform**: designed for mobile Chrome/Safari portrait; gestures are touch-only, so desktop mouse users cannot complete/skip sets.
- **Offline**: IndexedDB works offline, but assets are not cached (no SW), and Chart.js is loaded from a CDN.
- **Accessibility**: emoji-only icon buttons with no labels, swipe-only primary actions, no focus management in modals, small secondary text contrast on gradients.
- **Security**: user-entered names are interpolated into `innerHTML` and inline `onclick` strings (an apostrophe in a name breaks handlers; HTML in a name is rendered).

---

## 8. README vs. code discrepancies

| README says | Code does |
|---|---|
| Open `Workout Tracker.html` | File is `workout.html` |
| "Edit, delete, duplicate plans" | ✅, but sets inside a plan can only be added/removed, not edited/reordered |
| Works offline after first load | No service worker is registered; offline only if browser cache happens to hold assets |
| Back button closes cards first | `app.modalStack` is never populated, so it doesn't |
| Settings store | Defined but unused |

---

## 9. Known defects & risks (found during review)

| # | Area | Issue |
|---|---|---|
| 1 | Workout state | Active workout is memory-only; lost on reload/tab kill. |
| 2 | Sessions | `renderCongratsCard` saves a session every time it renders → injecting after "Complete" and finishing again saves a **duplicate** session; the first save also sets `isOngoing=false`, so the continued workout no longer shows on Home. |
| 3 | Plan expansion | Repeat count uses only `exercises[0].sets`; other exercises' `sets` in the same group are ignored. |
| 4 | Stats | `skippedSets = sets.length − completedSets.length` mixes cards and per-exercise entries → wrong for multi-exercise sets. |
| 5 | Rest timer | `setInterval` decrement drifts / pauses when backgrounded; progress ring is never updated after first paint; ring denominator uses `sets[currentSetIndex−1]`, wrong after injection. |
| 6 | Gestures | `currentX/currentY` aren't reset on `touchstart`; a tap on −/+ after an aborted drag can reuse stale deltas and trigger complete/skip. |
| 7 | Back button | Modal-closing branch is dead code (no `modalStack`). |
| 8 | PWA | Manifest via Blob URL with SVG data-URI icons → not reliably installable; `sw.js` not registered (a placeholder `data:` URL is registered and fails); `sw.js` caches an unused JSZip and not `scripts.js`/`styles.css`. |
| 9 | Notifications | `icon: '💪'` is not a valid URL. |
| 10 | Wake lock | Never released after workout ends. |
| 11 | Empty workout | Closing the Inject modal on an empty workout leaves a blank player screen. |
| 12 | Exercise edit | Saving an edit overwrites `createdAt`; deleting an exercise leaves orphaned references in plans. |
| 13 | Unused inputs | Global reps/duration/weight fields in Add-Set and Inject modals are ignored. |
| 14 | XSS / quoting | Unescaped user strings in HTML templates and inline handlers. |
| 15 | Chart dates | Dates bucketed via `toLocaleDateString()` and re-parsed with `new Date()` — locale-fragile sorting. |

These should be carried forward as acceptance criteria for any rewrite (see `03-ANDROID-IMPLEMENTATION-PLAN.md`).
