# Rekodi Architecture

## Module Structure

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

## Stack

- **Language:** Kotlin 100%
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **Async:** Kotlin Coroutines + Flow
- **Database:** Room
- **Preferences:** DataStore
- **Screen Capture:** MediaProjection + MediaRecorder
- **Audio Engine:** Pure Kotlin PCM pipeline
- **Navigation:** Compose Navigation (single-Activity)

## Recording Flow

1. User taps Record -> MediaProjection consent via trampoline Activity
2. On grant -> RecordingForegroundService starts with the projection token
3. Service creates VirtualDisplay, feeds MediaRecorder surface
4. Foreground notification shows live timer + actions
5. Floating bubble reflects state via shared StateFlow
6. On stop -> finalize file, write to MediaStore, insert Room entry
