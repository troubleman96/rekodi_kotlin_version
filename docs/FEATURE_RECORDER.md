# feature-recorder Module -- Screen Recording Engine

## Module Purpose

The feature-recorder module is the core of the Rekodi application. It implements a full-featured screen recording engine using Android's MediaProjection API, a draggable floating control bubble overlay for in-app recording control, tap detection via the Android AccessibilityService for counting user interactions during recordings, a zoom/ripple visualization overlay for tap feedback, and a permission-granting screen. This module is the reason the app exists -- its entire purpose is to capture the device screen along with audio, save it as an MP4 file, and provide a polished user experience around that core capability.

## Architecture Overview

The module is structured into several sub-packages, each with a single responsibility:

- **model/** -- Data classes and enums that define recording state and configuration
- **service/** -- The Android foreground service that manages the actual recording lifecycle
- **overlay/** -- Two overlay views: a draggable control bubble (RecordingBubbleView) and a transparent tap visualization layer (ZoomOverlayView)
- **accessibility/** -- An AccessibilityService that detects taps across the system for counting
- **di/** -- A Hilt module providing WindowManager and ZoomOverlayView singletons
- **ui/** -- The Compose permission-granting screen
- **res/** -- Layout XML, drawables, and resources for the overlay views

Dependencies: This module depends on `:core:core-data` for RecordingRepository, `:core:core-common` for utility types (RekodiResult, RekodiDispatchers, Extensions), and `:core:core-ui` for theme colors and shared components.

## Files

---

### model/RecordingModels.kt

**Package:** `com.camelcreatives.rekodi.recorder.model`

This file defines three types that form the foundation of the recording system's data layer.

**`Recording` data class (lines 5-23):**

This is the UI-layer model for a recording. It mirrors `RecordingEntity` from `:core:core-data` but lives in the feature module to avoid coupling the feature to Room annotations. Fields include:

- `id: Long` -- Primary key, matches Room entity. Defaults to 0 for new recordings.
- `filePath: String` -- Absolute file path on the filesystem where the MP4 is stored.
- `fileName: String` -- The display name (e.g. "Rekodi_20260101_120000.mp4").
- `mimeType: String` -- Defaults to "video/mp4" since all recordings are MP4 containers with H.264 video and AAC audio.
- `durationMs: Long` -- Duration in milliseconds, populated after recording stops.
- `fileSizeBytes: Long` -- Size in bytes, populated after recording stops.
- `resolution: String` -- A human-readable string like "1920x1080".
- `frameRate: Int` -- Frames per second. Defaults to 30.
- `bitrate: Int` -- Video encoding bitrate in bits per second.
- `tapCount: Int` -- Number of screen taps detected during recording.
- `isFavorite: Boolean` -- Whether the user has favorited this recording.
- `tags: String` -- Optional user-assigned tags, stored as a comma-separated string.
- `notes: String` -- Optional user notes.
- `createdAt: Long` -- Epoch milliseconds timestamp of creation.

The class also defines two computed properties:
- `uri: Uri` -- Lazily converts `filePath` to a `Uri` via `Uri.parse()`. This is used when sharing the recording via Android's share sheet.
- `durationSeconds: Long` -- A convenience getter that divides `durationMs` by 1000.

**Design decision:** The Recording is deliberately kept separate from RecordingEntity. This allows the UI layer to add computed properties (uri, durationSeconds) and to evolve independently of the persistence layer. The mapping between the two happens in the repository or ViewModel layer.

**`RecordingState` enum (lines 25-31):**

This enum represents the five states of the recording lifecycle:

- `IDLE` -- No recording is active. The service is either stopped or not yet started.
- `COUNTDOWN` -- A countdown is running before recording begins. Not currently wired to actual countdown logic in the service, but defined for future use.
- `RECORDING` -- Actively capturing screen and audio. MediaRecorder is in the started state.
- `PAUSED` -- Recording is paused. MediaRecorder.pause() has been called (API 24+). The VirtualDisplay and MediaProjection remain active.
- `STOPPING` -- Recording is in the process of finalizing. Not currently used but defined for potential future use where the service needs a brief teardown state.

This state drives the UI in the floating bubble and the notification.

**`RecorderConfig` data class (lines 33-41):**

Holds user-configurable recording parameters that map to the settings the user chooses in the Settings screen:

- `resolution: String` -- One of "Auto", "720p", "1080p", "Native". Default: "Auto".
- `fps: Int` -- Frames per second. Default: 30.
- `bitrate: String` -- One of "Low", "Medium", "High", "Custom". Default: "Medium".
- `orientationLock: String` -- One of "Auto", "Portrait", "Landscape". Default: "Auto".
- `audioSource: String` -- One of "Mute", "Microphone", "Internal", "Mic+Internal". Default: "Mic+Internal".
- `sampleRate: Int` -- Audio sample rate in Hz. Default: 44100.
- `audioChannels: String` -- "Mono" or "Stereo". Default: "Stereo".
- `countdownEnabled: Boolean` -- Whether to show a 3-2-1 countdown before recording starts. Default: true.

This config is currently defined but the hardcoded values in `RecordingForegroundService.startRecording()` (1080p, 30fps, 8Mbps) take precedence. A future enhancement would read from RecorderConfig to make these settings actually configurable.

---

### service/RecordingForegroundService.kt

**Package:** `com.camelcreatives.rekodi.recorder.service`

This is the heart of the recording engine. It is an Android foreground service annotated with `@AndroidEntryPoint` for Hilt injection. The service runs in the foreground with a persistent notification so the system does not kill it during recording. It uses `START_REDELIVER_INTENT` so if the system kills and recreates the service, it redelivers the last intent (important for crash recovery).

**Dependency Injection (line 41):**

```kotlin
@Inject lateinit var recordingRepository: RecordingRepository
```

The RecordingRepository is injected by Hilt and provides access to the Room database for persisting recording metadata.

**StateFlows (lines 43-49):**

Three `MutableStateFlow` instances expose observable state to the rest of the app:
- `_recordingState` / `recordingState` -- The current RecordingState (IDLE, RECORDING, PAUSED, etc.)
- `_elapsedSeconds` / `elapsedSeconds` -- Elapsed recording time in seconds, incremented every second by a coroutine timer
- `_tapCount` / `tapCount` -- The number of screen taps detected, updated by the accessibility service

These are exposed as read-only `StateFlow` via `.asStateFlow()`. The service is started via `startForegroundService()` which means it runs even if the app is in the background.

**Service Instance Variables (lines 51-55):**

- `mediaProjection: MediaProjection?` -- The MediaProjection token granted by the user. This is the gateway to screen capture.
- `mediaRecorder: MediaRecorder?` -- The MediaRecorder instance that does the actual encoding.
- `virtualDisplay: VirtualDisplay?` -- A virtual display that routes screen frames to the MediaRecorder's surface.
- `outputFile: File?` -- The destination file for the MP4 output.
- `recordingId: Long` -- The Room database ID of the recording entity, used to update metadata when recording stops.

**Companion Object Constants (lines 57-69):**

Action strings used in intents sent to the service:
- `ACTION_START` -- Begin recording. Carries `EXTRA_RESULT_CODE` and `EXTRA_RESULT_DATA` (the MediaProjection consent token).
- `ACTION_STOP` -- Stop recording.
- `ACTION_PAUSE` -- Pause recording (API 24+).
- `ACTION_RESUME` -- Resume a paused recording.
- `ACTION_UPDATE_TAP_COUNT` -- Update the tap counter from the accessibility service.
- `ACTION_CRASH_SAFEGUARD` -- Emergency stop called from the crash handler.

Constants:
- `NOTIFICATION_CHANNEL_ID` = "rekodi_recording"
- `NOTIFICATION_ID` = 1001

**`onCreate()` (lines 72-75):**

Calls `createNotificationChannel()` to set up the low-importance notification channel for the recording notification. IMPORTANCE_LOW means the notification does not make sound but still appears in the status bar.

**`onStartCommand()` (lines 77-97):**

Dispatches on the intent's action. Returns `START_REDELIVER_INTENT` which is critical for foreground services -- if the service is killed by the system, the last intent will be redelivered, which helps with crash recovery. The switch handles all six action types:

- `ACTION_START`: Extracts resultCode and data from the intent extras, validates they are not -1/null, then calls `startRecording()`.
- `ACTION_STOP`: Calls `stopRecording()`.
- `ACTION_PAUSE`: Calls `pauseRecording()`.
- `ACTION_RESUME`: Calls `resumeRecording()`.
- `ACTION_UPDATE_TAP_COUNT`: Extracts the new count and updates `_tapCount`, then calls `updateNotification()` to refresh the notification UI.
- `ACTION_CRASH_SAFEGUARD`: Calls `safeguardRecording()` for emergency teardown.

**`onDestroy()` (lines 101-104):**

Calls `releaseResources()` to ensure all native resources (MediaRecorder, VirtualDisplay, MediaProjection) are released when the service is destroyed.

**`createNotificationChannel()` (lines 106-117):**

Standard Android notification channel creation. Creates a channel with:
- ID: "rekodi_recording"
- Name: "Rekodi Recording"
- Importance: IMPORTANCE_LOW (no sound, appears in the shade)
- Description: "Notification for active screen recording"
- `setShowBadge(false)` -- Prevents showing a badge icon

**`startRecording()` (lines 119-167) -- The Core Recording Logic:**

Step-by-step breakdown:

1. **Get MediaProjection:** Gets the `MediaProjectionManager` system service and calls `getMediaProjection(resultCode, data)`. The resultCode and data come from `MediaProjectionTrampolineActivity` which called `createScreenCaptureIntent()` and obtained user consent.

2. **Create Output File:** Generates a timestamped filename like "Rekodi_20260101_120000.mp4" and creates the file in `getExternalFilesDir(null)` -- the app's private external storage. This avoids needing MANAGE_EXTERNAL_STORAGE or WRITE_EXTERNAL_STORAGE permissions.

3. **Configure MediaRecorder:** Uses the builder pattern to set:
   - Audio source: `MediaRecorder.AudioSource.MIC` -- Currently hardcoded to microphone. Future: make configurable.
   - Video source: `MediaRecorder.VideoSource.SURFACE` -- Captures from the VirtualDisplay surface.
   - Output format: `MediaRecorder.OutputFormat.MPEG_4` -- Standard MP4 container.
   - Video encoder: `MediaRecorder.VideoEncoder.H264` -- Widely compatible, hardware-accelerated on most devices.
   - Audio encoder: `MediaRecorder.AudioEncoder.AAC` -- Standard audio codec for MP4.
   - Video bitrate: 8,000,000 bps (8 Mbps) -- Good quality at 1080p.
   - Frame rate: 30 fps.
   - Video size: 1920x1080 (1080p).
   - Output file: The absolute path of the output file.
   - Calls `prepare()`.

4. **Create VirtualDisplay:** Calls `mediaProjection?.createVirtualDisplay()` with:
   - Name: "RekodiDisplay"
   - Width: 1920, Height: 1080
   - Density: 320 dpi
   - Flags: `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR` -- Mirrors the main display content.
   - Surface: The surface obtained from MediaRecorder.

5. **Start Recording:** Calls `mediaRecorder?.start()`, sets state to `RECORDING`, calls `startForeground()` with the notification, and calls `startTimer()` to begin the elapsed-time coroutine.

6. **Insert Room Entity:** Launches a coroutine on `serviceScope` (IO dispatcher) that creates a `RecordingEntity` with the available metadata (filePath, fileName, mimeType, frameRate, bitrate, resolution) and inserts it via `recordingRepository.insertRecording()`, saving the returned `recordingId`. This allows the entity to be updated later with duration, file size, and tap count when recording stops.

**`stopRecording()` (lines 169-204) -- Clean Teardown:**

1. **Stop MediaRecorder:** Calls `stop()` then `release()` inside a try-catch to handle any exceptions gracefully.
2. **Release Resources:** Sets `mediaRecorder`, `virtualDisplay`, and `mediaProjection` to null after releasing.
3. **Update State:** Sets `_recordingState` to IDLE and resets `_elapsedSeconds` to 0.
4. **Update Room Entity:** If `recordingId > 0`, fetches the entity from the database, creates a copy with the final `durationMs` (elapsedSeconds * 1000), `fileSizeBytes` (from `outputFile.length()`), and `tapCount`, then updates the database.
5. **Stop Foreground:** Calls `stopForeground(STOP_FOREGROUND_REMOVE)` to remove the notification, then `stopSelf()` to terminate the service.

**`pauseRecording()` / `resumeRecording()` (lines 206-220):**

These use `MediaRecorder.pause()` and `MediaRecorder.resume()` which are available from API 24+. They update the state and notification. The try-catch handles devices where pause/resume may not be fully supported.

**`safeguardRecording()` (lines 222-233):**

Called from `RekodiCrashHandler` when the app crashes. Attempts to stop and release the MediaRecorder, release the VirtualDisplay, and set state back to IDLE. This prevents resources from leaking if the app crashes mid-recording. It does NOT attempt to update the database entity since the crash handler runs during an uncaught exception where coroutine launches may not execute reliably.

**`releaseResources()` (lines 235-242):**

Idempotent cleanup that releases MediaRecorder, VirtualDisplay, and MediaProjection, nulling each reference. Used in `onDestroy()` as a safety net.

**`startTimer()` (lines 244-252):**

A coroutine launched on `serviceScope` that loops while the state is `RECORDING`, waiting 1000ms per iteration, incrementing `_elapsedSeconds`, and calling `updateNotification()`. The loop condition `_recordingState.value == RecordingState.RECORDING` naturally stops the timer when recording ends.

**`buildNotification()` (lines 254-286):**

Constructs the ongoing notification that appears during recording:
- Title: "Recording"
- Content: Shows elapsed time and tap count (e.g. "01:30 - 5 taps")
- Small icon: `android.R.drawable.ic_menu_camera` (a standard Android icon)
- Ongoing: true (cannot be swiped away)
- Silent: true (no sound)
- Two action buttons:
  - Pause/Resume: Toggles between pause and resume based on current state, using standard media icons
  - Stop: Stops the recording

Each action creates a `PendingIntent` targeting the service itself with the appropriate action constant, using `FLAG_IMMUTABLE` for security (required on API 31+).

**`updateNotification()` (lines 288-291):**

Gets the NotificationManager and calls `notify()` with the updated notification, refreshing the timer and tap count display.

---

### overlay/RecordingBubbleView.kt

**Package:** `com.camelcreatives.rekodi.recorder.overlay`

This class manages a floating overlay view that appears on top of all apps (courtesy of `TYPE_APPLICATION_OVERLAY`). It provides a compact control interface for the recording service. The view has two modes: a mini panel (48dp tall horizontal bar showing state text) and an expanded panel (popup with timer and control buttons).

**Constructor (lines 26-28):**

Takes `Context` and `WindowManager`. WindowManager is provided by Hilt via `RecorderModule`. Both are stored as private properties.

**Instance Variables (lines 30-42):**

- `bubbleView: ViewGroup?` -- The root FrameLayout inflated from `bubble_layout.xml`.
- `miniPanel: View?` -- The mini panel LinearLayout (id: `mini_panel`).
- `expandedPanel: View?` -- The expanded panel LinearLayout (id: `expanded_panel`).
- `idleTimer: Handler?` -- Handler for scheduling idle opacity reduction.
- `isIdle: Boolean` -- True when the bubble has been untouched for 3 seconds.
- `initialX, initialY` -- Window position during drag.
- `initialTouchX, initialTouchY` -- Touch coordinates for drag delta calculation.
- `stateCallback` -- Lambda invoked when the user presses Record/Pause/Stop.
- `tapCountText: TextView?` -- Reference to the tap count badge.

**`show()` (lines 44-77):**

1. **Guard:** Returns immediately if `bubbleView` is already showing.
2. **Inflate Layout:** Uses `LayoutInflater.from(context)` to inflate `R.layout.bubble_layout`.
3. **Find Views:** Gets references to `mini_panel`, `expanded_panel`, and `tap_count`.
4. **Update State:** Calls `updateState(state)` to set initial UI state.
5. **Create Window Params:** Creates `WindowManager.LayoutParams`:
   - Width/Height: WRAP_CONTENT
   - Type: `TYPE_APPLICATION_OVERLAY` (API 26+) or `TYPE_PHONE` (legacy)
   - Flags: `FLAG_NOT_FOCUSABLE` (so touches pass through to the app behind) and `FLAG_LAYOUT_NO_LIMITS` (allows positioning outside the screen bounds for snapping)
   - Format: `PixelFormat.TRANSLUCENT`
   - Gravity: TOP | START
   - Position: x=0, y=200 (below status bar)
6. **Add to WindowManager:** Calls `windowManager.addView()`.
7. **Setup Listeners:** Calls `setupTouchListener()` for drag handling and `setupClickListeners()` for button interactions.
8. **Start Idle Timer:** Begins the 3-second countdown to reduce opacity.

**`updateState()` (lines 79-102):**

Updates the mini panel text based on RecordingState:
- IDLE: Shows "Rekodi", then calls `showExpandedPanel()` to auto-open the expanded panel for new bubble display.
- RECORDING: Shows "- Recording" with a unicode bullet character for the red dot effect.
- PAUSED: Shows a pause symbol icon followed by "Paused".

In all states, the mini panel is VISIBLE and expanded panel is GONE (except IDLE which triggers the expanded panel auto-show).

**`updateTimerText()` (lines 104-106):**

Updates the `timer_text` TextView in the expanded panel.

**`updateTapCount()` (lines 108-111):**

Updates the `tap_count` badge. Shows the count as a string if > 0, otherwise clears and hides the badge. The badge uses `badge_background.xml` (red oval) for the visual indicator.

**`setStateCallback()` (lines 113-115):**

Allows external code (like a ViewModel or Activity) to register a callback that fires when the user presses record/pause/stop. The callback receives the desired RecordingState.

**`setupTouchListener()` (lines 117-149):**

Implements the drag behavior for the bubble. Uses `setOnTouchListener` on the root view:

- `ACTION_DOWN`: Records initial window position and touch coordinates, resets idle timer, sets alpha to 1.0 (user hover brings it back to full opacity), returns true to consume the event.
- `ACTION_MOVE`: Calculates delta from initial touch position and updates the window layout params accordingly. The bubble follows the finger.
- `ACTION_UP`: 
  1. Calls `snapToEdge()` to animate the bubble to the nearest screen edge.
  2. Restarts the idle timer.
  3. If the total drag distance was less than 10px in both axes, treats it as a tap and calls `performClick()` to trigger the mini panel's onClick.

**`setupClickListeners()` (lines 151-182):**

Sets up onClick listeners for all interactive elements:

- **Mini panel click:** Toggles expanded panel visibility. If idle, does nothing (the bubble is semi-transparent and should not respond to taps).
- **Record button (`btn_record`):** Invokes the state callback with RECORDING state, then sends `ACTION_START` intent to `RecordingForegroundService`.
- **Stop button (`btn_stop`):** Sends `ACTION_STOP` intent.
- **Pause button (`btn_pause`):** Sends `ACTION_PAUSE` intent.
- **Close button (`btn_close`):** Calls `hide()` to remove the bubble from the WindowManager.

Note: The record button directly starts the foreground service. This is the entry point for the recording to begin. In practice, the trampoline activity handles the MediaProjection consent before the service receives ACTION_START.

**`snapToEdge()` (lines 184-196):**

Uses `ValueAnimator` to smoothly slide the bubble to the nearest screen edge. The decision is based on whether the bubble's current X position is less than half the screen width:

- If left of center, snap to x=0 (left edge).
- If right of center, snap to x = screenWidth - bubbleWidth (right edge).

The animation runs over 200ms. This creates a pleasant "magnetic" effect where the bubble clings to the side of the screen, a common pattern in floating overlay UIs. The Y position is preserved -- only horizontal snapping is applied.

**`startIdleTimer()` / `resetIdleTimer()` (lines 198-214):**

The idle timer uses a Handler on the main looper with a 3000ms delay:
- After 3 seconds of no touch interaction, `isIdle` is set to true and an `ObjectAnimator` fades the bubble's alpha to 0.4 over 500ms.
- Any touch interaction (via `ACTION_DOWN` or the reset method) cancels the pending callback, sets `isIdle` to false, and restores the alpha to 1.0.
- This prevents the bubble from being too distracting during recording while remaining visible enough to find.

**`showExpandedPanel()` (lines 216-224):**

Called when state changes to IDLE. Shows the expanded panel and auto-hides it after 5 seconds via `postDelayed`. This is used when the bubble first appears to let the user see the available controls before auto-hiding.

**`hide()` (lines 226-230):**

Removes all callbacks from the idle timer handler and removes the view from the WindowManager. Nulls the `bubbleView` reference so `show()` can be called again.

---

### overlay/ZoomOverlayView.kt

**Package:** `com.camelcreatives.rekodi.recorder.overlay`

This class manages a full-screen transparent overlay that draws visual tap feedback (ripple animations) at the location of detected taps. It is a purely visual layer -- it does not intercept touches (FLAG_NOT_TOUCHABLE).

**Outer Class (`ZoomOverlayView`, lines 18-60):**

The public-facing class that manages the lifecycle of the overlay:

- `overlayView: ZoomDrawView?` -- The custom View that does the drawing.
- `isShowing: Boolean` -- Prevents double-show.
- `rippleColor: Long` -- Default amber color (0xFFE8A33D), the Rekodi brand color.

Methods:
- `show()`: Creates a `ZoomDrawView`, sets its ripple color, builds `WindowManager.LayoutParams` with:
  - MATCH_PARENT width and height (full screen)
  - TYPE_APPLICATION_OVERLAY for system alert overlay
  - FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE (touch-through)
  - PixelFormat.TRANSPARENT
  Then adds the view to the WindowManager.
- `hide()`: Removes the view and nulls references.
- `setRippleColor(colorHex)`: Parses a hex color string (e.g. "#E8A33D") and passes it to the draw view.

**Inner Class (`ZoomDrawView`, lines 62-138):**

A custom View that overrides `onDraw()` to render tap ripple animations:

- **Paints (lines 63-74):**
  - `ripplePaint`: ANTIALIAS_FLAG, STROKE style, 4px stroke width. Used for the ripple outline.
  - `fillPaint`: ANTIALIAS_FLAG, FILL style. Used for the inner fill of the ripple circle.
  - `magnifierPaint`: STROKE, 3px, white. Intended for a magnifier circle (not currently drawn -- the canvas code draws two concentric ripple circles instead, effectively serving as both ripple and outer boundary).

- **RippleData (lines 78-82):** A private data class holding:
  - `x, y`: Position of the tap (float coordinates).
  - `radius`: Current radius, starts at 10f, animates to `maxRadius` (120f).
  - `alpha`: Current alpha, starts at 0.6f and decreases as radius increases.
  - `maxRadius`: Constant 120f.

- **`setRippleColor(color)` (lines 85-89):** Sets the ripplePaint to the color at 150 alpha, fillPaint at 60 alpha, and invalidates.

- **`onTouchEvent()` (lines 91-96):** If the view received a touch event (ACTION_DOWN), adds a ripple. This is a secondary mechanism; the primary tap source is the accessibility service calling `simulateTap()`.

- **`simulateTap(x, y)` (lines 98-100):** Public method called from `TapDetectionAccessibilityService` when a system-wide tap is detected. Calls `addRipple()`.

- **`addRipple(x, y)` (lines 102-106):** Creates a new RippleData at the given coordinates, adds it to the list, and starts its animation.

- **`animateRipple(ripple)` (lines 108-121):** Uses `ValueAnimator` to animate the ripple from radius 10f to maxRadius (120f) over 500ms. The update listener updates the ripple's radius and alpha (linearly interpolated from 0.6 to 0.0 based on progress) and calls `invalidate()`. After 600ms, the ripple is removed from the list to avoid unbounded growth.

- **`onDraw(canvas)` (lines 123-132):** Iterates through all active ripples and draws:
  1. A filled circle (fillPaint) at the current alpha.
  2. A stroked circle (ripplePaint) at the current alpha.
  3. A second stroked circle at 1.5x the radius (creates a double-ring effect).
  The alpha values for paint are recalculated each frame based on the ripple's current alpha.

- **`onDetachedFromWindow()` (lines 134-137):** Removes all handler callbacks to prevent leaks when the view is removed.

**Design decision:** The inner class pattern keeps the drawing logic encapsulated. The ripple animation uses ValueAnimator rather than Compose animations because this is a View-based overlay (required for WindowManager overlays, which predate Compose). The double-circle approach creates a more visible tap effect without needing a magnifier implementation.

---

### accessibility/TapDetectionAccessibilityService.kt

**Package:** `com.camelcreatives.rekodi.recorder.accessibility`

This is an Android AccessibilityService that runs in the background and detects taps across the entire system. It is entirely optional -- the app works without it. It is annotated with `@AndroidEntryPoint` for Hilt injection and its implementation is deliberately minimal to avoid privacy concerns.

**Service Declaration (AndroidManifest.xml, feature-recorder):**

```xml
<service
    android:name=".accessibility.TapDetectionAccessibilityService"
    android:exported="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
</service>
```

It requires `BIND_ACCESSIBILITY_SERVICE` permission and is exported because the system binds to it.

**Dependencies (line 15):**

```kotlin
@Inject lateinit var zoomOverlay: ZoomOverlayView
```

The ZoomOverlayView is injected so the accessibility service can call `simulateTap()` on it when taps are detected. This is a singleton provided by RecorderModule.

**Instance Variables (lines 17-18):**

- `tapCount: Int` -- Running count of detected taps during the current recording session.
- `isRecording: Boolean` -- Controls whether tap events are counted. Set to true when recording starts, false when it stops.

**`onServiceConnected()` (lines 20-29):**

Configures the accessibility service's `serviceInfo`:
- `eventTypes`: `TYPES_ALL_MASK` -- Listen to all event types. The code filters later.
- `feedbackType`: `FEEDBACK_GENERIC` -- No audio/haptic feedback.
- `flags`: `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` and `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`.
- `notificationTimeout`: 0 -- Receive events immediately.

**`onAccessibilityEvent()` (lines 31-46):**

The main event handler. Guards with null check and `isRecording` check. Filters for:
- `TYPE_VIEW_CLICKED` -- A view was clicked (button presses, list item taps, etc.)
- `TYPE_VIEW_TOUCH_INTERACTION_START` -- A touch gesture started.

When a match is found, it increments `tapCount` and calls `updateTapCount()`. The code has a commented-out section that attempts to get the event source's screen location (for zoom ripple positioning), but this logic is incomplete -- it does not call `simulateTap()` on the zoom overlay.

**`setRecording()` (lines 50-53):**

Enables or disables tap counting. When set to false, also resets the count.

**`updateTapCount()` (lines 57-63):**

Sends an `ACTION_UPDATE_TAP_COUNT` intent to the `RecordingForegroundService` with the current tap count as an extra. The foreground service then updates its `_tapCount` StateFlow and refreshes the notification.

**Design decisions:**
- The service listens to `TYPES_ALL_MASK` but filters to two specific event types. This is intentional -- using a mask rather than a filtered set means the service could be extended without recompilation, though ideally it would set only the required event types.
- The `isRecording` guard prevents counting taps when the user is not recording, avoiding privacy concerns.
- The incomplete zoom overlay integration means tap visualization currently only works through the ZoomDrawView's own onTouchEvent (i.e., taps on the overlay itself). The accessibility service increments the counter but does not trigger visual feedback. This is a known limitation/incomplete feature.

---

### di/RecorderModule.kt

**Package:** `com.camelcreatives.rekodi.recorder.di`

A standard Hilt module annotated with `@InstallIn(SingletonComponent::class)` providing two singletons:

**`provideWindowManager()` (lines 17-21):**

Obtains the `WindowManager` system service from the application context. Required by both `RecordingBubbleView` and `ZoomOverlayView` to add/remove/update overlay views. It is provided as a singleton because there should be only one WindowManager instance.

**`provideZoomOverlayView()` (lines 23-30):**

Creates and provides a singleton `ZoomOverlayView`. Takes the application context and window manager as parameters. The singleton scope ensures that the same overlay instance is used by both the accessibility service and any other component that needs to show tap visualizations.

---

### ui/RecorderScreen.kt

**Package:** `com.camelcreatives.rekodi.recorder.ui`

A Jetpack Compose screen that displays three required permissions and blocks the user from proceeding until all are granted.

**`RecorderPermissionScreen()` (lines 46-164):**

Parameters:
- `onPermissionGranted: () -> Unit` -- Callback invoked when the user presses Continue with all permissions granted.
- `modifier: Modifier` -- Standard Compose modifier.

The screen uses three `remember` checks to determine current permission state:

1. **Overlay Permission (SYSTEM_ALERT_WINDOW):** Uses `Settings.canDrawOverlays(context)` (API 23+). On older devices, returns true automatically. This is required for the floating bubble.

2. **Notification Permission (POST_NOTIFICATIONS):** Uses `ContextCompat.checkSelfPermission()` (API 33+). On older devices, returns true automatically. Required for the foreground service notification.

3. **Microphone Permission (RECORD_AUDIO):** Standard runtime permission check. Required for recording audio alongside screen capture.

Each permission uses a `rememberLauncherForActivityResult()`:
- Overlay: Uses `StartActivityForResult` contract, launching `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with the app's package URI.
- Notifications: Uses `RequestPermission` contract for `Manifest.permission.POST_NOTIFICATIONS`.
- Audio: Uses `RequestPermission` contract for `Manifest.permission.RECORD_AUDIO`.

The UI layout (Column with vertical scroll):
1. Title: "Permissions Required"
2. Subtitle explaining the need for permissions
3. Three `PermissionItem` composables, one for each permission
4. Continue button, enabled ONLY when all three permissions are granted

**`PermissionItem()` (lines 166-212):**

A reusable card composable showing:
- Title and description text
- A "Granted" label (in primary color) if the permission is already granted
- An "Enable" FilledTonalButton if not granted

The card uses `surfaceVariant` background color for subtle distinction. The Row layout has the text on the left (weight 1f) and the status/button on the right.

---

### Resources

**Layout: `bubble_layout.xml`**

The root is a `FrameLayout` with `clipChildren="false"` to allow the expanded panel to render without clipping. It contains two children:

1. **Mini Panel (LinearLayout, id: `mini_panel`):**
   - Width: wrap_content, Height: 48dp
   - Horizontal orientation, gravity center
   - Background: `@drawable/bubble_background` (amber rounded shape)
   - Contains:
     - `state_text` (TextView): Shows "Rekodi"/"Recording"/"Paused". White, 14sp, bold.
     - `tap_count` (TextView): Shows the tap count. Min 20x20dp, white 10sp bold, red oval background (`badge_background.xml`). Initially GONE.

2. **Expanded Panel (LinearLayout, id: `expanded_panel`):**
   - Width/Height: wrap_content
   - Vertical orientation, padded 12dp
   - Background: `@drawable/bubble_background_expanded` (dark translucent rounded shape)
   - Initially GONE
   - Contains:
     - `timer_text` (TextView): Shows "00:00", white, 18sp, bold.
     - Row of four `ImageButton` controls (40x40dp each, `circle_button.xml` background):
       - `btn_record`: Camera icon. Starts recording.
       - `btn_pause`: Pause icon. Pauses/resumes recording.
       - `btn_stop`: Close icon. Stops recording.
       - `btn_close`: Close icon. Hides the bubble.

**Drawables:**

- **`bubble_background.xml`:** A rectangle shape with solid amber color (`#E8A33D`) and 24dp corner radius. Gives the mini panel its distinctive rounded pill appearance.
- **`bubble_background_expanded.xml`:** A rectangle shape with semi-transparent dark color (`#CC1C1B1F`) and 16dp corner radius. Creates a translucent card-like background for the expanded control panel.
- **`badge_background.xml`:** An oval shape with solid red color (`#E53935`). Used as the background for the tap count badge, creating a small red dot or pill.
- **`circle_button.xml`:** An oval shape with semi-transparent white fill (`#33FFFFFF`). Creates a 40dp circular button that looks like a subtle white semi-transparent disk.

---

## Integration Points

1. **Navigation:** The feature-recorder module is accessed from the LibraryScreen's FAB, which triggers `MediaProjectionTrampolineActivity` to request screen capture consent, which then starts `RecordingForegroundService`.

2. **Settings:** `RecorderConfig` is populated from `SettingsDataStore` (defined in :core:core-data). The Settings screen allows configuring resolution, fps, bitrate, orientation, audio source, countdown, bubble visibility, zoom style, and tap counter.

3. **Crash Handler:** `RekodiCrashHandler` (in :app module) sends `ACTION_CRASH_SAFEGUARD` to the foreground service to clean up on crash.

4. **Data Layer:** The `RecordingRepository` (in :core:core-data) persists recording metadata via Room. The service inserts on start and updates on stop.

## Known Issues and Future Work

- The MediaRecorder configuration in `startRecording()` is hardcoded (1080p, 30fps, 8Mbps, MIC audio). The `RecorderConfig` data class exists but is not yet wired to the service.
- The zoom overlay tap visualization is partially incomplete -- the accessibility service detects taps and counts them, but does not call `simulateTap()` on the overlay. The ripple coordinates are not sent to the ZoomOverlayView.
- The COUNTDOWN and STOPPING states are defined but not implemented.
- The `onActivityReenter` method in MediaProjectionTrampolineActivity simply calls `finish()` without handling the result, which may cause issues on some devices.
- The bubble's close button and expand functionality could be more polished with animations.
