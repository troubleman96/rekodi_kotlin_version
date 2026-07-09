# Rekodi Architecture

## Module Structure

Rekodi follows a multi-module Android architecture designed for separation of concerns, compile-time isolation, and testability. The module dependency graph is strict: dependencies flow in one direction, from `app` (top) through `feature` modules, through `core` modules, with `core-common` at the bottom.

```
               +-----------------------------------------------+
               |                   app                          |
               |  (Application, MainActivity, NavHost, DI)     |
               +-----------------------+-----------------------+
                                       |
          +----------------------------+----------------------------+
          |                            |                            |
          v                            v                            v
  +-------+--------+          +--------+--------+          +-------+--------+
  | feature-recorder|          | feature-library  |          | feature-settings|
  | (service,bubble,|          | (gallery,detail) |          | (settings UI)  |
  |  accessibilty)  |          +--------+--------+          +-------+--------+
  +--------+--------+                   |                            |
           |                            |                            |
           v                            v                            |
  +--------+--------+          +--------+--------+                   |
  | feature-editor   |          | feature-onboarding|                |
  | (audio engine)   |          | (permission flow) |                |
  +--------+---------+          +--------+---------+                |
           |                            |                            |
           +----------+     +----------+                            |
                      |     |                                       |
                      v     v                                       v
               +------+-----+------+----------------------+---------+
               |                 core-ui                  |
               |  (theme, colors, typography, components)|
               +----------------+------------------------+
                                |
                                v
               +----------------+------------------------+
               |               core-data                |
               |  (Room DB, DataStore, Repository, DI)  |
               +----------------+------------------------+
                                |
                                v
               +----------------+------------------------+
               |              core-common               |
               |  (Dispatchers, Extensions, Result)      |
               +-----------------------------------------+

```

### Dependency rules

1. **`app` depends on everything**: All core and feature modules. It is the only module that knows about all others.
2. **Feature modules depend on `core` modules only**: No feature module depends on another feature module. `feature-editor` imports `core-common` (for `RekodiResult`), `core-data` (for `RecordingRepository`), and `core-ui` (for theme/components). It does NOT import `feature-recorder` or any other feature.
3. **`core-ui` depends on `core-common`** for utility functions. It does NOT depend on `core-data` or any feature.
4. **`core-data` depends on `core-common`** for `RekodiResult` and `RekodiDispatchers`. It does NOT depend on `core-ui`.
5. **`core-common` has zero internal dependencies**: It depends only on `kotlinx-coroutines-core`.

This strict layering prevents circular dependencies and enables independent feature development. Each feature module can be built, tested, and potentially extracted into a dynamic feature APK.

### Convention plugins (build-logic)

The `build-logic` module contains five convention plugins that ensure consistent Gradle configuration across modules:

| Plugin | Applied to | What it does |
|--------|-----------|-------------|
| `camelcreatives.android.library` | `core-common`, `core-data` | Applies Android library + Kotlin plugins, sets compileSdk=35, minSdk=26, Java 17 |
| `camelcreatives.android.library.compose` | `core-ui` | Same as above + enables Compose, adds Compose BOM and Material3 dependencies |
| `camelcreatives.android.application.compose` | `app` | Applies Android application + Kotlin + Compose plugins, sets compileSdk/minSdk, enables Compose |
| `camelcreatives.android.hilt` | `core-data`, `app`, `feature-*` | Applies Hilt and KSP plugins, adds Hilt runtime + compiler dependencies |
| `camelcreatives.android.feature` | `feature-*` | Combines library + Hilt + Compose plugins in one application |

---

## Single-Activity Architecture

Rekodi uses a **single Activity** (`MainActivity`) with **Compose Navigation** for all screen transitions. This architecture offers:

1. **Simpler lifecycle management**: One `onCreate()`, one set of lifecycle observers. No Activity A to Activity B transitions with their complex lifecycle interactions.
2. **Consistent edge-to-edge**: `enableEdgeToEdge()` is called once in `MainActivity.onCreate()` and applies to all screens.
3. **Shared element transitions**: With Navigation Compose, shared element transitions between screens are straightforward (e.g., a recording card expanding to the detail screen).
4. **State preservation**: `rememberNavController()` is scoped to the Activity's composition, surviving configuration changes due to Compose's recomposition model.

### Navigation routes

The navigation graph is defined in `RekodiNavHost.kt`:

| Route | Pattern | Screen | Parameters |
|-------|---------|--------|-----------|
| `onboarding` | `"onboarding"` | `OnboardingScreen` | None |
| `home` | `"home"` | `LibraryScreen` | None |
| `audio_editor` | `"audio_editor/{recordingId}"` | `AudioEditorScreen` | `recordingId: Long` |
| `settings` | `"settings"` | `SettingsScreen` | None |
| `recording_detail` | `"recording_detail/{recordingId}"` | `RecordingDetailScreen` | `recordingId: Long` |

All transitions use `navController.navigate()` for forward navigation and `navController.popBackStack()` for back navigation. The `Routes` object provides type-safe builder functions to construct route strings with parameters.

The start destination is `HOME`. If the onboarding flow needs to be shown, a `DataStore` preference check before the `NavHost` composition determines whether to navigate to `ONBOARDING` first.

---

## Hilt DI Graph

Hilt generates a dependency graph from `@Module` annotated objects. Each module is installed in the `SingletonComponent` (application-scoped). Here is every binding in the graph:

### Provided by `AppModule` (`app` module)
```
RekodiDispatchers -> DefaultRekodiDispatchers  // ApplicationScope
```

### Provided by `DataModule` (`core-data` module)
```
RekodiDatabase     -> Room.databaseBuilder()     // Singleton
RecordingDao       <- RekodiDatabase             // Singleton
```

### Provided by `RecorderModule` (`feature-recorder` module)
```
WindowManager      <- Context.getSystemService()  // Singleton
ZoomOverlayView    <- new ZoomOverlayView()       // Singleton
```

### All `@Inject` constructors
- `SettingsDataStore` (in `core-data`): `@Inject constructor(@ApplicationContext context: Context)`
- `RecordingRepository` (in `core-data`): `@Inject constructor(recordingDao: RecordingDao)`
- `AudioEngine` (in `feature-editor`): `@Inject constructor(@ApplicationContext context: Context)`
- `RecordingForegroundService` (in `feature-recorder`): `@Inject lateinit var recordingRepository: RecordingRepository`
- `TapDetectionAccessibilityService` (in `feature-recorder`): `@Inject lateinit var zoomOverlay: ZoomOverlayView`

### How injection works in a Service

`RecordingForegroundService` is annotated with `@AndroidEntryPoint`. Hilt generates a `Hilt_RecordingForegroundService` base class that performs field injection in `onCreate()`. When the service receives `ACTION_START`, the `recordingRepository` field is already injected because Hilt's injection happens before `super.onCreate()` returns.

---

## Data Flow Architecture

### Recording metadata flow

```
RecordingForegroundService        RecordingRepository               Room Database
        |                               |                               |
        |-- insertRecording(entity) ---->|                               |
        |                               |-- INSERT INTO recordings ----->|
        |                               |       (returns id)             |
        |<---- recordingId = 123 -------|                               |
        |                               |                               |
        |-- (recording completes)        |                               |
        |-- updateRecording(entity) ---->|                               |
        |                               |-- UPDATE recordings ---------->|
        |                               |       SET durationMs=...       |
        |                               |       SET fileSizeBytes=...    |
```

### Settings flow

```
SettingsScreen (Compose)        SettingsDataStore               DataStore File
        |                               |                               |
        |-- collectAsState() <----------|<-- .data (Flow) <-------------|
        |   (RekodiSettings)            |       read every change       |
        |                               |                               |
        |-- updateDarkMode("Dark") ---->|                               |
        |                               |-- .edit { put("dark_mode",..)}|
        |                               |       (atomic write)          |
```

### Service state flow

```
RecordingForegroundService          Compose UI / Bubble View
        |                                   |
        |-- _recordingState (StateFlow) ---->| collectAsState()
        |-- _elapsedSeconds (StateFlow) ---->| collectAsState()
        |-- _tapCount (StateFlow) ---------->| collectAsState()
        |                                   |
        |-- StateFlow.value += <-- Service coroutine updates
```

The `StateFlow`s are created in the Service using `MutableStateFlow(initialValue)`, backed by `SupervisorJob()` + `Dispatchers.IO`. The Service exposes them as read-only `StateFlow` via `.asStateFlow()`. The UI (both Compose and the floating bubble View system) observes these flows and recomposes/re-renders on each emission.

The Service stores the StateFlow references in instance properties. Since the Service is a singleton within the process (Hilt-managed, albeit indirectly), the same StateFlow instances are used throughout the process lifetime. The `recordingViewModel` in each feature module observes the service's state via a bridge helper class (not shown in code, but conceptually: a `ViewModel` that injects a service state observer).

---

## Recording Engine Flow

This is the most architecturally complex feature in the application. Here is the complete end-to-end flow:

### Step 1: User taps Record

The user interacts with either:
- The Compose UI `RecorderPermissionScreen` / `LibraryScreen` (after permissions are granted).
- The floating `RecordingBubbleView`.

Both paths result in a call to `context.startActivity(Intent(context, MediaProjectionTrampolineActivity::class.java))`.

### Step 2: MediaProjectionTrampolineActivity opens

The trampoline Activity is transparent and has no visible UI. In `onCreate()`:

```kotlin
val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
```

`createScreenCaptureIntent()` returns an `Intent` that, when launched via `startActivityForResult()`, displays the system screen recording permission dialog. This dialog asks the user "Rekodi will capture all content on the screen" with options to "Start recording" or "Cancel" or "Don't show again".

### Step 3: User grants screen capture permission

If the user grants permission, `onActivityResult()` is called with `resultCode == RESULT_OK` and a non-null `data` Intent. The `data` Intent contains the user's consent token.

### Step 4: RecordingForegroundService starts with projection token

```kotlin
val serviceIntent = Intent(this, RecordingForegroundService::class.java).apply {
    action = RecordingForegroundService.ACTION_START
    putExtra(EXTRA_RESULT_CODE, resultCode)      // resultCode from onActivityResult
    putExtra(EXTRA_RESULT_DATA, data)             // data Intent from onActivityResult
}
startForegroundService(serviceIntent)
```

`startForegroundService()` is used instead of `startService()` because the service must post a notification within 5 seconds or the system kills it.

### Step 5: MediaRecorder configured, VirtualDisplay created

Inside `RecordingForegroundService.startRecording()`:

1. `MediaProjectionManager.getMediaProjection(resultCode, data)` recovers the `MediaProjection` object from the consent token.
2. `MediaRecorder` is configured:
   - Audio source: `MIC` (microphone). Future versions will support internal audio via `AudioPlaybackCaptureConfiguration`.
   - Video source: `SURFACE` (receives frames from a `VirtualDisplay`).
   - Output format: `MPEG_4` (standard `.mp4` container).
   - Video encoder: `H264` (hardware-accelerated on all modern devices).
   - Audio encoder: `AAC` (standard audio codec for MP4).
   - Bitrate: 8 Mbps (fixed for now, `RecorderConfig` will make this configurable).
   - Frame rate: 30 fps.
   - Resolution: 1920x1080 (Full HD) fixed.
   - Output file: `getExternalFilesDir(null)/Rekodi_<timestamp>.mp4`.
3. `mediaRecorder.prepare()` configures the encoder and output file.
4. `mediaRecorder.surface` provides the input surface.
5. `mediaProjection.createVirtualDisplay("RekodiDisplay", 1920, 1080, 320, AUTO_MIRROR, surface, null, null)` creates a virtual display that mirrors the screen and feeds frames to the `MediaRecorder` surface.
6. `mediaRecorder.start()` begins recording.

### Step 6: Service becomes foreground with notification

```kotlin
startForeground(NOTIFICATION_ID, buildNotification())
```

The notification:
- Title: "Recording"
- Content: "MM:SS . N taps" (live timer and tap counter)
- Actions: Pause/Resume button, Stop button
- Ongoing: true (non-dismissable)
- Silent: true (no sound)

The timer is updated every second via a coroutine coroutine that delays 1000ms and increments `_elapsedSeconds`.

### Step 7: On stop, file finalized, metadata saved to Room

When the user taps "Stop" (via notification, bubble, or in-app button):

```kotlin
private fun stopRecording() {
    mediaRecorder?.stop()       // Finalizes the MP4 file
    mediaRecorder?.release()    // Releases encoder resources
    virtualDisplay?.release()   // Releases display resources
    mediaProjection?.stop()     // Ends screen capture

    // Update Room entity with final metadata
    recordingRepository.updateRecording(entity.copy(
        durationMs = elapsed * 1000,
        fileSizeBytes = outputFile?.length() ?: 0,
        tapCount = _tapCount.value
    ))

    stopForeground(STOP_FOREGROUND_REMOVE)  // Removes notification
    stopSelf()                              // Stops the service
}
```

### Step 8: On crash, CrashHandler safeguards partial recording

If the app crashes during recording (Step 5-6), the `RekodiCrashHandler` sends `ACTION_CRASH_SAFEGUARD` to the service, which calls `safeguardRecording()`:

```kotlin
private fun safeguardRecording() {
    try {
        mediaRecorder?.stop()    // Finalize partial file
        mediaRecorder?.release()
    } catch (_: Exception) {}    // Ignore errors during crash salvage
    mediaRecorder = null
    virtualDisplay?.release()
    virtualDisplay = null
}
```

This ensures that even a crash produces a playable (though possibly truncated) video file. The file size may not be updated in Room (the crash happens before the Room update), but the partial file on disk contains the recorded frames up to the crash point.

---

## Floating Bubble Architecture

The `RecordingBubbleView` class manages a `WindowManager` overlay that appears during recording.

### WindowManager setup

```kotlin
val params = WindowManager.LayoutParams(
    WRAP_CONTENT, WRAP_CONTENT,
    TYPE_APPLICATION_OVERLAY,       // Requires SYSTEM_ALERT_WINDOW permission
    FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = TOP or START
    x = 0; y = 200                 // Initial position: top-left, 200px down
}
```

- `TYPE_APPLICATION_OVERLAY` (API 26+): The correct window type for overlays. On older devices, falls back to `TYPE_PHONE`.
- `FLAG_NOT_FOCUSABLE`: Touches pass through the overlay to the content beneath, except on the bubble itself.
- `FLAG_LAYOUT_NO_LIMITS`: Allows positioning outside the screen bounds (for edge snapping).

### XML layout

The bubble uses `bubble_layout.xml` (in `feature-recorder` resources) which contains:
- `mini_panel`: A small circular/rounded view showing state text (Recording/Paused/Rekodi).
- `expanded_panel`: A larger panel with Record, Stop, Pause, and Close buttons.
- `state_text`: Shows current state (e.g., `" Recording"`, `" Paused"`).
- `timer_text`: Shows elapsed time.
- `tap_count`: Shows tap counter badge.

### Touch handling

The bubble implements drag-and-snap:

1. `ACTION_DOWN`: Records initial position and touch coordinates. Resets idle timer, makes bubble fully opaque.
2. `ACTION_MOVE`: Updates `params.x` and `params.y` based on touch delta, calls `windowManager.updateViewLayout()`.
3. `ACTION_UP`: If movement is small (<10px), treats as a click (toggles expanded panel). Otherwise, snaps to the nearest screen edge using `snapToEdge()`.

### Edge snapping

```kotlin
private fun snapToEdge(params: WindowManager.LayoutParams) {
    val display = windowManager.defaultDisplay
    val size = Point().also { display.getSize(it) }
    val targetX = if (params.x < size.x / 2) 0 else size.x - bubbleView!!.width
    val anim = ValueAnimator.ofInt(params.x, targetX)
    anim.addUpdateListener {
        params.x = it.animatedValue as Int
        windowManager.updateViewLayout(bubbleView, params)
    }
    anim.duration = 200
    anim.start()
}
```

The bubble snaps to the left or right edge depending on which half of the screen it is closest to. The snap is animated over 200ms using `ValueAnimator`.

### Idle timer

After 3 seconds of no interaction, the bubble's alpha fades to 0.4 (set via `bubbleOpacity` setting). Touching the bubble resets the timer and restores full opacity. This prevents the bubble from being distracting during long recordings.

### State updates from service

The bubble does NOT directly observe StateFlows (it is a View system component, not Compose). Instead, the `RecordingForegroundService` or an intermediary calls:

```kotlin
bubbleView.updateState(recordingState)
bubbleView.updateTimerText("05:23")
bubbleView.updateTapCount(42)
```

These calls are made from the service's coroutine (the timer update loop) and from the `ACTION_UPDATE_TAP_COUNT` intent handler.

---

## Zoom/Tap Visualization

### TapDetectionAccessibilityService

The accessibility service runs in the background and listens for `TYPE_VIEW_CLICKED` and `TYPE_VIEW_TOUCH_INTERACTION_START` events. When a tap is detected:

1. Increments the internal `tapCount`.
2. Sends `ACTION_UPDATE_TAP_COUNT` intent to `RecordingForegroundService` with the new count.
3. Calls `zoomOverlay.simulateTap(x, y)` if the zoom visualization is enabled.

The service is enabled by the user in Settings > Accessibility > Rekodi. It can also be programmatically opened to the accessibility settings via an Intent.

### ZoomOverlayView

When enabled, `ZoomOverlayView` creates another WindowManager overlay (fullscreen, transparent, `FLAG_NOT_TOUCHABLE` so touches pass through) that draws animated ripple effects at tap locations.

The `ZoomDrawView` (inner class, extends `View`) uses `Canvas.drawCircle()` in `onDraw()`:

```kotlin
private class ZoomDrawView(context: Context) : View(context) {
    private val ripplePaint = Paint(ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val fillPaint = Paint(ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private data class RippleData(
        val x: Float, val y: Float,
        var radius: Float = 10f,
        var alpha: Float = 0.6f,
        val maxRadius: Float = 120f
    )
    private var ripples = mutableListOf<RippleData>()

    private fun addRipple(x: Float, y: Float) {
        val ripple = RippleData(x, y)
        ripples.add(ripple)
        animateRipple(ripple)
    }

    private fun animateRipple(ripple: RippleData) {
        val anim = ValueAnimator.ofFloat(10f, ripple.maxRadius)
        anim.duration = 500
        anim.addUpdateListener {
            ripple.radius = it.animatedValue as Float
            ripple.alpha = 0.6f * (1f - (ripple.radius / ripple.maxRadius))
            invalidate()
        }
        anim.start()
        handler.postDelayed({ ripples.remove(ripple); invalidate() }, 600)
    }
}
```

Each tap creates a `RippleData` that animates from radius 10 to 120 over 500ms, fading from alpha 0.6 to 0.0. Two concentric circles are drawn: a filled circle (semi-transparent fill) and two stroked circles (outer rings). The ripple is removed from the list after 600ms, stopping its rendering. The ripple color is configurable via the `zoomColor` setting (default amber `#E8A33D`).

---

## Audio Editor Architecture

### Waveform extraction

`AudioEngine.extractWaveform()` uses `MediaCodec` to decode the audio file to raw PCM samples, then computes peak amplitudes into `targetPeaks` (default 1000) samples:

1. `MediaExtractor` opens the file and selects the audio track.
2. `MediaCodec.createDecoderByType(mime)` creates a decoder for the audio codec (AAC in most cases).
3. Decoder is configured with the audio format (sample rate, channel count) and started.
4. The loop feeds compressed data to the decoder and receives decoded PCM `ShortBuffer` output.
5. PCM chunks are concatenated into a single `ShortArray`.
6. `computePeaks()` divides the PCM array into `targetPeaks` chunks and finds the maximum absolute amplitude in each chunk, normalized to [0.0, 1.0].
7. Returns a `WaveformPeaks` object containing the float array, sample rate, and total duration.

### Non-destructive editing

The `AudioEngine` provides three editing operations, all creating NEW output files (non-destructive):

1. **`trimAudio(inputPath, outputPath, startMs, endMs)`**: Seeks to `startMs`, copies frames to `endMs`, writes to new file via `MediaMuxer`. The original file is never modified.

2. **`applyFade(inputPath, outputPath, fadeInMs, fadeOutMs)`**: Copies all frames but adjusts per-frame gain at the beginning and end. Currently a simplified implementation that modifies flags rather than actual PCM gain -- a real implementation would decode, apply gain on PCM level, and re-encode.

3. **`mergeAudio(clips, outputPath)`**: Sequentially concatenates multiple `AudioClip` segments into one output file. Each clip specifies a source file, start/end positions, and optional fade durations. The presentation timestamps are adjusted so the output timeline is continuous.

### Canvas-based waveform rendering

The `AudioEditorScreen` renders the waveform using Compose Canvas. The `WaveformPeaks.samples` float array is drawn as vertical bars:

```kotlin
Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
    val barWidth = size.width / peaks.size
    for (i in peaks.indices) {
        val barHeight = peaks[i] * size.height * 0.9f
        drawRect(
            color = MaterialTheme.colorScheme.primary,
            topLeft = Offset(i * barWidth, size.height - barHeight),
            size = Size(barWidth * 0.8f, barHeight)
        )
    }
}
```

### Undo/redo stack

`UndoState` data class captures snapshots of the clip list with a description. Each editing action pushes a new `UndoState` onto a stack:

```kotlin
data class UndoState(
    val clips: List<AudioClip>,
    val description: String  // "Trim 00:30 to 01:15"
)

private val undoStack = mutableListOf<UndoState>()
private val redoStack = mutableListOf<UndoState>()
```

On undo, the previous state is popped from `undoStack`, the current state is pushed to `redoStack`. On redo, the reverse happens. The UI enables/disables undo/redo buttons based on stack size.

---

## Permissions Strategy

Rekodi requires five permission categories:

### 1. Overlay (SYSTEM_ALERT_WINDOW)
- **When requested**: At first-record attempt, before the trampoline Activity.
- **How**: Sent to `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with the app's package URI.
- **Degradation**: Bubble feature is disabled if not granted. Recording can still start from the in-app UI.

### 2. Notifications (POST_NOTIFICATIONS, Android 13+)
- **When requested**: At first-record attempt.
- **How**: Standard `ActivityResultContracts.RequestPermission()`.
- **Degradation**: Foreground service crashes if notification is not posted within 5 seconds. Without this permission, `startForeground()` throws `SecurityException`. The UI blocks recording until this is granted.

### 3. Microphone (RECORD_AUDIO)
- **When requested**: At first-record attempt.
- **How**: Standard `ActivityResultContracts.RequestPermission()`.
- **Degradation**: Audio-less recording (video only). The UI warns the user.

### 4. Screen capture (implicit, via trampoline)
- **When requested**: Every time the user taps Record.
- **How**: System dialog via `MediaProjectionManager.createScreenCaptureIntent()`.
- **Degradation**: If denied, recording cannot start. The user can re-trigger by tapping Record again.

### 5. Accessibility service
- **When requested**: When the user enables "Tap visualization" in settings.
- **How**: The app opens `Settings.ACTION_ACCESSIBILITY_SETTINGS` for the user to manually enable the service.
- **Degradation**: Tap visualization and tap counter do not work. Other recording features remain functional.

### Just-in-time principle

All permissions are requested at the moment they are first needed, not at app install or first launch. This follows Google's permission best practices and avoids overwhelming the user with a permission wall at onboarding. The `RecorderPermissionScreen` in `feature-recorder` handles the initial permission flow, but each permission is also checkable and requestable individually from settings.

---

## Crash Prevention

Rekodi implements multiple layers of crash prevention:

### 1. Global Exception Handler

`RekodiCrashHandler` (in `app` module) catches all uncaught exceptions and sends `ACTION_CRASH_SAFEGUARD` to the recording service to finalize any in-progress recording before the process dies.

### 2. Service START_REDELIVER_INTENT

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_REDELIVER_INTENT
}
```

If the service is killed by the system (low memory, etc.), `START_REDELIVER_INTENT` tells the system to re-deliver the last intent. This means if the service was in the middle of `ACTION_START` when killed, the system will retry the start. This is critical for recording resilience.

### 3. Sealed Result types

The `AudioEngine` and other I/O operations return `RekodiResult<T>` instead of throwing exceptions. This forces callers to handle both success and failure cases explicitly. The `onError` extension ensures errors are at least logged or shown to the user.

### 4. Defensive MediaRecorder operations

All `MediaRecorder.stop()`, `MediaRecorder.release()`, `VirtualDisplay.release()`, and `MediaProjection.stop()` calls are wrapped in try-catch blocks. `MediaRecorder.stop()` can throw `RuntimeException` if the recording state is unexpected (e.g., paused, already stopped). The catches suppress these expected exceptions.

### 5. Try-catch in safeguard path

The `safeguardRecording()` and `stopRecording()` methods catch all exceptions during media resource cleanup to ensure that one failure (e.g., `MediaRecorder.stop()` throws) does not prevent subsequent cleanup (e.g., `VirtualDisplay.release()`).

### 6. Room as single source of truth

Recording metadata is written to Room BEFORE recording starts (insert with defaults) and updated AFTER recording completes. If a crash occurs between these two points, the database still contains an entry with `durationMs = 0` and `fileSizeBytes = 0`. The UI can detect these "incomplete" entries and either hide them or show a "Recovery needed" indicator. This is more resilient than writing metadata only at completion.

---

## Summary of Key Architectural Decisions

| Decision | Rationale |
|----------|----------|
| Single Activity | Simplified lifecycle, Compose Navigation, consistent edge-to-edge |
| Multi-module with convention plugins | Compile-time isolation, parallel builds, clear dependency direction |
| `RekodiDispatchers` interface | Testability: inject test dispatchers in unit tests |
| `RekodiResult` sealed class | Explicit error handling, custom error messages, no exception overhead |
| Room for recording metadata | Reactive `Flow` queries, type-safe queries, automatic table invalidation |
| DataStore for settings | Async API, `Flow`-based observation, no ANR risk, Protocol Buffers format |
| `StateFlow` for service state | Shared state between Service and UI without polling, lifecycle-aware collection |
| `MediaProjectionTrampolineActivity` | MediaProjection requires Activity context; trampoline pattern avoids coupling service to Activity lifecycle |
| Transparent overlay for bubble | WindowManager overlay persists across Activity configuration changes; not tied to Compose lifecycle |
| Crash handler safeguards recording | Ensures partial recording is finalized before process death |
| Non-destructive audio editing | Original file never modified; undo/redo via snapshot stack |
| Just-in-time permissions | Google best practice, reduces user friction at first launch |
