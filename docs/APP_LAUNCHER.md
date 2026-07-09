# app Module -- Application Launcher

## Purpose

The `app` module is the application entry point. It ties together all other modules: the three `core-*` modules and all five `feature-*` modules. It contains the `Application` class, `MainActivity`, the navigation graph, DI configuration at the app level, the crash handler, the `MediaProjection` trampoline Activity, and all resources (`AndroidManifest.xml`, string resources, XML themes, colors).

---

## File: RekodiApplication.kt

**Path:** `app/src/main/java/com/camelcreatives/rekodi/RekodiApplication.kt`

```kotlin
package com.camelcreatives.rekodi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RekodiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(RekodiCrashHandler(this))
    }
}
```

### Analysis

- **`@HiltAndroidApp`**: This annotation triggers Hilt's code generation. Hilt generates:
  - A base class `Hilt_RekodiApplication` that extends `Application` and sets up the Hilt component hierarchy during `onCreate()`.
  - The `SingletonComponent` -- the top-level Hilt component that lives as long as the application process. All `@InstallIn(SingletonComponent::class)` modules register their bindings here.
  - Entry point classes for Hilt injection.
  
  Without `@HiltAndroidApp`, Hilt would not have a root component, and `@AndroidEntryPoint` annotations on Activities and Services would fail to compile.

- **`onCreate()`**: Called when the application process starts, before any Activity, Service, or ContentProvider. This is the earliest point to set up the crash handler. `Thread.setDefaultUncaughtExceptionHandler()` sets a global handler for all uncaught exceptions on all threads (including the main thread). The previous handler (if any) is stored inside `RekodiCrashHandler` for fallthrough.

---

## File: RekodiCrashHandler.kt

**Path:** `app/src/main/java/com/camelcreatives/rekodi/RekodiCrashHandler.kt`

```kotlin
package com.camelcreatives.rekodi

import android.app.Application
import android.content.Intent
import com.camelcreatives.rekodi.recorder.service.RecordingForegroundService
import com.camelcreatives.rekodi.recorder.service.RecordingState

class RekodiCrashHandler(private val app: Application) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val intent = Intent(app, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_CRASH_SAFEGUARD
        }
        try {
            app.startForegroundService(intent)
        } catch (_: Exception) {}
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
```

### Analysis

**Why a custom crash handler?**

The core requirement "never lose a recording" means that if the app crashes during an active recording, we must safely finalize the video file before the process dies. A standard crash dialog would kill the process immediately, leaving the `MediaRecorder` in an inconsistent state and the output file unplayable.

**How it works:**

1. When an uncaught exception occurs, the JVM calls `uncaughtException()` on the default handler.
2. The handler creates an `Intent` targeting `RecordingForegroundService` with the action `ACTION_CRASH_SAFEGUARD`.
3. `app.startForegroundService(intent)` sends the intent. Since the service runs in the same process, `Service.onStartCommand()` is called synchronously before the process dies. The service's `safeguardRecording()` method:
   - Calls `mediaRecorder.stop()` and `mediaRecorder.release()` to finalize the file.
   - Releases `VirtualDisplay` and `MediaProjection`.
   - Sets `_recordingState` to `IDLE`.
4. The exception is then passed to the default handler (which may show the standard "App has stopped" dialog or `Toast`).

**Limitations:**

- The service handler executes on the main thread. If the service's `onStartCommand` blocks or throws, the safeguard may not complete. The try-catch ensures at least we attempted the safeguard.
- If the crash originates from within the service itself (e.g., `NullPointerException` in `safeguardRecording()`), the safeguard may fail. The service code is designed to be defensive with try-catch blocks around media operations.
- The `STOP_SELF` call at the end of `stopRecording()` is not called here -- we leave that to the process termination.

---

## File: MainActivity.kt

**Path:** `app/src/main/java/com/camelcreatives/rekodi/MainActivity.kt`

```kotlin
package com.camelcreatives.rekodi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.camelcreatives.rekodi.ui.theme.RekodiTheme
import com.camelcreatives.rekodi.ui.navigation.RekodiNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RekodiTheme {
                RekodiNavHost()
            }
        }
    }
}
```

### Analysis

**`@AndroidEntryPoint`**: Hilt annotation that generates a `Hilt_MainActivity` base class. This enables field injection with `@Inject` and `@HiltViewModel` in ViewModels. Without this, Hilt cannot inject into the Activity.

**`installSplashScreen()`**: Called BEFORE `super.onCreate()`. This is the Android 12+ Splash Screen API (`androidx.core:core-splashscreen`). It displays the app's splash icon (from `ic_launcher`) on a white/amber background during cold start, while the Activity's `onCreate` runs. This provides a smooth, branded launch experience instead of a blank white screen. On pre-Android 12, the library gracefully degrades (no splash).

**`enableEdgeToEdge()`**: Sets the system bar colors to transparent and draws the Compose content behind the status and navigation bars. The Compose content must handle system window insets using `WindowInsets` APIs (e.g., `WindowInsets.systemBars` padding). This gives a modern, immersive look.

**`setContent { ... }`**: The Compose entry point. Everything inside is a Compose tree. The structure is:
- `RekodiTheme` wraps the entire app (provides color scheme, typography, shapes).
- `RekodiNavHost` contains the Compose Navigation graph.

**Single Activity architecture**: There is only one Activity. All screens are Compose destinations managed by Navigation. This is the modern Android architecture pattern -- it avoids configuration change complexities, simplifies lifecycle management, and enables smooth shared element transitions between screens.

---

## File: MediaProjectionTrampolineActivity.kt

**Path:** `app/src/main/java/com/camelcreatives/rekodi/service/MediaProjectionTrampolineActivity.kt`

```kotlin
package com.camelcreatives.rekodi.service

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.camelcreatives.rekodi.recorder.service.RecordingForegroundService

class MediaProjectionTrampolineActivity : Activity() {

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, RecordingForegroundService::class.java).apply {
                action = RecordingForegroundService.ACTION_START
                putExtra(RecordingForegroundService.EXTRA_RESULT_CODE, resultCode)
                putExtra(RecordingForegroundService.EXTRA_RESULT_DATA, data)
            }
            startForegroundService(serviceIntent)
        }
        finish()
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
        finish()
    }
}
```

### Analysis

**Why a separate trampoline Activity?**

`MediaProjection` consent requires:
1. A `MediaProjectionManager` system service.
2. Calling `mgr.createScreenCaptureIntent()` which returns an `Intent`.
3. Launching that intent via `startActivityForResult()` (a `ComponentActivity` method) to show the system consent dialog.

The critical constraint: you can only call `startActivityForResult()` from an Activity context, not a Service context. Since `RecordingForegroundService` is a Service, it cannot directly request screen capture permission. The trampoline pattern solves this:

1. The service (or UI) launches this transparent Activity.
2. The Activity immediately shows the system screen capture consent dialog.
3. If the user grants consent, the Activity forwards the result to the Service via an Intent extra.
4. The Activity finishes immediately (transparent, no UI shown to user).

**Implementation details:**

- **Transparent theme** (`@style/Theme.Rekodi.Transparent`): The Activity has no visible UI. It is essentially just a lifecycle container for the permission request.
- **`excludeFromRecents="true"`**: The Activity does not appear in the recent tasks list, so the user cannot navigate back to it.
- **`launchMode="singleTask"`**: Prevents multiple instances of the trampoline from stacking if the user somehow triggers it multiple times.
- **`onActivityReenter()`**: Handles the case where the user returns to the app via the recent tasks list after granting permission. Without this, the Activity would be re-displayed.
- **`finish()` at end**: Always finishes, whether permission was granted or denied. The UI observes the recording state via `StateFlow` and reacts accordingly.

---

## File: RekodiNavHost.kt

**Path:** `app/src/main/java/com/camelcreatives/rekodi/ui/navigation/RekodiNavHost.kt`

```kotlin
package com.camelcreatives.rekodi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.camelcreatives.rekodi.library.ui.LibraryScreen
import com.camelcreatives.rekodi.settings.SettingsScreen
import com.camelcreatives.rekodi.editor.ui.AudioEditorScreen
import com.camelcreatives.rekodi.onboarding.OnboardingScreen
import com.camelcreatives.rekodi.recorder.model.Recording

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val AUDIO_EDITOR = "audio_editor/{recordingId}"
    const val SETTINGS = "settings"
    const val RECORDING_DETAIL = "recording_detail/{recordingId}"

    fun audioEditor(recordingId: Long) = "audio_editor/$recordingId"
    fun recordingDetail(recordingId: Long) = "recording_detail/$recordingId"
}

@Composable
fun RekodiNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = { navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
            )
        }
        composable(Routes.HOME) {
            LibraryScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToEditor = { recordingId -> navController.navigate(Routes.audioEditor(recordingId)) },
                onNavigateToDetail = { recordingId -> navController.navigate(Routes.recordingDetail(recordingId)) }
            )
        }
        composable(Routes.AUDIO_EDITOR) {
            AudioEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.RECORDING_DETAIL) {
            com.camelcreatives.rekodi.library.ui.RecordingDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditor = { recordingId -> navController.navigate(Routes.audioEditor(recordingId)) }
            )
        }
    }
}
```

### Analysis

**Routes object**: Centralizes route constants and builder functions. Benefits:
- Avoids hardcoded route strings scattered across files.
- The `{recordingId}` path parameter is defined once and reused via `audioEditor()` and `recordingDetail()` helper functions.
- Route strings are `const val` for performance (compile-time constants are inlined).

**Start destination: `HOME`**: The app starts at the home screen (library). Onboarding is a separate screen that can be navigated to based on a `SharedPreferences` or `DataStore` check (not shown in this file, typically done before `NavHost` composition by checking if onboarding is complete).

**Navigation flow:**

| Route | Parameters | Screen | Back behavior |
|-------|-----------|--------|---------------|
| `ONBOARDING` | None | Permission/customization walkthrough | Completes with `popUpTo(ONBOARDING, inclusive=true)` removing onboarding from the back stack |
| `HOME` | None | `LibraryScreen` with recording list | Root of the back stack |
| `AUDIO_EDITOR` | `recordingId` (Long) | `AudioEditorScreen` | `popBackStack()` returns to previous screen |
| `SETTINGS` | None | `SettingsScreen` | `popBackStack()` returns to previous screen |
| `RECORDING_DETAIL` | `recordingId` (Long) | `RecordingDetailScreen` | `popBackStack()` returns to previous screen |

The parameter handling for `{recordingId}` is not shown in the `composable()` destinations (the parameter extraction uses `navBackStackEntry.arguments?.getLong("recordingId")` inside each screen's ViewModel/entry point). This follows the standard Navigation Compose pattern.

---

## File: AppModule.kt (DI)

**Path:** `app/src/main/java/com/camelcreatives/rekodi/di/AppModule.kt`

```kotlin
package com.camelcreatives.rekodi.di

import com.camelcreatives.rekodi.common.DefaultRekodiDispatchers
import com.camelcreatives.rekodi.common.RekodiDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDispatchers(): RekodiDispatchers = DefaultRekodiDispatchers()
}
```

This is the only app-level DI binding. It provides the `RekodiDispatchers` abstraction. All other bindings are provided by their respective modules:
- `DataModule` (in `core-data`): provides `RekodiDatabase` and `RecordingDao`.
- `RecorderModule` (in `feature-recorder`): provides `WindowManager` and `ZoomOverlayView`.

The `@Singleton` annotation ensures that all consumers receive the same dispatcher instance. This is important because the dispatchers themselves are stateless singletons, so recreating them would be wasteful.

---

## AndroidManifest.xml

**Path:** `app/src/main/AndroidManifest.xml`

### Permission declarations

| Permission | Purpose |
|-----------|---------|
| `FOREGROUND_SERVICE` | Required to start any foreground service (Android 9+). Without this, `startForegroundService()` crashes. |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Specific foreground service type for media projection (Android 14+). Declares that the service uses `mediaProjection` as its foreground service type. |
| `POST_NOTIFICATIONS` | Required to post notifications (Android 13+). The recording notification requires this. |
| `SYSTEM_ALERT_WINDOW` | Required for the floating bubble overlay (`TYPE_APPLICATION_OVERLAY`). The user must manually grant this in Settings. |
| `RECORD_AUDIO` | Required for `MediaRecorder.setAudioSource(MIC)`. |
| `MEDIA_PROJECTION` | Required for screen capture APIs. |
| `ACCESSIBILITY_SERVICE` | Required for `TapDetectionAccessibilityService`. |
| `READ_MEDIA_VIDEO` | Required to read video files from MediaStore (Android 13+ granular media permissions). |
| `READ_MEDIA_AUDIO` | Required to read audio files from MediaStore. |
| `INTERNET` | Declared but not currently used in-app. May be needed for analytics or crash reporting in future versions. |
| `BIND_ACCESSIBILITY_SERVICE` | Required to bind the accessibility service. `tools:ignore="BinderResources"` suppresses the lint warning about accessibility service configuration. |

### Activity declarations

**MainActivity**:
- `android:exported="true"`: Required for launcher activities.
- `android:theme="@style/Theme.Rekodi"`: Uses the translucent status/nav bar theme.
- Intent filter for `MAIN`/`LAUNCHER`: Makes this the entry point Activity.

**MediaProjectionTrampolineActivity**:
- `android:excludeFromRecents="true"`: Not shown in overview.
- `android:launchMode="singleTask"`: Single instance.
- `android:theme="@style/Theme.Rekodi.Transparent"`: Invisible Activity.

### Service declarations

**RecordingForegroundService**:
- `android:foregroundServiceType="mediaProjection"`: Declares that this service uses `MediaProjection`. Required on Android 14+ for the `mediaProjection` type.
- `android:exported="false"`: Not accessible from other apps.

**TapDetectionAccessibilityService**:
- `android:exported="true"`: Accessibility services must be exported to allow the system to bind to them.
- `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`: Only the system can bind to accessibility services.
- Intent filter for `android.accessibilityservice.AccessibilityService`: Registers this as an accessibility service the user can enable in Settings.

---

## Resources

### colors.xml (values/)

```xml
<color name="rekodi_amber">#E8A33D</color>
<color name="rekodi_charcoal">#1C1B1F</color>
<color name="rekodi_off_white">#FAF7F2</color>
<color name="rekodi_savannah_green">#3E7C59</color>
<color name="rekodi_red">#E53935</color>
```

These are the brand colors in XML format, accessible from XML layouts and drawables via `@color/rekodi_amber`. The Compose `Color.kt` file in `core-ui` defines the same values in Kotlin. Both must be kept in sync. The XML colors are used by:
- The splash screen display theme.
- The notification icon tint (if applicable).
- XML-based vector drawables.

### colors.xml (values-night/)

```xml
<color name="rekodi_amber">#FFD070</color>
<color name="rekodi_charcoal">#1C1B1F</color>
<color name="rekodi_off_white">#2C2B30</color>
<color name="rekodi_savannah_green">#7DD49A</color>
<color name="rekodi_red">#FFB4AB</color>
```

Dark mode color overrides. The `values-night` resource qualifier is used when the system is in dark mode. Amber is lighter (`#FFD070`), off-white becomes dark (`#2C2B30`), green and red become lighter/softer for better contrast on dark backgrounds. These are referenced by the splash screen and any XML-based UI components during dark mode.

### strings.xml

```xml
<resources>
    <string name="app_name">Rekodi</string>
    <string name="rekodi_tagline">Rekodi Kila Kitu</string>
    <string name="rekodi_publisher">by Camel Creatives</string>
</resources>
```

- `app_name`: The application display name. Appears in launcher, recent tasks, Settings > Apps.
- `rekodi_tagline`: Swahili for "Record Everything". Used in the About/Onboarding screens.
- `rekodi_publisher`: Attribution string for the developer.

### themes.xml

```xml
<style name="Theme.Rekodi" parent="android:Theme.Material.Light.NoActionBar">
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
</style>

<style name="Theme.Rekodi.Transparent" parent="Theme.Rekodi">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowContentOverlay">@null</item>
</style>
```

- **`Theme.Rekodi`**: The base theme for `MainActivity`. Inherits from `Material.Light.NoActionBar` (the AndroidX Material theme without a title bar). The transparent status/nav bar colors enable edge-to-edge display, working in conjunction with `enableEdgeToEdge()` in `MainActivity`.
- **`Theme.Rekodi.Transparent`**: Extends the base theme to make the window fully transparent. Used by `MediaProjectionTrampolineActivity` so the activity is invisible to the user.

### backup_rules.xml

```xml
<full-backup-content>
    <exclude domain="sharedpref" path="rekodi_settings.xml" />
</full-backup-content>
```

This file configures Android's auto-backup feature. By default, Android backs up app data to Google Drive. This rule excludes DataStore Preferences from backup. The rationale: DataStore files are stored as protocol buffers, not XML SharedPreferences. However, the exclusion path still references `sharedpref` and `rekodi_settings.xml` -- this is a legacy configuration that may need updating to properly exclude the DataStore `.preferences_pb` file. Currently, the user's settings are not backed up. The `RecordingEntity` data in Room is also not explicitly excluded, meaning it IS backed up (which is usually desirable).
