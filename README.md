# Rekodi — Record Everything

**Rekodi Kila Kitu**

Rekodi is a free, open-source Android app for recording your screen and audio, with a floating always-on-top control bubble, tap-visualization ("zoom"), a click counter, and a built-in sound/audio editor.

**Publisher:** Camel Creatives  
**Origin:** Tanzania  
**License:** MIT

## Features

- **Screen Recording** — High-quality screen capture using MediaProjection API
- **Floating Control Bubble** — Draggable overlay with start/pause/stop, timer, and tap counter
- **Tap Counter & Zoom** — Optional tap visualization with ripple + magnifier effects
- **Audio Editor** — Trim, split, merge, fade, normalize, and noise reduction with waveform view
- **Library/Gallery** — Grid/list view with search, sort, filter, favorites, and batch actions
- **Material 3 Design** — Dynamic color support, dark mode, premium UX

## Build Requirements

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 35
- Gradle 8.7+

## Building

```bash
git clone https://github.com/troubleman96/rekodi_kotlin_version.git
cd rekodi_kotlin_version
./gradlew assembleDebug
```

## Architecture

Multi-module Gradle project:

```
rekodi/
├── app/                    # Application module, DI, navigation
├── core/
│   ├── core-ui/            # Design system, theme, components
│   ├── core-data/          # Room DB, DataStore, repositories
│   └── core-common/        # Utils, dispatchers, extensions
├── feature/
│   ├── feature-recorder/   # Screen recording, bubble, tap detection
│   ├── feature-editor/     # Audio editing UI and engine
│   ├── feature-library/    # Gallery and recording details
│   ├── feature-settings/   # Settings screens
│   └── feature-onboarding/ # First-run experience
└── build-logic/            # Convention plugins
```

## Permissions

- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PROJECTION` — Recording engine
- `POST_NOTIFICATIONS` — Recording notification (Android 13+)
- `SYSTEM_ALERT_WINDOW` — Floating bubble overlay
- `RECORD_AUDIO` — Microphone capture
- `BIND_ACCESSIBILITY_SERVICE` — Tap detection (optional)

## License

MIT License — see [LICENSE](LICENSE)

---

*Made by Camel Creatives, Tanzania*
