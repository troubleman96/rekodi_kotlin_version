# feature-settings Module -- Settings

## Module Purpose

The feature-settings module provides a comprehensive settings screen organized by category. It allows users to configure every aspect of the Rekodi application, from video and audio recording parameters to floating bubble behavior, zoom visualization style, storage management, appearance, and language. The module implements the UI layer only; persistence is handled by `SettingsDataStore` in the `:core:core-data` module.

## Architecture Overview

The module consists of a single file containing the main settings screen composable and three reusable helper composables:

- **SettingsScreen** -- The main scrollable settings UI organized into categories
- **SettingsSection** -- A card wrapper with a section title
- **SettingsDropdown** -- An ExposedDropdownMenuBox for selection from options
- **SettingsToggle** -- A labeled Switch toggle

Dependencies: This module depends on `:core:core-ui` for theme colors and Material3 components.

---

### ui/SettingsScreen.kt

**Package:** `com.camelcreatives.rekodi.settings`

**`SettingsScreen()` (lines 43-124):**

The main settings composable. Parameters:
- `onNavigateBack: () -> Unit` -- Back navigation callback.
- `modifier: Modifier` -- Standard Compose modifier.

The screen is a Scaffold with a TopAppBar titled "Settings" and a back arrow. The content is a vertically scrollable Column containing seven `SettingsSection` components, each grouping related settings.

**Category Breakdown:**

#### 1. Video Settings Section (lines 70-77)

Controls for screen recording video parameters:

- **Resolution** (dropdown): "Auto", "720p", "1080p", "Native". Default: "Auto". Determines the capture resolution.
- **Frame Rate** (dropdown): "30", "60". Default: "30". Higher FPS produces smoother video but larger files.
- **Bitrate** (dropdown): "Low", "Medium", "High", "Custom". Default: "Medium". Higher bitrate means better quality but larger files.
- **Orientation** (dropdown): "Auto", "Portrait", "Landscape". Default: "Auto". Locks screen orientation during recording.
- **Countdown Timer** (toggle): Default: enabled. Shows a 3-2-1 countdown before recording starts.
- **Stop on Lock Screen** (toggle): Default: disabled. Automatically stops recording when the device is locked.

#### 2. Audio Settings Section (lines 79-84)

Controls for audio recording:

- **Source** (dropdown): "Mute", "Microphone", "Internal", "Mic+Internal". Default: "Mic+Internal". Selects the audio input source.
- **Sample Rate** (dropdown): "44100", "48000". Default: "44100". CD-quality vs. higher quality.
- **Channels** (dropdown): "Mono", "Stereo". Default: "Stereo". Mono saves space, stereo preserves spatial audio.
- **Noise Suppression** (toggle): Default: disabled. Would apply noise filtering to the audio track.

#### 3. Floating Bubble Section (lines 86-89)

Controls for the recording overlay bubble:

- **Enable Bubble** (toggle): Default: enabled. Shows or hides the floating control bubble during recording.
- **Hide During Recording** (toggle): Default: disabled. Auto-hides the bubble while recording is active.

#### 4. Zoom & Tap Section (lines 91-95)

Controls for the tap visualization overlay:

- **Enable Zoom Effect** (toggle): Default: disabled. Shows ripple animations on screen taps.
- **Style** (dropdown): "Ripple", "Ripple+Magnifier", "Off". Default: "Ripple". Visual style of tap feedback.
- **Tap Counter** (toggle): Default: enabled. Shows a tap count badge on the bubble.

#### 5. Storage Section (lines 97-99)

- **Auto-delete after 30 days** (toggle): Default: disabled. Automatically removes recordings older than 30 days.

#### 6. Appearance Section (lines 101-105)

Controls for visual theming:

- **Theme** (dropdown): "System", "Light", "Dark". Default: "System". Follows device theme or forces light/dark.
- **Dynamic Color (Material You)** (toggle): Default: enabled. On Android 12+, uses system wallpaper-based colors.
- **Language** (dropdown): "English", "Kiswahili". Default: "English". App UI language selection.

#### 7. About Section (lines 107-119)

Static informational section:

- **Version**: "Rekodi v1.0.0" displayed in bodyMedium style.
- **Credit**: "Made by Camel Creatives, Tanzania" displayed below the version.

**`SettingsSection()` (lines 126-148):**

A reusable composable that groups related settings together:

1. Renders the section title as a `Text` composable in `titleMedium` style with bold weight, with top padding of 16dp and bottom padding of 8dp.
2. Wraps the content in a `Card` with `fillMaxWidth()`, 12dp rounded corners, and `surfaceVariant` background color.
3. The card's content has 16dp internal padding.

This gives each section a distinct visual grouping with a labeled header.

**`SettingsDropdown()` (lines 150-199):**

A reusable composable implementing Material3's `ExposedDropdownMenuBox` pattern:

Parameters:
- `label: String` -- The setting name shown as a label.
- `options: List<String>` -- The available options for selection.
- `defaultOption: String` -- The initially selected value.

State:
- `expanded: Boolean` -- Controls dropdown visibility.
- `selected: String` -- The currently selected option.

Layout:
- A Row with the label text on the left (weight 1f) and the dropdown on the right.
- The dropdown uses `ExposedDropdownMenuBox` wrapping an `OutlinedTextField` with:
  - `readOnly = true` (user cannot type, only select)
  - `singleLine = true`
  - A trailing icon rendered by `ExposedDropdownMenuDefaults.TrailingIcon`
  - `menuAnchor()` modifier to anchor the popup
  - bodyMedium text style
- The `ExposedDropdownMenu` contains `DropdownMenuItem` for each option. Selecting an option updates `selected`, closes the dropdown, and would need to persist the value via SettingsDataStore.

**`SettingsToggle()` (lines 201-224):**

A reusable composable implementing a labeled Switch:

Parameters:
- `label: String` -- The setting name.
- `defaultChecked: Boolean` -- The initial switch state.

State:
- `checked: Boolean` -- Managed via `mutableStateOf`.

Layout:
- A Row with the label text on the left (weight 1f) and a `Switch` on the right.
- Vertical padding of 6dp for spacing between toggles.

---

## Data Persistence

The settings UI is purely presentational. Actual persistence is handled by `SettingsDataStore` in `:core:core-data`, which uses Jetpack DataStore (Preferences) to store all settings as key-value pairs. Each setting in the UI corresponds to a method on SettingsDataStore:

- `updateVideoResolution()`, `updateVideoFps()`, `updateVideoBitrate()`, `updateOrientationLock()`
- `updateCountdownEnabled()`, `updateStopOnLockScreen()`
- `updateAudioSource()`, `updateSampleRate()`, `updateAudioChannels()`, `updateNoiseSuppression()`
- `updateBubbleEnabled()`, `updateBubbleOpacity()`, `updateBubbleHideRecording()`
- `updateZoomEnabled()`, `updateZoomStyle()`, `updateZoomColor()`, `updateZoomSensitivity()`
- `updateAutoDeleteDays()`, `updateStorageMaxCapMb()`
- `updateDarkMode()`, `updateDynamicColor()`, `updateLanguage()`
- `updateTapCountEnabled()`

The `RekodiSettings` data class bundles all settings with sensible defaults. The `settings` Flow on SettingsDataStore provides reactive observation.

---

## Integration Points

1. **Navigation:** The settings screen is reached from the LibraryScreen's settings icon. Route: `Routes.SETTINGS`.

2. **Recorder Configuration:** The Video and Audio settings directly map to `RecorderConfig` fields in `feature-recorder`. When wired, changing a dropdown would call the corresponding DataStore update method, and the recorder would observe the settings flow.

3. **Theme:** The Appearance settings (dark mode, dynamic color) feed into `RekodiTheme` in `:core:core-ui`, which reads the settings to choose between light/dark color schemes.

## Known Issues and Future Work

- No settings values are persisted -- the dropdowns and toggles maintain local state but do not call SettingsDataStore.
- The dropdown's "Custom" bitrate option has no corresponding sub-settings for custom value entry.
- The Language dropdown has no actual localization implementation. The strings.xml currently only has English.
- "Internal" audio source (internal device audio capture) requires additional platform APIs beyond standard MediaRecorder.
- The About section should ideally include links to open source licenses, privacy policy, and GitHub repository.
- The Auto-delete feature has no background worker (e.g., WorkManager task) to perform periodic cleanup.
