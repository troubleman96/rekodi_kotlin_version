# core-common Module -- Shared Utilities

## Purpose

The `core-common` module is the foundational layer of the Rekodi application. It contains zero Android framework dependencies (with the exception of `Context` in the extensions file, which is an Android SDK class). Every other module -- `core-data`, `core-ui`, and all feature modules -- depends on `core-common`. This module exists to:

1. **Abstract platform coroutine dispatchers** so that tests can inject test dispatchers instead of depending on `Dispatchers.Main` (which requires the Android main loop instrumentation).
2. **Provide extension functions** on Android `Context` and Kotlin primitives (`Long`) that are used pervasively across the application.
3. **Define a sealed `Result` type** that carries both a success value and (on failure) a human-readable error message plus an optional `Throwable`. The built-in `kotlin.Result` is insufficient because it cannot carry a custom error message and its `Failure` type is opaque.

Because all other modules depend on `core-common`, any shared utility or abstraction that might be needed in two or more modules belongs here. Putting infrastructure in `core-common` avoids circular dependencies between feature modules.

---

## File: Dispatchers.kt

### Full source

```kotlin
package com.camelcreatives.rekodi.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface RekodiDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val computation: CoroutineDispatcher
}

class DefaultRekodiDispatchers : RekodiDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val computation: CoroutineDispatcher = Dispatchers.Default
}
```

### Line-by-line analysis

**Line 1**: Package declaration. Matches the module namespace `com.camelcreatives.rekodi.common` defined in `core-common/build.gradle.kts`.

**Lines 3-4**: Imports `CoroutineDispatcher` (the abstract base class) and `Dispatchers` (the standard implementation object). These are from `kotlinx-coroutines-core`, the only dependency of `core-common` (declared as `implementation(libs.coroutines.core)` in the build file).

**Lines 6-10**: `RekodiDispatchers` interface. Exposes three properties, each returning a `CoroutineDispatcher`:

- `main`: For UI-thread work. Maps to `Dispatchers.Main` in production. In unit tests, this is replaced with `Dispatchers.Unconfined` or `TestDispatchers` to avoid the `Main` dispatcher needing an Android Looper.
- `io`: For blocking I/O (file writes, network calls, database operations). Maps to `Dispatchers.IO` in production, which uses a thread pool that grows dynamically.
- `computation`: For CPU-intensive work. Maps to `Dispatchers.Default` in production, which uses a thread pool limited to the number of CPU cores.

The interface exists solely for test seam purposes. Without this abstraction, any class that calls `Dispatchers.IO` directly is permanently coupled to the real dispatcher. With the interface, Hilt provides an instance, and tests can provide a different implementation (e.g., `UnconfinedRekodiDispatchers` where all three properties return `Dispatchers.Unconfined`).

**Lines 12-16**: `DefaultRekodiDispatchers` is the production implementation. It is a simple value object with no logic. Each property delegates to the corresponding constant on the `Dispatchers` object.

- `Dispatchers.Main` (line 13): On Android, this dispatcher posts to the main `Looper`. It requires that `kotlinx-coroutines-android` is on the classpath (which the `feature-recorder` module provides via `libs.coroutines.android`).
- `Dispatchers.IO` (line 14): Designed for offloading blocking I/O. It shares a thread pool with `Default` but can expand the pool beyond the CPU core count because I/O operations are assumed to be blocking.
- `Dispatchers.Default` (line 15): Bounded thread pool sized to `max(2, availableProcessors)`. Used for pure computation without blocking.

### Why not use `Dispatchers` directly?

Consider a `RecordingRepository` that launches a coroutine with `Dispatchers.IO`. In a Robolectric or instrumented test, this works fine. But in a pure unit test (JVM, no Android shadow), `Dispatchers.Main` will throw an exception because there is no main loop. Worse, testing a coroutine that uses `Dispatchers.IO` means the test runs on a real thread pool, introducing flakiness from timing. By injecting `RekodiDispatchers`, a test can supply:

```kotlin
object TestDispatchers : RekodiDispatchers {
    override val main = UnconfinedTestDispatcher()
    override val io = StandardTestDispatcher()
    override val computation = StandardTestDispatcher()
}
```

This makes all coroutines run deterministically on the test's virtual time.

---

## File: Extensions.kt

### Full source

```kotlin
package com.camelcreatives.rekodi.common

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.getRecordingOutputFile(): File {
    val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File(dir, "Rekodi_$timestamp.mp4")
}

fun Context.getAudioOutputFile(): File {
    val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File(dir, "Rekodi_Audio_$timestamp.m4a")
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024
    val mb = kb / 1024
    val gb = mb / 1024
    return when {
        gb > 0 -> String.format("%.1f GB", bytes / (1024f * 1024f * 1024f))
        mb > 0 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        kb > 0 -> String.format("%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
```

### Line-by-line analysis

**Line 1**: Package declaration.

**Lines 3-11**: Imports.

- `Context`: Android abstraction for application environment. Required for `getExternalFilesDir()`.
- `Uri`, `Environment`, `MediaStore`: Imported but not used in this version of the file. They are available for future extension functions that register files with `MediaStore`.
- `File`: Standard Java file representation.
- `SimpleDateFormat`, `Date`, `Locale`: Java time formatting utilities. `SimpleDateFormat` is used because Kotlin's `java.time` package requires API 26+, and while `minSdk = 26` is set, `SimpleDateFormat` is simpler for filename timestamps and avoids desugaring complications.

**Lines 12-16**: `Context.getRecordingOutputFile()`.

- Calls `getExternalFilesDir(Environment.DIRECTORY_MOVIES)`. This returns a `File` pointing to the app-specific external storage directory under `Movies/`. On Android 10+, scoped storage means this directory is accessible without `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE` permissions. Files here are automatically cleaned up when the app is uninstalled.
- Creates a timestamp string using `SimpleDateFormat`. The pattern `"yyyyMMdd_HHmmss"` produces a lexicographically sortable string like `"20260709_143052"`. This is safe for filenames because it contains no forbidden characters.
- Constructs a `File` with the name `"Rekodi_<timestamp>.mp4"`. The `Rekodi_` prefix provides namespace isolation in the directory.
- Returns the `File` object. The file is not created at this point; it is merely a path descriptor.

**Lines 18-22**: `Context.getAudioOutputFile()`.

- Identical structure but targets `Environment.DIRECTORY_MUSIC` and produces an `.m4a` extension (AAC container, the audio codec used in recordings).
- The name includes `"_Audio_"` to distinguish audio-only recordings from video recordings.

**Lines 24-30**: `formatDuration(seconds: Long)`.

- Takes a duration in whole seconds (not milliseconds). Callers convert from milliseconds to seconds before calling.
- Computes hours, minutes, and seconds using integer division and modulo.
- If hours > 0, formats as `"H:MM:SS"` using `String.format`. The `%d` for hours is unpadded; `%02d` for minutes and seconds ensures two digits (e.g., `"1:05:03"` not `"1:5:3"`).
- If hours == 0, formats as `"MM:SS"` only (e.g., `"05:03"`).
- This function is used in `RecordingTimer` composable, the foreground notification timer text, and the floating bubble timer display.

**Lines 32-42**: `formatFileSize(bytes: Long)`.

- Computes kilobyte, megabyte, and gigabyte values using integer division. Note that `kb = bytes / 1024` loses precision (it is floor division). The values are only used as guards to select the appropriate unit.
- The `when` block checks units from largest (GB) to smallest (B):
  - If `gb > 0`, formats as `"X.X GB"` using float division: `bytes / (1024f * 1024f * 1024f)` produces a float with one decimal place.
  - If `mb > 0`, formats as `"X.X MB"`.
  - If `kb > 0`, formats as `"X.X KB"`.
  - Otherwise, formats as `"X B"` (integer, no decimal).
- Uses `String.format("%.1f", ...)` for one decimal place of precision in KB/MB/GB ranges.
- Used in the library UI for each recording's file size display.

---

## File: Result.kt

### Full source

```kotlin
package com.camelcreatives.rekodi.common

sealed class RekodiResult<out T> {
    data class Success<T>(val data: T) : RekodiResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : RekodiResult<Nothing>()
}

inline fun <T, R> RekodiResult<T>.map(transform: (T) -> R): RekodiResult<R> = when (this) {
    is RekodiResult.Success -> RekodiResult.Success(transform(data))
    is RekodiResult.Error -> this
}

inline fun <T> RekodiResult<T>.onSuccess(action: (T) -> Unit): RekodiResult<T> {
    if (this is RekodiResult.Success) action(data)
    return this
}

inline fun <T> RekodiResult<T>.onError(action: (String, Throwable?) -> Unit): RekodiResult<T> {
    if (this is RekodiResult.Error) action(message, throwable)
    return this
}
```

### Line-by-line analysis

**Lines 1**: Package declaration.

**Line 3**: Declares `RekodiResult<T>` as a sealed class with `out T` covariance. `sealed` means all subclasses are known at compile time, enabling exhaustive `when` expressions. `out` means `RekodiResult<List<Int>>` is a subtype of `RekodiResult<Any>`, which is safe because `T` only appears in `out` positions (the `Success` data class exposes `T` as a read-only val).

**Line 4**: `Success<T>` data class. The `data` keyword gives us `equals()`, `hashCode()`, `toString()`, and `copy()`. The generic parameter `T` is the type of the success value. `Success<RekodiResult.Error>` does not make sense semantically, but the type system allows it.

**Line 5**: `Error` data class. It extends `RekodiResult<Nothing>` -- `Nothing` is the bottom type in Kotlin, meaning an `Error` instance can be used wherever `RekodiResult<T>` is expected (due to covariance). It carries:
- `message`: A human-readable error description (e.g., `"Trim failed: Invalid offset"`). This is the primary error communication mechanism.
- `throwable`: `null` by default. When present, it captures the originating Java/Kotlin exception for stack trace debugging. It is nullable because not all errors originate from exceptions (e.g., validation failures).

**Line 8**: `map` extension function. `inline` is used to avoid lambda allocation overhead. The generic parameters are `<T, R>` where `T` is the input type and `R` is the output type. If the receiver is `Success`, the transform lambda is applied to `data` and the result is wrapped in a new `Success`. If the receiver is `Error`, the error is propagated unchanged (errors cannot be ignored through `map`). This is a standard monadic `map` operation.

**Lines 13-16**: `onSuccess` extension. Executes the `action` lambda only if the result is `Success`, passing the success value. Returns `this` (the original `RekodiResult`) so calls can be chained: `result.onSuccess { ... }.onError { ... }`.

**Lines 18-21**: `onError` extension. Executes the `action` lambda only if the result is `Error`, passing `message` and `throwable`. Returns `this` for chaining.

### Why not `kotlin.Result`?

Kotlin's built-in `kotlin.Result<T>` has several limitations that make it inappropriate for this application:

1. `kotlin.Result` is not a sealed class. It is a value class with two internal representations (`Success` and `Failure`). The `Failure` variant wraps a `Throwable`, which means you must throw exceptions to represent errors. `RekodiResult.Error` stores a string message directly, avoiding exception overhead for predictable failures.
2. `kotlin.Result` is designed for use as a return type in Kotlin-Java interop scenarios (especially with reflection). Its API surface is intentionally minimal.
3. `kotlin.Result` exposes `exceptionOrNull()` which only returns a `Throwable`, not a message. To get a user-presentable error, you must extract `.message` from the throwable, which may be null.
4. `kotlin.Result` does not have extension-friendly API; `map` and `onSuccess`/`onError` require manual implementation anyway.

`RekodiResult` is used throughout the `AudioEngine` (file I/O operations), the `RecordingRepository` (future use for error handling), and any other place where an operation can fail predictably.

---

## Dependencies

The `core-common` module's `build.gradle.kts` declares a single dependency:

```kotlin
dependencies {
    implementation(libs.coroutines.core)
}
```

This is `org.jetbrains.kotlinx:kotlinx-coroutines-core`. It provides `CoroutineDispatcher`, `Dispatchers`, and the coroutine primitives used in `Dispatchers.kt`. Notably, `kotlinx-coroutines-android` is NOT a dependency here -- that is only required by modules that use `Dispatchers.Main` at runtime (e.g., `feature-recorder` which uses coroutines in `RecordingForegroundService`). The `core-common` module defines the interface, but the `DefaultRekodiDispatchers` implementation references `Dispatchers.Main` which is resolved at runtime via the Android classpath.

The module uses the convention plugin `camelcreatives.android.library` (defined in `build-logic/AndroidLibraryConventionPlugin.kt`), which:
- Applies `com.android.library` and `org.jetbrains.kotlin.android`.
- Sets `compileSdk = 35` and `minSdk = 26`.
- Sets Java 17 compatibility.
- Does NOT enable Compose (no `compose` flag, no Compose dependencies).

This is correct because `core-common` has no UI responsibilities.
