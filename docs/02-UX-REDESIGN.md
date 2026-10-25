# Workout Tracker — UX/UI Redesign Proposal

> Role: senior UI/UX designer. Goal: keep what makes this app special (fast, swipe-driven, offline, no account) and fix what makes it hard to learn, easy to mis-tap, and visually busy. This spec is platform-neutral but written with Android / Material 3 as the primary target (see `03-ANDROID-IMPLEMENTATION-PLAN.md`).

---

## 1. Audit — what's working, what isn't

### Keep
- **Card-per-set player.** One thing on screen at a time is exactly right for someone mid-set, sweaty, glancing at the phone.
- **Inject mid-workout.** Real training is improvised; this is a differentiator.
- **Supersets as first-class** (multiple exercises on one card).
- **Zero setup**: presets, no login, local data.

### Fix
| Problem | Why it hurts | Severity |
|---|---|---|
| Swipe is the *only* way to complete/skip | Undiscoverable, impossible on desktop, inaccessible, accidental swipes have no undo | High |
| Hamburger menu hides the 6 main areas | 2 taps for everything; users forget sections exist (Stats, History) | High |
| No "what's next" visibility in the player | User can't see remaining exercises or overall progress beyond "Set 4 of 18" | High |
| Rest timer doesn't auto-advance; no sound/vibrate options | User must touch the phone after every rest | High |
| Duration exercises have no countdown | The core need of a plank/hold is a timer | High |
| Plan builder: modal-on-modal, form fields that are ignored, no reorder/edit | Confusing and slow; users rebuild plans to change one number | High |
| History rows not tappable; no session detail | Users can't review what they did | Medium |
| Stats show totals, not progress | "Total weight" sum is meaningless; no PRs, streaks or trends per exercise | Medium |
| Native `alert/confirm/prompt` | Jarring, unstyled, blocks UI | Medium |
| Emoji as icons; gradient-heavy text | Inconsistent rendering across devices, poor contrast, feels unpolished | Medium |
| Home is mostly empty | Missed chance to motivate (streak, last workout, suggested next plan) | Medium |

---

## 2. Design principles

1. **One-thumb, one-glance.** Every in-workout action reachable with the right thumb in the bottom 40% of the screen; key number readable from arm's length.
2. **Gestures accelerate, buttons guarantee.** Every swipe has a visible button equivalent. Gestures are a shortcut, never the only path.
3. **Forgiving.** Every destructive or state-changing action is undoable (snackbar "Undo") instead of guarded by confirm dialogs.
4. **Show the path.** Users always know where they are in the workout and what's next.
5. **Progress over totals.** Celebrate improvement (PRs, streaks, trends), not raw sums.
6. **Calm by default, loud at milestones.** Neutral UI; colour and motion reserved for completion, PRs and the rest timer.

---

## 3. Information architecture

Replace the hamburger with a **bottom navigation bar** (4 destinations) + a contextual **FAB**.

```
┌───────────────────────────────────────────────┐
│  Today     Plans     Progress     Library      │  ← bottom nav
└───────────────────────────────────────────────┘
  Settings & Backup → avatar/gear icon in Today's top bar
  History → lives inside Progress (tab: "Overview | History | Exercises")
```

| Destination | Contains | FAB |
|---|---|---|
| **Today** (home) | Resume banner, streak/weekly goal, "Up next" plan suggestion, recent sessions | ▶ Start workout |
| **Plans** | Plan cards, templates | + New plan |
| **Progress** | Overview (charts, streak, PRs) · History (calendar + list) · per-exercise drill-down | — |
| **Library** | Exercise catalogue, filters by category/type, custom exercises | + New exercise |

A persistent **mini-player bar** sits above the bottom nav whenever a workout is active (like a music app): `● Push Day · Set 5/18 · Rest 0:42  [Resume]`. This replaces the Home "Active workout" card and makes resuming one tap from anywhere.

---

## 4. Visual design system

### 4.1 Colour (dark-first, with a real light theme)

Move from "blue + teal + purple gradients everywhere" to one **brand accent** plus semantic colours. Use Material 3 dynamic colour on Android 12+, with this as the fallback seed.

| Token | Dark | Light | Use |
|---|---|---|---|
| `surface` | `#0F1115` | `#FAFAFB` | App background |
| `surfaceContainer` | `#181B21` | `#FFFFFF` | Cards |
| `surfaceContainerHigh` | `#22262E` | `#F1F2F5` | Inputs, chips |
| `onSurface` | `#ECEDEF` | `#14161A` | Primary text |
| `onSurfaceVariant` | `#A3A8B3` | `#5B616C` | Secondary text (≥ 4.5:1) |
| `primary` (Energy) | `#3D8BFF` | `#1F6FEB` | Primary actions, active nav |
| `success` (Done) | `#2FD27A` | `#10A85A` | Complete set, PRs |
| `rest` (Recover) | `#9B8CFF` | `#6B5BE6` | Rest timer only |
| `warning` (Skip) | `#FFB547` | `#C77A00` | Skip action |
| `error` | `#FF6B6B` | `#D93636` | Destructive |

Gradients are allowed in exactly two places: the rest-timer ring and the workout-complete hero.

### 4.2 Typography
- Family: **Inter** (web) / **Roboto Flex** (Android) with tabular numerals for all counters.
- Scale: Display 64/72 (player numbers) · Headline 28 · Title 20 · Body 16 · Label 14 · Caption 12.
- Player rep/time value: Display, weight 700, tabular, never gradient-filled (contrast).

### 4.3 Shape, spacing, elevation
- 4-pt grid; screen gutters 16; card padding 16–20.
- Radii: cards 20, sheets 28 (top corners), buttons/chips full-pill, inputs 12.
- Elevation via tonal surface shifts, not heavy drop shadows.

### 4.4 Iconography
- Replace emoji with a single outlined icon set (Material Symbols Rounded). Category icons get custom glyphs (chest, back, legs, core, arms, shoulders, full-body) for a polished, consistent look.
- Emoji reserved for celebratory moments only (🎉 on completion, 🔥 on streak).

### 4.5 Motion
- Standard easing (M3 emphasized), 200–300 ms.
- Player card: spring physics on drag; on commit, card flies off in swipe direction while next card scales up from 0.94 → 1.
- Rest ring: continuous smooth sweep (not 1-second ticks).
- Respect "reduce motion": swap flies for cross-fades.

---

## 5. Screen-by-screen redesign

### 5.1 Today

```
┌─────────────────────────────────┐
│ Good evening, Alex          ⚙   │
│                                 │
│ ┌─ This week ────────────────┐  │
│ │ ● ● ● ○ ○ ○ ○   3/4 goal   │  │  weekly dots + streak 🔥 5
│ └────────────────────────────┘  │
│ ┌─ Up next ──────────────────┐  │
│ │ Pull Day                   │  │  rotates through plans by last-done
│ │ 6 exercises · ~40 min      │  │
│ │ Last: 3 days ago           │  │
│ │            [ ▶ Start ]     │  │
│ └────────────────────────────┘  │
│ Recent                     All →│
│ ┌ Push Day · Tue · 42m · 18 sets┐│  tap → session detail
│ └──────────────────────────────┘│
│                       ( ▶ )     │  FAB: start (opens plan sheet)
├─────────────────────────────────┤
│ Today  Plans  Progress  Library │
└─────────────────────────────────┘
```

- **Start sheet** (bottom sheet, replaces Select Plan modal): search, recent plans first, "Quick workout (no plan)" at top.
- Empty state: friendly illustration + two CTAs: "Use a starter plan" (ship 3 templates: Full Body Beginner, Push/Pull/Legs, Core 10) and "Build your own".

### 5.2 Workout player (the hero screen)

```
┌─────────────────────────────────┐
│ ✕   Push Day          12:34  ⋮  │  elapsed time; ⋮ = reorder/finish early
│ ▓▓▓▓▓▓▓░░░░░░░░░░░  5 / 18      │  segmented progress (one segment per set)
│                                 │
│ ┌─────────────────────────────┐ │
│ │ CHEST · SET 2 OF 3          │ │
│ │ Diamond Push-ups            │ │
│ │                             │ │
│ │     −      12      +        │ │  Display numerals, big tap targets
│ │           reps              │ │
│ │  Last time: 10 reps ▲2      │ │  ← progressive overload cue
│ │  ─────────────────────────  │ │
│ │  + Pike Push-ups   10 reps  │ │  superset partner(s) inline
│ └─────────────────────────────┘ │
│                                 │
│  Up next: Dips · 3×8            │
│                                 │
│  [ Skip ]      [ ✓ Done ]       │  primary = right thumb
│  ↑ Add exercise                 │
└─────────────────────────────────┘
```

Rules:
- **Buttons + gestures**: right swipe = Done, left = Skip, up = Add. Card tints green/amber as you drag past threshold, with a haptic "tick" at the threshold.
- **Undo** snackbar for 5 s after Done/Skip ("Set logged · Undo").
- **Edit actuals**: −/+ adjusts *what you actually did*; long-press the number for a numpad. Weight editable in 0.5/1/2.5 kg steps.
- **Duration exercises**: card becomes a timer — tap ▶ to start a 3-2-1 countdown, ring fills, auto-completes at 0 (with option to keep counting for "max hold").
- **Up next** row is tappable → opens a **queue sheet** to reorder (drag), remove, or jump to a set.
- **Finish early** from ⋮ saves a partial session (status `partial`), never silently discards.
- ✕ minimises to the mini-player (doesn't end the workout).

### 5.3 Rest

```
┌─────────────────────────────────┐
│            REST                 │
│         ╭───────────╮           │
│        │    0:42     │          │  smooth ring, rest colour
│         ╰───────────╯           │
│      [ −15s ]   [ +15s ]        │
│                                 │
│ Next: Diamond Push-ups · 12     │  preview of next card
│                                 │
│          [ Skip rest ]          │
└─────────────────────────────────┘
```

- **Auto-advance** to the next card at 0 (setting, default on), with sound + vibration + heads-up notification if the app is backgrounded.
- Live timer on the **lock screen / notification** (Android foreground service; "Live Activity" style).
- ±15 s steps (finer than ±30 s), long-press for ±60 s.
- Per-exercise default rest remembered from the last edit.

### 5.4 Workout complete

- Hero: 🎉, duration, sets, total reps, **volume** (Σ weight × reps), and a **PR list** ("New best: Pull-ups 12 reps").
- Optional 1–5 effort rating and note.
- Actions: **Done** · **Save as plan** (for ad-hoc) · **Update plan with today's numbers** (for planned workouts — progressive overload in one tap).
- Session is saved **once**, when the user taps Done (or on finish-early), not on render.

### 5.5 Plans

- List of plan cards: name, exercise count, estimated duration, last performed, muscle-group chips. Overflow menu: Start · Edit · Duplicate · Delete (with Undo).
- **Plan editor is a full screen**, not a modal:

```
┌─────────────────────────────────┐
│ ←  Push Day                Save │
│ ≡ Diamond Push-ups              │  drag handle = reorder
│   3 sets × 12 reps · rest 60s ▸ │  tap row → inline expand to edit
│ ≡ ┌ Superset ────────────────┐  │
│   │ Dips          3 × 8      │  │
│   │ Pike Push-ups 3 × 10     │  │
│   └ rest 90s ────────────────┘  │
│ ≡ Plank   3 × 45s · rest 30s  ▸ │
│                                 │
│ [ + Add exercises ]             │
│  Est. 38 min · 9 sets           │
└─────────────────────────────────┘
```

- **Add exercises** → full-screen picker (shared with Library and in-workout Add): search, category chips, multi-select with a sticky "Add 3" button. Selected items appear as editable rows with sensible defaults (from last use).
- Group/ungroup into superset via long-press multi-select → "Group as superset".
- Per-set targets optional ("3 × 12" or set-by-set: 12/10/8, or pyramid).

### 5.6 Library

- Search bar + filter chips (category, Reps/Time, Custom).
- Rows: category icon, name, type, "Last: 3×12 · 2 days ago".
- Exercise detail screen (not a modal): trend chart (best set over time), PRs, history list, notes/cues, Edit/Delete for custom; "Duplicate & edit" for presets.
- Deleting an exercise used by plans warns with the plan names and offers archive instead.

### 5.7 Progress

Tabs: **Overview · History · Exercises**.
- Overview: range segmented control (7D/30D/1Y/All); KPI tiles (workouts, active minutes, volume, streak) each with Δ vs previous period; weekly bar chart of workouts; muscle-group distribution; recent PRs.
- History: month **calendar heat-map** + list. Tap a session → **Session detail** (every set, actual vs target, notes) with "Repeat this workout" and Delete (Undo).
- Exercises: ranked list → exercise detail (same as Library).

### 5.8 Settings
- Units (kg/lb), default rest, auto-advance rest, sound, vibration, keep screen on, theme (system/dark/light), weekly goal.
- Data: Backup to file, Restore (with **merge or replace** choice and preview counts), Delete all (type-to-confirm).
- About / version.

---

## 6. Interaction & feedback patterns

| Situation | Old | New |
|---|---|---|
| Destructive action | `confirm()` dialog | Act immediately + Undo snackbar (except "Delete all") |
| Validation | `alert()` | Inline field error, disabled CTA |
| Naming a plan | `prompt()` | Bottom sheet with text field, smart default |
| Success | Toast | Snackbar; PRs get a celebratory banner + haptic |
| Haptics | Same buzz everywhere | Light tick (threshold), medium (set done), success pattern (workout done) |

---

## 7. Accessibility

- All actions have labelled buttons; gestures are optional. TalkBack custom actions on the player card ("Complete set", "Skip set", "Add exercise").
- Touch targets ≥ 48 dp; player buttons 64 dp.
- Contrast ≥ 4.5:1 for text, 3:1 for UI; no information conveyed by colour alone (Done/Skip also differ by icon and label).
- Dynamic type up to 200 % without truncating the player number (auto-shrink).
- Rest-end announced via TalkBack live region and haptic, not just sound.
- Reduce-motion respected.

---

## 8. Microcopy

- Verbs over nouns: "Start", "Log set", "Skip", "Add exercise" (not "Inject").
- Encouraging, brief: "Nice — 2 more reps than last time." / "Rest up. Next: Dips."
- Consistent terms: **Workout** (a session), **Plan** (a template), **Exercise**, **Set** (one round), **Superset** (exercises done together). Replace the ambiguous "exercise set".

---

## 9. Prioritised redesign backlog

| P | Item | Impact | Effort |
|---|---|---|---|
| P0 | Visible Done/Skip buttons + Undo | ★★★ | S |
| P0 | Duration-exercise countdown | ★★★ | S |
| P0 | Rest auto-advance + background notification | ★★★ | M |
| P0 | Persist active workout (survive restart) | ★★★ | S |
| P0 | Bottom nav + mini-player | ★★★ | M |
| P1 | Full-screen plan editor with reorder/edit + supersets | ★★★ | L |
| P1 | "Last time" cue + PR detection | ★★☆ | M |
| P1 | Session detail + calendar history | ★★☆ | M |
| P1 | New design tokens, icons, light theme | ★★☆ | M |
| P2 | Progress overview with deltas, volume, muscle split | ★★☆ | M |
| P2 | Starter templates + onboarding | ★☆☆ | S |
| P2 | Units, weekly goal, settings expansion | ★☆☆ | S |

---

## 10. Validation plan

1. Clickable prototype (Figma) of Today → Start → Player → Rest → Complete.
2. 5 hallway tests with gym-goers: measure time-to-first-logged-set, error rate (accidental skip), and whether users find History/Stats unaided.
3. Success metrics after launch: % sessions completed vs started, undo rate (signals mis-swipes), 4-week retention, plans edited (vs re-created).
