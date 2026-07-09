# Rekodi — Full Build Specification

**Tagline:** *Rekodi Kila Kitu.* ("Record Everything.")
**Publisher:** Camel Creatives
**Origin:** Tanzania 🇹🇿
**License:** Open Source (MIT recommended — see §11)
**Platform:** Android, native Kotlin + Jetpack Compose
**Category:** Screen recorder / audio recorder / audio editor, premium-grade UX

This document is a complete engineering brief. It is written so it can be fed directly into Claude Code, handed to a dev team, or used as a living README/architecture doc for the repo.

---

## 1. Product Overview

Rekodi is a free, open-source Android app for recording your screen and audio, with a floating always-on-top control bubble, tap-visualization ("zoom"), a click counter, and a built-in sound/audio editor — trim, split, merge, fade, and clean up recordings without leaving the app. The bar is a **premium, polished, crash-free experience** comparable to paid recorders (AZ Screen Recorder, Mobizen), but fully open source and Tanzania-built.

### Core pillars
1. **Reliability first** — never crash, never silently drop a recording.
2. **Floating control bubble** — draggable overlay with start/pause/stop, elapsed timer, and a live tap counter.
3. **Zoom/tap visualization** — optional ripple + magnifier effect at every touch point, great for tutorials.
4. **Built-in audio editor** — trim, split, merge, fade in/out, volume normalize, noise reduction, waveform view.
5. **Proper local storage** — scoped storage / MediaStore compliant, works on Android 8 through 15 without a single permission crash.
6. **Premium design** — Material 3 expressive theming, smooth motion, dark mode, custom brand identity.

---

## 2. Branding & Identity

| Element | Spec |
|---|---|
| App name | **Rekodi** |
| Meaning | Swahili for "Record" |
| Publisher line | "by Camel Creatives" shown on splash screen & About screen |
| Primary color | Deep amber/gold (`#E8A33D`) evoking Tanzanian sunsets — pair with a deep charcoal (`#1C1B1F`) for dark mode and off-white (`#FAF7F2`) for light mode |
| Accent | Savannah green (`#3E7C59`) for record-active states |
| Logo concept | Abstract circular "record dot" merged with a Maasai-shield-inspired ring; simple enough to read at 24dp (status bar) up to 512dp (Play Store) |
| Typography | Google Sans / Inter for UI; a distinct display font for the splash wordmark |
| App icon | Adaptive icon: amber record-dot on charcoal background, subtle shield ring |
| Voice/tone | Confident, friendly, minimal — copy in English with Swahili touches (e.g. "Anza Kurekodi" as an alt label on the record button, toggleable in settings) |

---

## 3. Feature Specification (Detailed)

### 3.1 Screen Recording
- Uses `MediaProjection` API + `MediaRecorder` (or `MediaCodec` for finer control over bitrate/fps).
- Configurable: resolution (Auto/720p/1080p/native), frame rate (30/60fps), bitrate (Low/Medium/High/Custom Mbps), orientation lock.
- Audio source options: **Mute**, **Microphone**, **Internal audio** (Android 10+ `AudioPlaybackCapture`, with mic fallback messaging for older OS), **Mic + Internal mixed**.
- Countdown before recording starts (3-2-1, skippable, configurable in settings).
- Pause/Resume support (Android 7+ native pause where available; else stop-and-stitch fallback documented for pre-N... but since minSdk will be 26+, native pause/resume is safe to assume).
- Auto-stop on: storage full, call interruption (optional pause-on-call setting), user-defined max duration.
- Recording indicator: persistent notification (foreground service) + floating bubble timer.

### 3.2 Floating Control Bubble
- Implemented via `WindowManager` + `TYPE_APPLICATION_OVERLAY`, requires `SYSTEM_ALERT_WINDOW` permission (Settings deep-link flow, see §5).
- Draggable anywhere on screen, snaps to nearest edge on release (with a subtle spring animation).
- States:
  - **Idle**: shows Rekodi logo mark, tap to expand mini control panel (Record / Settings / Close).
  - **Recording**: shrinks to a compact pill showing elapsed time (mm:ss) and a pulsing red dot.
  - **Tap counter badge**: small numeric badge on the bubble showing count of screen taps detected since recording started (see 3.3). Resets each new recording session; visible live, saved into recording metadata afterward (e.g. "482 taps" shown in the recording's detail screen — useful for reviewers/analytics on tutorial content).
  - Long-press bubble → quick actions radial menu (Pause, Stop, Toggle Zoom Effect, Screenshot).
- Bubble opacity reduces to ~40% when idle for 3+ seconds, restores to 100% on touch (configurable).
- Bubble position and last-used settings persist across app restarts (DataStore, see §4).

### 3.3 Tap Counter & Zoom/Tap Visualization
- Uses an `AccessibilityService` (with clear user-facing rationale + easy opt-out) OR a system-wide touch overlay approach to detect taps outside the app for visualization purposes — **be explicit in onboarding about why this permission is requested, and make it fully optional**: if declined, tap counting/zoom simply doesn't render, recording still works normally.
- **Tap counter**: increments an in-memory counter each detected tap during an active recording session; displayed live on the bubble badge; persisted as metadata attached to the finished recording.
- **Zoom/tap visualization ("zoom effect")**: on each tap, draws a short-lived animated ripple + optional magnifying circle centered on the touch point, rendered in an overlay window so it's visible in the recorded screen capture. Configurable:
  - Style: Ripple only / Ripple + Magnifier / Off
  - Color and size of ripple
  - Magnifier zoom level (1.5x–3x) and duration (300–800ms)
- All visualization rendering must be lightweight (single overlay `View`/Compose overlay, hardware-accelerated) to avoid frame drops in the actual screen recording.

### 3.4 Sound/Audio Editing Suite
A dedicated in-app editor for any recording (or externally imported audio/video's audio track):
- **Waveform view**: renders the waveform for fast, accurate scrubbing (down-sampled peaks for performance on long files).
- **Trim/Cut**: drag handles on the waveform, live preview.
- **Split**: split at playhead into two clips.
- **Merge**: combine multiple clips in a chosen order into one file.
- **Fade in / Fade out**: adjustable duration.
- **Volume normalize / gain adjustment.**
- **Noise reduction**: basic spectral noise gate (simple, on-device, no cloud dependency — keep expectations realistic, this isn't studio-grade).
- **Silence trim**: auto-detect and remove long silences (with a sensitivity slider).
- **Export options**: format (M4A/AAC, MP3 via a bundled open encoder, WAV), quality/bitrate.
- All edits are **non-destructive until export** — original recording is never overwritten unless the user explicitly chooses "Save over original."
- Undo/redo stack for the current editing session.

### 3.5 Library / Gallery
- Grid/list of all recordings (video + audio), thumbnail preview, duration, size, date, and tap-count badge for recordings made with the zoom feature on.
- Search, sort (date/size/duration/name), filter (video only/audio only/favorites).
- Batch actions: delete, share, move to folder.
- Rename, favorite/star, add tags/notes.
- Tap to open: video → built-in player with trim shortcut; audio → waveform editor.
- Share sheet integration (standard Android share intent — WhatsApp, etc., popular in Tanzania, should "just work").

### 3.6 Settings
- Video: resolution, fps, bitrate, orientation lock, countdown timer, stop-on-lock-screen toggle.
- Audio: source, sample rate, mono/stereo, noise suppression toggle.
- Bubble: enable/disable, opacity behavior, position reset, hide-during-recording option.
- Zoom/Tap effect: on/off, style, color, sensitivity.
- Storage: choose save location (internal app-scoped folder by default, or a user-chosen SAF folder), auto-delete after X days (optional), max storage cap warning.
- Appearance: Light / Dark / System, dynamic color (Material You) toggle, language (English / Swahili).
- About: version, licenses (auto-generated OSS license list), "Made by Camel Creatives, Tanzania 🇹🇿", link to GitHub repo.

---

## 4. Technical Architecture

### 4.1 Stack
| Layer | Choice |
|---|---|
| Language | Kotlin (100%) |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Local DB | Room (recording metadata, tags, tap-counts) |
| Preferences | Jetpack DataStore (Proto or Preferences) — bubble position, settings |
| Background work | Foreground Service (recording engine) + WorkManager (post-processing/export jobs, cleanup) |
| Screen capture | `MediaProjection` + `MediaRecorder`/`MediaCodec` |
| Audio editing engine | FFmpeg-Kit (LGPL build, self-hosted binaries to stay open-source-license-clean) or a pure-Kotlin PCM pipeline for lighter ops (trim/fade/merge) with FFmpeg-Kit reserved for encode/transcode |
| Waveform rendering | Custom Compose `Canvas` drawing from down-sampled PCM peaks (precomputed on a background thread) |
| Image loading | Coil |
| Navigation | Compose Navigation (single-Activity architecture) |
| Testing | JUnit5 + Turbine (Flow testing) + Compose UI testing + Espresso for the overlay/service integration |
| Crash reporting | Optional, privacy-respecting, opt-in only (e.g. self-hosted or none at all, given open-source/privacy positioning) — **do not bundle Firebase/Crashlytics by default** if the project wants to stay a "clean" FOSS app; document this as a config flag instead |

### 4.2 Module structure (multi-module Gradle setup recommended for build speed & separation)
```
rekodi/
├── app/                      # Application module, DI graph root, navigation host
├── core/
│   ├── core-ui/              # Design system: theme, typography, shared composables
│   ├── core-data/            # Room DB, DataStore, repositories, models
│   ├── core-common/          # Utils, dispatchers, result wrappers, extensions
├── feature/
│   ├── feature-recorder/      # MediaProjection service, recording engine, bubble
│   ├── feature-editor/        # Audio editing UI + engine bindings
│   ├── feature-library/       # Gallery/list screens
│   ├── feature-settings/      # Settings screens
│   ├── feature-onboarding/     # Permission flow, first-run tutorial
├── build-logic/               # Convention plugins for consistent module config
```

### 4.3 Recording Engine Flow (high level)
1. User taps Record (from app or bubble) → request `MediaProjection` consent via `Activity.startActivityForResult` (must originate from an Activity context, even when triggered from the bubble — use a transparent trampoline Activity).
2. On grant → start `RecordingForegroundService` with `ACTION_START`, pass the projection token via a same-process singleton (not Intent extras, since `MediaProjection` isn't Parcelable-safe across process boundaries) or by immediately creating the projection inside the service using the result data.
3. Service creates `VirtualDisplay` from `MediaProjection`, feeds `MediaRecorder`/`MediaCodec` surface.
4. Foreground notification shows live timer + Stop/Pause actions.
5. Bubble (separate overlay window, same service) reflects state via a shared `StateFlow`.
6. On stop → finalize file, write to app-scoped storage or MediaStore, insert Room entry with metadata (duration, resolution, tap-count, size), release `VirtualDisplay`/`MediaProjection`.
7. Any exception during capture → caught, recording safely finalized/truncated rather than lost, user notified — **never let a capture error crash the whole app.**

---

## 5. Permissions & Storage — Detailed Matrix

Rekodi must work cleanly from **API 26 (Android 8) through the latest release** with zero permission-related crashes. Handle every permission with a clear rationale screen *before* the system dialog (never cold-request).

| Permission | Why | Android version notes |
|---|---|---|
| `FOREGROUND_SERVICE` | Run recording engine as a foreground service | All versions |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Required foreground service type for screen capture | Android 14+ (API 34) mandatory — declare `android:foregroundServiceType="mediaProjection"` |
| `POST_NOTIFICATIONS` | Show the persistent recording notification | Runtime-requested on Android 13+ (API 33+); below that it's normal/auto-granted |
| `SYSTEM_ALERT_WINDOW` | Floating bubble overlay | Special permission via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` deep link — not a runtime dialog; must guide user to Settings and detect grant on `onResume` |
| `RECORD_AUDIO` | Microphone recording | Runtime-requested, all versions |
| `WRITE_EXTERNAL_STORAGE` / `READ_MEDIA_*` | Save/access recordings | **Do NOT request `WRITE_EXTERNAL_STORAGE` on API 29+** (scoped storage active); use `MediaStore` API instead. On API 33+, request granular `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` only if reading files the app didn't create itself (e.g. importing external audio for editing) |
| `BIND_ACCESSIBILITY_SERVICE` | Tap detection for counter/zoom effect (optional feature) | User must manually enable in system Accessibility settings; deep-link there with a clear explanation screen; app must function fully with this declined |
| `SCHEDULE_EXACT_ALARM` | Only if implementing a scheduled/auto-record feature (optional/future) | Android 12+ |

### Storage strategy
- **Default**: save into the app's own scoped storage location, exposed to the user via `MediaStore.Video`/`MediaStore.Audio` inserts with `RELATIVE_PATH = "Movies/Rekodi"` / `"Music/Rekodi"` (or `Recordings/Rekodi` where the OEM supports it) — this makes files visible in the system Gallery/Files app and in "My Files" without needing broad storage permissions, and it survives app uninstall (by design, since these are user media).
- **Optional**: let power users pick a custom folder via Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) and persist the URI permission (`takePersistableUriPermission`) for future writes.
- Never write raw files to `Environment.getExternalStorageDirectory()` (deprecated/blocked on modern Android) — this is the #1 cause of storage-related crashes in recorder apps and must be explicitly avoided.
- All file I/O wrapped in try/catch with graceful degradation (e.g. if MediaStore insert fails, fall back to app-private storage under `context.getExternalFilesDir()` and inform the user, rather than crashing).

### Permission-request UX
1. Onboarding carousel explains *why* each permission is needed (plain language, Swahili option).
2. Each permission requested **just-in-time**, not all up front — e.g. only ask for overlay permission when the user first enables the floating bubble.
3. Every optional permission (Accessibility for tap/zoom) has a visible in-app toggle to enable/disable later, with the app remaining fully functional either way.
4. Denied-permission states handled gracefully with an explanatory inline banner + "Open Settings" button — never a silent failure or crash.

---

## 6. UI/UX — Premium Design Guidelines

- **Material 3 Expressive** components throughout; enable dynamic color (Material You) on Android 12+, with the amber/charcoal brand palette as the fallback theme on older versions or when dynamic color is off.
- Motion: use `Compose` animation APIs (`animateFloatAsState`, `AnimatedVisibility`, shared-element transitions between Library → Player/Editor) — everything should feel springy but restrained, not gimmicky.
- The record button itself should have a distinct, satisfying press animation (subtle scale + haptic feedback via `HapticFeedbackConstants`).
- Empty states (no recordings yet) should be illustrated, warm, and encouraging rather than a blank screen.
- Dark mode is a first-class citizen, not an afterthought — design dark first given recorder apps are often used in low-light/screen-focused contexts.
- Respect system font scaling and TalkBack accessibility labels on every interactive element (especially critical since the app already touches Accessibility APIs — it should be a model citizen here).
- Splash screen: Android 12+ `SplashScreen` API with the Rekodi wordmark animating in, "by Camel Creatives" fading in beneath.

---

## 7. Reliability & Crash Prevention

This is non-negotiable given the "no crashes" requirement. Concrete measures:

1. **Global uncaught exception handler** (`Thread.setDefaultUncaughtExceptionHandler`) that safely finalizes any in-progress recording (writes whatever has been captured so far to disk) before the process dies, so a crash never means losing the user's footage.
2. **Service resilience**: `RecordingForegroundService` uses `START_REDELIVER_INTENT`; on unexpected service death, attempt to recover state from a small persisted "recording in progress" flag and notify the user rather than silently losing data.
3. **Strict null-safety and sealed-class `Result` wrappers** for all I/O and MediaProjection operations — no unguarded platform calls in the recording path.
4. **Extensive lifecycle testing** across configuration changes (rotation, split-screen, foldables) since screen recording state must survive Activity recreation cleanly (state lives in the Service, UI just observes it).
5. **Battery optimization exemption prompt** (optional, clearly explained) to prevent OEM background-kill from ending recordings on Android skins with aggressive battery managers (common in the region — this genuinely improves reliability).
6. **Automated tests**: unit tests for the editing engine (trim/merge/fade math), instrumentation tests for the recording service lifecycle, and manual QA checklist covering at minimum: low storage, call interruption mid-recording, screen rotation mid-recording, app backgrounded mid-recording, permission revoked mid-use.
7. **ProGuard/R8** rules tuned and tested in release builds specifically (a common source of "works in debug, crashes in release" bugs) — ship a release-build smoke test as part of CI.
8. **Graceful feature degradation**: if MediaProjection, Accessibility, or overlay permission is unavailable/denied, the relevant feature disables itself with a clear message instead of throwing.

---

## 8. Suggested Screen List

1. Splash
2. Onboarding / Permission walkthrough
3. Home (Library) — with the "quick record" FAB and bubble-enable toggle
4. Recording overlay/bubble (system window, not a normal screen)
5. Video Player (playback + trim shortcut)
6. Audio Editor (waveform, trim/split/merge/fade)
7. Recording Detail (metadata: duration, size, tap-count, date, share/delete/rename)
8. Settings (with sub-screens: Video, Audio, Bubble & Zoom, Storage, Appearance, About)
9. About / Open Source Licenses / Camel Creatives credit

---

## 9. Suggested Build Milestones

| Milestone | Deliverable |
|---|---|
| M1 | Project scaffold, module structure, design system (colors/type/theme), navigation shell |
| M2 | Core screen recording (start/stop/pause) via foreground service, saved to MediaStore correctly |
| M3 | Floating bubble (drag, snap, expand/collapse, recording timer) |
| M4 | Tap counter + zoom/tap visualization overlay (Accessibility-based, fully optional) |
| M5 | Library screen, Room DB, recording metadata, player |
| M6 | Audio editor (waveform, trim/split/merge/fade/export) |
| M7 | Settings, storage location picker (SAF), Swahili localization |
| M8 | Reliability pass: crash-handler, low-storage/interruption testing, ProGuard-tested release build |
| M9 | Polish pass: motion, empty states, icon/splash, About screen, OSS license generation |
| M10 | Open-source release prep: README, CONTRIBUTING, CI, license headers, Play Store + F-Droid listing assets |

---

## 10. Open Source Project Setup

- **License**: MIT is simplest for maximizing reuse; GPLv3 if you want derivative apps to stay open too. (Note: if bundling FFmpeg-Kit's LGPL build, ensure dynamic linking / compliant distribution — document this clearly in `NOTICE.md`.)
- **Repo structure essentials**:
  - `README.md` — what it is, screenshots, features, build instructions, Camel Creatives credit, license badge.
  - `CONTRIBUTING.md` — how to set up the project locally, code style (ktlint/detekt config), PR process.
  - `CODE_OF_CONDUCT.md`
  - `LICENSE`
  - `.github/workflows/ci.yml` — build + lint + unit tests on every PR (GitHub Actions, e.g. `gradle build`, `ktlintCheck`, `detekt`, `test`).
  - `docs/ARCHITECTURE.md` — a trimmed version of this spec for contributors.
- Consider listing on **F-Droid** in addition to the Play Store, since it's open-source and privacy-respecting (no default analytics/crash SDK) — F-Droid has specific build reproducibility requirements worth planning for from day one (no proprietary dependencies, reproducible Gradle build).

---

## 11. Notes / Open Questions to Resolve Before Coding

- Confirm minSdk target — **API 26 (Android 8.0)** recommended as the floor for a clean `MediaProjection`/foreground-service story; confirm this matches your target user base in Tanzania (older/budget devices may run lower — worth checking analytics if you have any install base already).
- Decide MP3 encoding approach — Android's native `MediaCodec`/`MediaRecorder` doesn't produce MP3 directly; you'll want AAC/M4A as the native default and only add MP3 export via a bundled open encoder (e.g. LAME via FFmpeg-Kit) if it's a hard requirement, since it adds APK size.
- Decide the exact scope of "zoom" (tap-visualization, confirmed assumption above) vs. any pinch-to-zoom video editing feature, which would be a separate, larger effort (frame-accurate zoom keyframing in the video editor).
- Decide analytics/telemetry stance explicitly (recommended: none by default, or a fully opt-in, self-hosted, privacy-respecting option) — this is both a technical and a brand-positioning decision given the "open source" framing.

---

*End of specification. This document is intended to be handed to Claude Code, or any Android engineering team, as a build-ready brief for Rekodi by Camel Creatives.*
