# Workout Tracker (PWA)

A mobile-first, offline-capable workout tracker built with vanilla **HTML/CSS/JS**. It lets you create exercises and workout plans, run workouts with **Tinder-like swipe cards**, timed rest intervals, inject new sets mid-session, and review history + statistics (with charts).

## Features

### Workout flow (cards + gestures)
- Swipe **right**: complete the current set
- Swipe **left**: skip the current set
- Swipe **up**: **inject** a new set (one or multiple exercises) into the current session
- Rest timer card with:
  - Countdown
  - Adjust time with **±30s**
  - Swipe right to end rest early
  - Optional notification when rest completes
- “Workout complete” card:
  - Swipe right to end the workout
  - Swipe up to inject more exercises and continue

### Plans & exercises
- Create exercises:
  - Type 1: reps-based
  - Type 2: duration-based
  - Optional weight
  - Category (chest, back, legs, core, arms, shoulders, full-body)
- Create workout plans with “sets” that can contain **multiple exercises** (performed sequentially on the same card)
- Save, edit, delete, duplicate plans
- Preset calisthenics/street-workout exercises included (50+)

### Injection UX improvements
- Searchable, categorized exercise picker when:
  - Adding exercise sets to a plan
  - Injecting exercises during a workout
- Selection persists while searching (doesn’t lose checked items)
- Configure **per-exercise** reps/duration/weight when selecting multiple exercises

### Stats & history
- Workout history stored locally (permanent unless deleted)
- Statistics page with:
  - Date filters (Last Week, Last 30 Days, Last Year, All Time)
  - Charts (Chart.js)
  - Exercise breakdown metrics (sets, reps/duration, maxes, total weight when applicable)

### Offline & PWA
- Uses **IndexedDB** for offline-first storage (exercises, plans, sessions)
- Installable as a PWA (manifest + standalone display)
- Works without network after first load (assuming your static files are cached/served locally)

## Tech stack
- Vanilla HTML / CSS / JavaScript
- IndexedDB for persistence
- Chart.js for charts
- Optional device integrations (when supported by browser):
  - Vibration API (haptics)
  - Notifications (rest complete)
  - Wake Lock (prevent screen sleep during workout)

## Getting started

### 1) Run locally (quick test)
Just open the `Workout Tracker.html` file in Chrome to test basic functionality.

> For **PWA installability**, you typically need to serve it over `http://localhost` or `https://` (not `file://`), and provide a real `manifest.json` + icons.

### 2) Run with a simple local server (recommended)
Use any static server, for example:
- `python -m http.server 5173`
- `npx serve`

Then open:
- `http://localhost:5173/Workout%20Tracker.html`

### 3) Install on Android (Chrome)
- Open the app URL in Chrome
- Menu → **Install app** (or **Add to Home screen**)
- If it installs correctly, it should launch in **standalone** mode (no browser UI)

## Data storage
All data is stored locally in the browser via IndexedDB:
- `exercises`
- `workoutPlans`
- `workoutSessions`
- `settings`

The Settings page includes:
- Export data (JSON)
- Import data (JSON)
- Delete all data (with confirmation)

## App navigation & back button behavior (PWA/mobile)
- Back button should close open “cards” (modals) first
- When a workout is ongoing:
  - Back should go to Home (workout remains resumable)
  - Home shows an “Active workout” card to resume
- Starting a new workout while one is active prompts to terminate the current workout first

## Notes / known limitations
- True “background timers” and guaranteed notifications vary by browser and OS power-management rules.
- For best PWA install behavior on Chrome Android:
  - Use a real `manifest.json` file
  - Provide PNG icons (192x192 and 512x512)
  - Serve over HTTPS or localhost
- If your charts don’t render, verify Chart.js is loading and there are sessions in history.

## License
Personal/internal project by default. Add a license if you plan to distribute publicly.
