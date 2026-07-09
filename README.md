# Rekodi — Record Everything (No Ads, No Subscriptions)

**Rekodi Kila Kitu**

Rekodi is a premium, open-source Android application designed for high-performance screen recording and professional audio editing. Unlike other recording tools that clutter the experience with intrusive ads, paywalls, or "pro" subscriptions, Rekodi is built with a **clean-first philosophy**: providing powerful tools for creators, developers, and workers for free.

**Publisher:** Camel Creatives  
**Origin:** Tanzania 🇹🇿  
**License:** MIT

---

## 🚀 The Mission: "Tools for Workers, Not for Profit"

Most utility tools on the Play Store are currently broken by aggressive monetization. Rekodi exists to prove that a professional-grade utility can be:
1. **Ad-Free**: No banners, no interstitials, no "watch this video to export."
2. **Privacy-Focused**: Everything stays on your device. We don't track your recordings.
3. **Powerful**: High-bitrate recording, accessibility-driven tap detection, and a non-destructive audio editor.

---

## ✨ Key Features

### 📹 Advanced Screen Recording
- **High-Fidelity Capture**: Supports up to 1080p (or native), 60FPS, and 16Mbps bitrates.
- **MediaProjection Engine**: Uses the latest Android 15 (API 35) standards for stable background recording.
- **Audio Versatility**: Record Microphone audio, Internal System audio, or a mix of both.

### 🫧 Floating Control Bubble
- **Draggable Overlay**: A persistent, unobtrusive bubble that sits on top of any app.
- **Interactive States**: Changes visual style during countdown, active recording, and paused states.
- **Tap Tracking**: Displays a real-time "Tap Counter" directly on the bubble, perfect for creating tutorials.
- **Drift Protection**: Snaps to the nearest edge and fades when idle to avoid blocking your view.

### 🖱️ Tap Visualization & Accessibility
- **Visual Feedback**: Real-time ripple effects and optional magnification at the point of contact.
- **Accessibility Service**: Uses Android's Accessibility framework to detect interactions globally, allowing for precise tap counting without requiring root access.

### ✂️ Built-in Audio Editor
- **Non-Destructive Editing**: Trim, split, and merge your audio files without losing quality.
- **Waveform Visualization**: Precise seek-and-cut using a high-performance Canvas-based waveform view.
- **Audio Processing**: Apply Fade-In/Fade-Out, Volume Normalization, and basic Noise Suppression.

### 📁 Intelligent Library
- **Automatic Organization**: Smart filtering by video, audio, or favorites.
- **Work Session Notes**: Attach notes and tags to recordings to document what was happening during a specific task.
- **Secure Sharing**: Uses Android FileProvider to share recordings directly to Slack, WhatsApp, or Google Drive without exposing your entire file system.

---

## 🛠️ Technical Architecture

Rekodi is built using modern Android development best practices:

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Asynchronous Work**: Kotlin Coroutines & Flow
- **Dependency Injection**: Hilt (Dagger)
- **Local Storage**: Room (Database) & Preferences DataStore (Settings)
- **Design Pattern**: MVVM with Clean Architecture modules
- **Build System**: Multi-module Gradle with Build Logic Convention Plugins

### Project Structure:
- `:app` — Entry point, Hilt modules, and Main Navigation.
- `:feature:feature-recorder` — The core recording engine, Foreground Service, and Overlay Bubble.
- `:feature:feature-editor` — Audio processing logic and the Waveform UI.
- `:feature:feature-library` — Gallery management and detail views.
- `:core:core-data` — Source of truth for all recording metadata and user preferences.
- `:core:core-ui` — The "Rekodi Design System" (Theme, Amber/Gold color palette, and reusable components).

---

## 🛠️ Build & Installation

### Prerequisites
- Android Studio Ladybug (2024.2.1) or higher.
- JDK 17.
- Android device running API 26 (Oreo) or higher.

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/troubleman96/rekodi_kotlin_version.git
   ```
2. Open in Android Studio.
3. Sync Gradle.
4. Run on your device.

---

## 📋 Permissions Explained

- **RECORD_AUDIO**: Required to capture your voice or system sounds.
- **SYSTEM_ALERT_WINDOW**: Required to display the floating control bubble over other apps.
- **FOREGROUND_SERVICE_MEDIA_PROJECTION**: Ensures Android doesn't kill the recording process when you switch apps.
- **POST_NOTIFICATIONS**: Required for Android 13+ to show recording controls in the notification shade.
- **BIND_ACCESSIBILITY_SERVICE**: (Optional) Used solely to detect taps and calculate the tap counter.

---

## 🤝 Contributing

We welcome contributions from everyone! Whether it's fixing a bug, adding a Kiswahili translation, or improving the audio processing engine.

1. Fork the project.
2. Create your feature branch.
3. Commit your changes.
4. Open a Pull Request.

---

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*Made with ❤️ by Camel Creatives, Tanzania*
