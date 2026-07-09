# Rekodi -- Project Overview

## Project Identity

**App name:** Rekodi (Swahili for "Record")

**Publisher:** Camel Creatives, Tanzania

**License:** MIT

**Platform:** Android, minSdk 26 (Android 8.0 Oreo), targetSdk 35, compileSdk 35.

**Tech stack:**
- **Language:** Kotlin 100% -- no Java source files exist in the project. The entire codebase, including build logic convention plugins, is written in Kotlin.
- **UI framework:** Jetpack Compose with Material 3 design system. The project uses the Compose BOM for aligned dependency versions.
- **Dependency injection:** Hilt (Dagger Hilt 2.53.1), integrated via the `hilt-android` Gradle plugin and KSP for annotation processing.
- **Asynchronous programming:** Kotlin Coroutines + Flow throughout. The project defines a custom `RekodiDispatchers` interface (in `core-common`) to make dispatchers injectable and testable. All ViewModels, repositories, and the recording service use `viewModelScope` or custom `CoroutineScope` instances with `SupervisorJob`.
- **Local persistence:** Room database for recording metadata (entity, DAO, database class) and Jetpack DataStore (Preferences) for user settings. Both are in `core-data`.
- **Image loading:** Coil (Compose integration) for thumbnail loading in the library/gallery feature.

## Architecture Philosophy

**Multi-module Gradle project:** The project is split into 9 modules (1 app module, 3 core modules, 5 feature modules, plus a build-logic included build) to enforce strict dependency boundaries, enable parallel Gradle builds, and improve compilation times. Each module has a single responsibility.

**Single-Activity architecture:** The app has exactly one `ComponentActivity` (`MainActivity`) that serves as the navigation host. All screens are Composables rendered inside `RekodiNavHost()` using Compose Navigation. This eliminates the complexity of multiple Activity lifecycles, makes state management predictable, and aligns with modern Android architecture guidelines.

**Unidirectional data flow (UDF):** Data flows in a single direction: Room DB through DAOs to `RecordingRepository`, then from the repository through ViewModels (via `StateFlow`) to Compose UI. User actions flow back down through callbacks or ViewModel method calls. The recording service exposes its state via `MutableStateFlow` which the UI observes.

**Crash-safe design:** `RekodiApplication.onCreate()` installs a global `Thread.setDefaultUncaughtExceptionHandler` via `RekodiCrashHandler`, which on any uncaught exception sends a crash-safe intent to `RecordingForegroundService` with `ACTION_CRASH_SAFEGUARD`, allowing the service to safely finalize any in-progress recording before the process dies. This is the cornerstone of the app's reliability guarantee.

**Convention plugins for consistent module configuration:** Rather than repeating SDK versions, Compose dependencies, and Java compatibility settings across every `build.gradle.kts`, the project defines five convention plugins in a `build-logic` included build. Every module applies the appropriate convention plugin and gets all standard configuration automatically.

## Module Dependency Graph

```
                     +------------------+
                     |       app        |
                     +--------+---------+
                              |
          +-------------------+-------------------+
          |                   |                   |
          v                   v                   v
  +-------+--------+  +------+-------+  +--------+-------+
  | feature-* (5)  |  |   core-data  |  |    core-ui     |
  +-------+--------+  +------+-------+  +--------+-------+
          |                   |                   |
          +-------------------+-------------------+
                              |
                              v
                     +--------+---------+
                     |   core-common    |
                     +------------------+
                              |
                     (no project dependencies)
```

Detailed explanation of every edge:

1. **`app` depends on:** `core-ui`, `core-data`, `core-common`, and ALL five feature modules (`feature-recorder`, `feature-editor`, `feature-library`, `feature-settings`, `feature-onboarding`). The app module is the assembly point: it stitches everything together by including all modules as implementation dependencies, sets up the Hilt DI graph root, and hosts the navigation graph.

2. **Each feature module** (`feature-*`) depends on `core-ui` and `core-common` automatically via the `camelcreatives.android.feature` convention plugin. They also individually depend on `core-data` when they need database access (all features do, except onboarding). These dependencies are declared explicitly in each feature module's `build.gradle.kts`. Feature modules never depend on other feature modules -- they are completely independent of each other.

3. **`core-data` depends on `core-common`** because it uses types like `RekodiDispatchers` and `RekodiResult` defined there.

4. **`core-ui` depends on `core-common`** for the same reason (shared utilities, extensions).

5. **`core-common` has zero project dependencies.** It is the leaf module. It depends only on `kotlinx-coroutines-core` (external library). This makes it the foundation of the entire module graph.

This strict layering means that changes to `core-common` trigger a rebuild of everything, but changes to a feature module only rebuild that module and the app module.

## Build System

**Gradle 8.7** with the **Kotlin 2.1.0** compiler plugin. The Gradle wrapper is configured in `gradle/wrapper/gradle-wrapper.properties` and uses the `gradle-8.7-bin.zip` distribution.

**Version catalog** at `gradle/libs.versions.toml` centralizes all dependency versions, library coordinates, and plugin references. The build-logic module has its own separate version catalog at `build-logic/gradle/libs.versions.toml` for the Gradle plugin artifacts (AGP, Kotlin Gradle Plugin, KSP, Hilt) that the convention plugins need at compile time.

**Convention plugins (5)** registered in `build-logic/build.gradle.kts` under `gradlePlugin { plugins { ... } }`:

| Plugin ID | Class | Purpose |
|---|---|---|
| `camelcreatives.android.application.compose` | `AndroidApplicationComposeConventionPlugin` | Applies `com.android.application`, `kotlin-android`, `kotlin-compose`; sets compileSdk/minSdk/targetSdk to 35/26/35; configures Java 17; adds all Compose and Activity/Lifecycle dependencies |
| `camelcreatives.android.library.compose` | `AndroidLibraryComposeConventionPlugin` | Same as above but for `com.android.library` modules (for `core-ui`) |
| `camelcreatives.android.library` | `AndroidLibraryConventionPlugin` | Basic Android library setup without Compose (for `core-common`) |
| `camelcreatives.android.feature` | `AndroidFeatureConventionPlugin` | Library + Compose + auto-dependency on `core-ui` and `core-common` + navigation-compose + hilt-navigation-compose |
| `camelcreatives.android.hilt` | `AndroidHiltConventionPlugin` | Applies `hilt-android` and `ksp` plugins; adds `hilt-android` dependency and `hilt-compiler` KSP processor |

**Root `build.gradle.kts`** declares all Gradle plugins with `apply false` so they resolve on the classpath but are only actually applied by convention plugins or individual modules.

**ProGuard/R8** is enabled in release builds via `isMinifyEnabled = true` and `isShrinkResources = true` in `app/build.gradle.kts`. Custom rules in `proguard-rules.pro` keep Hilt classes, Room entities, `RecordingState`, and `MediaProjection` classes from being stripped.

**GitHub Actions CI** at `.github/workflows/ci.yml` triggers on pushes and PRs to `main`. It runs on `ubuntu-latest` with JDK 17 (Temurin), executes `assembleDebug` and `testDebugUnitTest`.

## Key Libraries

Every library declared in the version catalog, with version and purpose:

| Library | Version | Purpose |
|---|---|---|
| **Android Gradle Plugin (AGP)** | 8.7.3 | Android build system -- compiles resources, generates R classes, manages APK packaging |
| **Kotlin Gradle Plugin** | 2.1.0 | Kotlin compilation, language features |
| **Kotlin Compose Compiler Plugin** | 2.1.0 | Compose compiler for Kotlin 2.0+ (replaces the old `composeOptions { kotlinCompilerExtensionVersion }` approach) |
| **KSP** | 2.1.0-1.0.29 | Kotlin Symbol Processing for annotation processing (used by Hilt and Room) |
| **Hilt** | 2.53.1 | Dependency injection framework |
| **Compose BOM** | 2024.12.01 | Bill of materials -- aligns all Compose library versions |
| **AndroidX Core KTX** | 1.15.0 | Core AndroidX extensions for Kotlin |
| **AndroidX Core SplashScreen** | 1.0.1 | Android 12+ splash screen API support |
| **Activity Compose** | 1.9.3 | `ComponentActivity` integration with Compose (`setContent`) |
| **Navigation Compose** | 2.8.5 | Type-safe navigation between Composables |
| **Lifecycle Runtime Compose** | 2.8.7 | Lifecycle-aware Compose APIs (`LifecycleEventEffect` etc.) |
| **Lifecycle ViewModel Compose** | 2.8.7 | `viewModel()` composable function, `collectAsStateWithLifecycle` |
| **Lifecycle Service** | 2.8.7 | Lifecycle-aware Android Services |
| **Coroutines Core** | 1.9.0 | Kotlin coroutine primitives |
| **Coroutines Android** | 1.9.0 | `Dispatchers.Main` on Android, structured concurrency |
| **Room Runtime** | 2.6.1 | SQLite abstraction layer |
| **Room KTX** | 2.6.1 | Coroutine/Flow extensions for Room |
| **Room Compiler** | 2.6.1 | Room annotation processor (KSP) |
| **DataStore Preferences** | 1.1.1 | Key-value preference storage with Flow support |
| **Coil Compose** | 2.7.0 | Image loading for Compose (thumbnails) |
| **Media3 ExoPlayer** | 1.5.1 | Video/audio playback engine |
| **Media3 UI** | 1.5.1 | Player UI components |
| **Hilt Navigation Compose** | 1.2.0 | Hilt integration with Compose Navigation (`hiltViewModel()`) |
| **JUnit** | 4.13.2 | Unit testing framework |
| **Turbine** | 1.2.0 | Flow testing library |

## Package Structure

```
src/main/java/com/camelcreatives/rekodi/

app/                                    # :app module
  RekodiApplication.kt                  # @HiltAndroidApp, global crash handler
  RekodiCrashHandler.kt                 # UncaughtExceptionHandler, safe-recording-finalize
  MainActivity.kt                       # Single Activity, Compose host
  di/
    AppModule.kt                        # Hilt @Module providing RekodiDispatchers
  service/
    MediaProjectionTrampolineActivity.kt # Transparent Activity for MediaProjection consent
  ui/
    navigation/
      RekodiNavHost.kt                  # Compose Navigation graph (Routes object + NavHost)

core/core-common/                       # :core-common module (no project deps)
  common/
    Dispatchers.kt                      # RekodiDispatchers interface + Default impl
    Extensions.kt                       # File output helpers, formatDuration, formatFileSize
    Result.kt                           # RekodiResult sealed class (Success/Error)

core/core-data/                         # :core-data module
  data/
    di/
      DataModule.kt                     # Hilt @Module: provides Room DB + DAO
    local/
      RekodiDatabase.kt                 # Room @Database (1 entity, version 1)
      dao/
        RecordingDao.kt                 # @Dao: CRUD + search queries (Flow-based)
      entity/
        RecordingEntity.kt              # @Entity: recordings table schema
    datastore/
      SettingsDataStore.kt              # Preferences DataStore: 20+ settings keys
    repository/
      RecordingRepository.kt            # @Singleton repository wrapping DAO

core/core-ui/                           # :core-ui module
  ui/
    theme/
      Color.kt                          # Brand color palette (Amber, Charcoal, Savannah Green...)
      Type.kt                           # RekodiTypography (Material 3 type scale)
      Theme.kt                          # RekodiTheme: dynamic color + light/dark schemes
    components/
      RekodiComponents.kt              # Shared Composables: RecordButton, RecordingTimer, RecordingCard

feature/feature-recorder/               # :feature-recorder module
  recorder/
    model/
      RecordingModels.kt                # Recording data class, RecordingState enum, RecorderConfig
    service/
      RecordingForegroundService.kt     # Foreground service: MediaProjection capture, MediaRecorder
    overlay/
      RecordingBubbleView.kt            # System overlay (WindowManager): floating control bubble
      ZoomOverlayView.kt                # System overlay: ripple + magnifier tap visualization
    accessibility/
      TapDetectionAccessibilityService.kt # AccessibilityService for tap detection
    ui/
      RecorderScreen.kt                 # Permission-granting screen composable
    di/
      RecorderModule.kt                 # Hilt @Module: provides WindowManager, ZoomOverlayView

feature/feature-editor/                 # :feature-editor module
  editor/
    model/
      AudioEditModels.kt                # AudioClip, WaveformPeaks, EditAction, UndoState
    engine/
      AudioEngine.kt                    # MediaCodec-based: extractWaveform, trimAudio, applyFade, mergeAudio
    ui/
      AudioEditorScreen.kt             # Editor UI: waveform view, transport, tool controls

feature/feature-library/                # :feature-library module
  library/
    ui/
      LibraryScreen.kt                  # Grid/list of recordings, search, filter, FAB
      RecordingDetailScreen.kt          # Metadata display, share/edit/delete actions

feature/feature-settings/               # :feature-settings module
  settings/
    SettingsScreen.kt                   # Settings UI: video/audio/bubble/appearance sections

feature/feature-onboarding/             # :feature-onboarding module
  onboarding/
    OnboardingScreen.kt                 # HorizontalPager carousel (4 pages), skip/get-started
```

## Feature Summary

**feature-recorder:** Core of the app. Manages the screen recording lifecycle via a foreground service using `MediaProjection` API + `MediaRecorder`. Renders a draggable floating control bubble via `WindowManager` with start/pause/stop controls and an elapsed timer. Provides an optional tap-detection overlay via an `AccessibilityService` that counts taps and renders animated ripples/magnifier effects. Exposes recording state as `StateFlow` for the UI to observe.

**feature-editor:** In-app audio editing suite. Uses `MediaExtractor` + `MediaCodec` + `MediaMuxer` to decode audio to PCM, compute waveform peaks, and perform non-destructive edits (trim, fade in/out, merge clips). Features a Compose `Canvas`-based waveform view with playhead and trim region overlays, transport controls, and tool-specific panels (Trim, Fade, Volume, Split, Merge).

**feature-library:** Gallery for browsing recordings. Displays a searchable, filterable list (all/video/audio/favorites) with `LazyColumn` and `Card` items showing filename, duration, file size, and tap count. Includes a `RecordingDetailScreen` with full metadata, share/edit/delete actions. Uses the `RecordingRepository` to observe the Room DB.

**feature-settings:** Full settings screen organized into sections (Video, Audio, Floating Bubble, Zoom & Tap, Storage, Appearance, About). Each setting corresponds to a key in `SettingsDataStore`. Currently uses local Compose state (not yet connected to DataStore).

**feature-onboarding:** First-run experience with a `HorizontalPager` carousel explaining the app's key features (welcome, floating bubble, audio editor, permissions). Users can skip or navigate through pages and tap "Get Started".

## Permissions Map

The `AndroidManifest.xml` declares these permissions:

| Permission | Reason | Notes |
|---|---|---|
| `FOREGROUND_SERVICE` | Required to run the recording engine as a foreground service | Android fundamental requirement |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Declares the foreground service type as `mediaProjection` | Mandatory on Android 14+ (API 34) |
| `POST_NOTIFICATIONS` | Show the persistent recording notification with timer and actions | Runtime-requested on Android 13+ |
| `SYSTEM_ALERT_WINDOW` | Display the floating control bubble overlay via `WindowManager` | Special permission via Settings deep link |
| `RECORD_AUDIO` | Capture microphone audio during recording | Runtime permission |
| `MEDIA_PROJECTION` | Screen capture consent via system dialog | Requested via `createScreenCaptureIntent()` |
| `ACCESSIBILITY_SERVICE` | Detect taps outside the app for tap counter and zoom visualization | Optional -- app works without it |
| `BIND_ACCESSIBILITY_SERVICE` | Bind the `TapDetectionAccessibilityService` | Required by the accessibility service manifest declaration |
| `READ_MEDIA_VIDEO` | Read video recordings from shared storage (API 33+) | Granular media permission |
| `READ_MEDIA_AUDIO` | Read audio recordings from shared storage (API 33+) | Granular media permission |
| `INTERNET` | Reserved for future features (crash reporting, license checking, etc.) | Declared but not currently used |

The `feature-recorder` module's `AndroidManifest.xml` also duplicates the first 6 permissions (necessary because the feature module's manifest is merged into the final manifest).

## Data Flow

The data flow follows strict unidirectional architecture:

**Persistence Layer (Room DB):**
- `RecordingEntity` defines the schema with fields: `id`, `filePath`, `fileName`, `mimeType`, `durationMs`, `fileSizeBytes`, `resolution`, `frameRate`, `bitrate`, `tapCount`, `isFavorite`, `tags`, `notes`, `createdAt`.
- `RecordingDao` exposes methods returning `Flow<List<RecordingEntity>>` for reactive observation (e.g., `getAllRecordings()`, `getVideoRecordings()`, `searchRecordings()`) and `suspend` functions for writes.

**Settings Layer (DataStore):**
- `SettingsDataStore` wraps Jetpack Preferences DataStore. It exposes a `settings: Flow<RekodiSettings>` and individual `suspend` update functions for each setting (20+ settings spanning video quality, audio config, bubble behavior, zoom config, storage, appearance).

**Repository Layer:**
- `RecordingRepository` is a `@Singleton` that wraps `RecordingDao`. It delegates all DAO calls and exists as an abstraction point for potential future caching, sync, or offline-first logic. It does not currently perform any business logic transformation.

**ViewModel Layer:**
- Although the project does not yet have ViewModels wired up to every screen (currently `LibraryScreen` holds recordings in local `remember` state rather than through a ViewModel), the `RecordingForegroundService` acts as a pseudo-ViewModel for the recording state. It exposes `recordingState: StateFlow<RecordingState>`, `elapsedSeconds: StateFlow<Long>`, and `tapCount: StateFlow<Int>` that the UI could observe via `collectAsState()`.

**UI Layer (Compose):**
- Navigable screens defined in `RekodiNavHost` observe `StateFlow` instances from ViewModels/services, render composables accordingly, and dispatch user actions through callbacks or service intents.

**Complete data flow for a recording:**
1. `RecordingForegroundService.startRecording()` creates a `MediaProjection` + `MediaRecorder`.
2. On stop, the service inserts a `RecordingEntity` into Room via `recordingRepository.insertRecording()`.
3. The `RecordingDao` emits the new entity through its `Flow`.
4. `LibraryScreen` (via a future ViewModel) collects the Flow and updates the UI.
5. User taps a recording -> navigates to `RecordingDetailScreen` -> displays metadata.
6. User taps "Edit Audio" -> navigates to `AudioEditorScreen` -> `AudioEngine` loads the file, decodes PCM, computes waveform, and renders the editor.

## Relationship Between REKODI_BUILD_SPEC.md and the Implementation

`REKODI_BUILD_SPEC.md` at the project root is the **original product specification document**. It describes the full vision for Rekodi as a premium screen recorder with floating bubble, tap visualization, built-in audio editor, and a polished Material 3 UX. It covers:

- Product overview and core pillars (reliability, floating bubble, zoom visualization, audio editor, proper local storage, premium design).
- Branding identity (colors: amber #E8A33D, charcoal #1C1B1F, savannah green #3E7C59; Swahili influences).
- Detailed feature specifications for screen recording, floating bubble, tap counter/zoom, audio editing suite, library/gallery, and settings.
- Technical architecture recommendations (multi-module, Hilt, Room, DataStore, MediaProjection, Compose Navigation).
- Permissions matrix with Android version notes and storage strategy.
- UI/UX guidelines and reliability/crash-prevention strategies.
- Screen list, build milestones, and open-source project setup.

The **current implementation** is an early-stage prototype that follows the spec closely:
- The module structure matches the spec exactly (app, core-ui, core-data, core-common, feature-recorder, feature-editor, feature-library, feature-settings, feature-onboarding, build-logic).
- The architecture decisions (Hilt, Room, DataStore, Compose Navigation, single-Activity) match the spec.
- The recording engine uses `MediaProjection` + `MediaRecorder` as specified.
- The floating bubble uses `WindowManager` + `TYPE_APPLICATION_OVERLAY` as specified.
- `TapDetectionAccessibilityService` implements the optional tap detection.
- `ZoomOverlayView` renders ripple animations.
- `AudioEngine` provides the core editing operations (trim, fade, merge) using `MediaCodec`.
- The `RecordingEntity` schema matches the spec's metadata requirements.
- `SettingsDataStore` covers all settings categories from the spec.

**Areas where the implementation is not yet complete (per the spec):**
- The audio editor lacks noise reduction, silence trim, undo/redo, and export options.
- The library does not yet have a ViewModel wired to the Room database.
- Settings screen uses local Compose state instead of being connected to `SettingsDataStore`.
- The zoom overlay only renders ripples, not the magnifier effect.
- Swahili localization (`Kiswahili`) is listed in settings but not implemented.
- Crash reporting and WorkManager (for post-processing) are not yet implemented.
- The project has no instrumentation tests yet.

In essence, `REKODI_BUILD_SPEC.md` serves as the **north star** for development. All implementation decisions are made in reference to it, and the gap between the spec and the current codebase defines the remaining development work.
