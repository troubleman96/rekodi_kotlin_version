# Build System Deep Dive

This document is a line-by-line, decision-level explanation of Rekodi's Gradle build system. It covers every configuration file, every plugin, every dependency, and every property that governs how the project compiles, ships, and is tested. Understanding this document is essential for any developer making changes to the build configuration or adding new modules.

---

## 1. Root `build.gradle.kts`

**Location:** `/home/cameltech/Projects/rekodi/build.gradle.kts`

**Full content:**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

### Line-by-line analysis

**`plugins { ... }` block:** This is the top-level plugin declaration block. In Gradle, plugins declared in the root project's `plugins` block are resolved (downloaded from repositories) and made available on the classpath for all subprojects. However, every plugin here has `apply false`, meaning the plugin is resolved but **not applied** to the root project.

**Why `apply false`?** If a plugin were applied to the root project, it would configure the root project as if it were an Android application or library, which it is not (the root project is just a container for submodules). The convention is to resolve all required plugins at the root level so that submodules (and convention plugins in `build-logic`) can access them without needing their own `buildscript { dependencies { classpath(...) } }` block.

Each line uses the version catalog type-safe accessor `libs.plugins.<name>` which is a generated accessor from the version catalog (see section 4). The actual versions are defined in `gradle/libs.versions.toml` under the `[plugins]` section.

**Plugin details:**

| Catalog key | Plugin ID | Version | Why needed |
|---|---|---|---|
| `android.application` | `com.android.application` | 8.7.3 | The Android Gradle Plugin for application modules. Produces APK/AAB output. |
| `android.library` | `com.android.library` | 8.7.3 | The Android Gradle Plugin for library modules. Produces AAR output. |
| `kotlin.android` | `org.jetbrains.kotlin.android` | 2.1.0 | Enables Kotlin compilation for Android projects. Configures Kotlin source sets, adds `kotlin-stdlib`. |
| `kotlin.compose` | `org.jetbrains.kotlin.plugin.compose` | 2.1.0 | The Kotlin Compose compiler plugin (introduced in Kotlin 2.0). Replaces the old `composeOptions { kotlinCompilerExtensionVersion }` block. Must be applied to any module that uses Jetpack Compose. |
| `hilt.android` | `com.google.dagger.hilt.android` | 2.53.1 | Hilt DI plugin. Processes `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel` annotations and generates Dagger components. |
| `ksp` | `com.google.devtools.ksp` | 2.1.0-1.0.29 | Kotlin Symbol Processing plugin. Required by Hilt (for `hilt-compiler`) and Room (for `room-compiler`) to run annotation processing without kapt. KSP is significantly faster than kapt. |

### How convention plugins are discovered

The convention plugins are NOT declared in the root `plugins` block. Instead, they come from the `build-logic` included build (see section 2, `settings.gradle.kts`). The `includeBuild("build-logic")` directive makes all plugins registered in `build-logic/build.gradle.kts` under `gradlePlugin { plugins { ... } }` available by their `id` string (e.g., `"camelcreatives.android.application.compose"`). Individual submodules then apply them via:

```kotlin
plugins {
    id("camelcreatives.android.application.compose")
}
```

This is the standard "convention plugins via included build" pattern recommended by the Android Gradle Plugin team. It allows the convention plugins to be written in Kotlin, use the Gradle API directly, and be versioned together with the project.

---

## 2. `settings.gradle.kts`

**Location:** `/home/cameltech/Projects/rekodi/settings.gradle.kts`

**Full content:**

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "rekodi"
include(":app")
include(":core:core-ui")
include(":core:core-data")
include(":core:core-common")
include(":feature:feature-recorder")
include(":feature:feature-editor")
include(":feature:feature-library")
include(":feature:feature-settings")
include(":feature:feature-onboarding")
```

### `pluginManagement` block

**`includeBuild("build-logic")`:** This is the critical line that includes the `build-logic` directory as a "composite build" (also called an "included build"). This makes all plugins and libraries defined in `build-logic` available to all modules in the main build. The included build has its own `settings.gradle.kts`, `build.gradle.kts`, and can even have its own version catalog (which `build-logic` does at `build-logic/gradle/libs.versions.toml`).

**`repositories { google(); mavenCentral(); gradlePluginPortal() }`:** These are the repositories where Gradle plugin artifacts are resolved. `gradlePluginPortal()` is the default Gradle Plugin Portal (https://plugins.gradle.org/). `google()` is required for AGP. `mavenCentral()` hosts additional plugin artifacts. This block only affects plugin resolution (the `plugins` block), not dependency resolution.

### `dependencyResolutionManagement` block

**`RepositoriesMode.FAIL_ON_PROJECT_REPOS`:** This setting prevents individual modules from declaring their own `repositories { ... }` blocks. All repository configuration must happen at the settings level. This enforces consistency (all modules use the same repositories) and eliminates a common source of hard-to-debug build failures where one module adds a repository that another module accidentally depends on.

**`repositories { google(); mavenCentral(); maven { url = uri("https://jitpack.io") } }`:** The three repositories for dependency resolution:
- `google()` -- AndroidX, Material, Compose, Room, DataStore, Navigation, Lifecycle libraries.
- `mavenCentral()` -- Kotlin libraries, Coroutines, Dagger/Hilt, Coil, Media3.
- `jitpack.io` -- A Maven repository that builds GitHub projects on-the-fly. Currently declared but not used by any dependency. May be needed for future dependencies.

### `include(":path")` declarations

Each `include(...)` call registers a module in the Gradle project graph. Module names follow the filesystem path format with colons as separators:
- `:app` corresponds to `app/`
- `:core:core-ui` corresponds to `core/core-ui/`
- `:feature:feature-recorder` corresponds to `feature/feature-recorder/`

All modules are included at the settings level because Gradle needs to know the full module graph before executing any task. If a module is not included here, it will not be compiled, even if other modules depend on it.

---

## 3. `gradle.properties`

**Location:** `/home/cameltech/Projects/rekodi/gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.configuration-cache=true
```

### `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`
Sets the maximum heap size for the Gradle daemon JVM to 2048 MB (2 GB). This is important for multi-module Android projects because Gradle and the Android toolchain are memory-intensive. The `-Dfile.encoding=UTF-8` ensures consistent file encoding across platforms, preventing issues with non-ASCII characters in source files.

### `android.useAndroidX=true`
Tells the Android plugin that the project uses AndroidX (the modern Android support library namespace) rather than the deprecated `android.support.*` libraries. This is required because all dependencies (Compose, Room, etc.) are AndroidX-based.

### `kotlin.code.style=official`
Uses the official Kotlin coding style as defined by JetBrains. This affects how the Kotlin compiler handles things like spacing around colons and semicolons.

### `android.nonTransitiveRClass=true`
When true (the default for AGP 8+), each module gets its own `R` class with only its own resources. When false, the `R` class includes resources from all transitive dependencies. Non-transitive R classes improve compilation performance and reduce the risk of resource ID conflicts. This is the recommended setting for multi-module projects.

### `org.gradle.configuration-cache=true`
Enables Gradle's **configuration cache** (stable since Gradle 8.1). This caches the result of the configuration phase (the execution of all `build.gradle.kts` files) and reuses it on subsequent builds when nothing has changed. It dramatically reduces build times for the "configure" part of the build, making iterative development faster. However, it can cause issues if plugins or build scripts use non-serializable objects or rely on fresh state each build. If build problems arise, this is the first property to try disabling.

---

## 4. Version Catalog (`gradle/libs.versions.toml`)

**Location:** `/home/cameltech/Projects/rekodi/gradle/libs.versions.toml`

The version catalog follows the TOML format defined by Gradle. It has three main sections: `[versions]`, `[libraries]`, and `[plugins]`.

### `[versions]`

Defines named version constants that can be referenced elsewhere in the catalog:

| Key | Version | Notes |
|---|---|---|
| `agp` | 8.7.3 | Latest stable AGP as of the catalog's creation. Supports Gradle 8.7+ |
| `kotlin` | 2.1.0 | Kotlin 2.1.0 is a feature release with Kotlin 2.0+ compiler compatibility |
| `ksp` | 2.1.0-1.0.29 | Must match the Kotlin version (2.1.0) followed by the KSP release (1.0.29) |
| `hilt` | 2.53.1 | Dagger Hilt. Compatible with KSP. |
| `compose-bom` | 2024.12.01 | Compose Bill of Materials. This single version aligns all Compose libraries to compatible versions. |
| `activity-compose` | 1.9.3 | Activity + Compose integration |
| `navigation-compose` | 2.8.5 | Compose Navigation |
| `lifecycle` | 2.8.7 | Lifecycle libraries |
| `coroutines` | 1.9.0 | Kotlin Coroutines |
| `room` | 2.6.1 | Room database |
| `datastore` | 1.1.1 | Jetpack DataStore |
| `coil` | 2.7.0 | Coil image loading |
| `medialib` | 1.1.0 | Reserved, currently unused |

### `[libraries]`

Each entry has a key, a Maven `group`, `name`, and optionally a `version` or `version.ref` (which references a version from `[versions]`). Libraries without explicit versions get their version from the BOM.

**AndroidX Core:**
- `androidx-core-ktx` -- Kotlin extensions for Android core APIs. Version 1.15.0.
- `androidx-core-splashscreen` -- Android 12+ SplashScreen API backward-compatible to API 23. Version 1.0.1.

**Compose (all versioned by BOM `2024.12.01`):**
- `compose-bom` -- The Bill of Materials itself.
- `compose-ui` -- Compose UI primitives (Modifier, Layout, Drawing).
- `compose-ui-graphics` -- Compose graphics layer.
- `compose-ui-tooling-preview` -- `@Preview` annotation support.
- `compose-ui-tooling` -- Layout inspector, recomposition counting (debug only).
- `compose-ui-test-manifest` -- Test manifest for Compose UI tests (debug only).
- `compose-ui-test-junit4` -- Compose UI testing with JUnit4.
- `compose-material3` -- Material 3 component library.
- `compose-material-icons-extended` -- Extended Material Icons set (thousands of icons).
- `compose-animation` -- Compose animation APIs.
- `compose-foundation` -- Compose foundation (gestures, layouts, text).

**Activity:**
- `activity-compose` -- `ComponentActivity.setContent {}` and `ActivityResultContracts` integration.

**Navigation:**
- `navigation-compose` -- Complete navigation solution for Compose with type-safe arguments.

**Lifecycle:**
- `lifecycle-runtime-compose` -- Lifecycle-aware Compose utilities.
- `lifecycle-viewmodel-compose` -- `viewModel()` composable, `collectAsStateWithLifecycle()`.
- `lifecycle-service` -- Lifecycle-aware Android Services.

**Hilt:**
- `hilt-android` -- Hilt runtime library (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Inject`, `@Module`, `@Provides`).
- `hilt-compiler` -- Hilt annotation processor (KSP). Generates Dagger components at compile time.
- `hilt-navigation-compose` -- Hilt integration with Compose Navigation (`hiltViewModel()`).

**Coroutines:**
- `coroutines-core` -- Kotlin coroutines primitives (`launch`, `async`, `Flow`, `Channel`).
- `coroutines-android` -- Android-specific dispatcher (`Dispatchers.Main`).

**Room:**
- `room-runtime` -- Room runtime library (`@Database`, `@Dao`, `@Entity`).
- `room-ktx` -- Room coroutine/Flow extensions (`Flow` DAO return types, suspend queries).
- `room-compiler` -- Room annotation processor (KSP). Generates DAO implementations.

**DataStore:**
- `datastore-preferences` -- Preferences DataStore for key-value settings storage.

**Coil:**
- `coil-compose` -- Compose integration for Coil image loader (`AsyncImage` composable).

**Media:**
- `media3-exoplayer` -- ExoPlayer media playback engine (audio/video).
- `media3-ui` -- Player UI components.

**Testing:**
- `junit` -- JUnit 4 testing framework.
- `turbine` -- Flow testing library by Cash App (`test { ... }` blocks for Flow assertions).

### `[plugins]`

Defines plugin references with their IDs and versions. These are used by the root `build.gradle.kts` `plugins { alias(libs.plugins.X) }` block.

| Key | Plugin ID | Version |
|---|---|---|
| `android-application` | `com.android.application` | 8.7.3 |
| `android-library` | `com.android.library` | 8.7.3 |
| `kotlin-android` | `org.jetbrains.kotlin.android` | 2.1.0 |
| `kotlin-compose` | `org.jetbrains.kotlin.plugin.compose` | 2.1.0 |
| `hilt-android` | `com.google.dagger.hilt.android` | 2.53.1 |
| `ksp` | `com.google.devtools.ksp` | 2.1.0-1.0.29 |

### build-logic's separate version catalog

**Location:** `build-logic/gradle/libs.versions.toml`

This catalog exists because `build-logic` is an **independent included build** with its own classloader. It cannot access the main project's version catalog. It must declare its own dependencies to compile the convention plugins. It mirrors the same versions (AGP 8.7.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29, Hilt 2.53.1, Compose BOM 2024.12.01) for the Gradle plugin artifacts:

- `android-gradle-plugin` -- `com.android.tools.build:gradle:8.7.3` (the AGP itself, not the application/library plugin).
- `kotlin-gradle-plugin` -- `org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0` (the Kotlin Gradle plugin artifact).
- `compose-gradle-plugin` -- `org.jetbrains.kotlin:kotlin-compose-compiler-plugin:2.1.0` (the Compose compiler plugin for Kotlin 2.0+).
- `hilt-gradle-plugin` -- `com.google.dagger:hilt-android-gradle-plugin:2.53.1`.
- `ksp-gradle-plugin` -- `com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.0-1.0.29`.

These are declared as `compileOnly` dependencies in `build-logic/build.gradle.kts` because the convention plugins use the Gradle plugin APIs at compile time, but the actual plugins are resolved and applied at runtime in the main build.

---

## 5. `build-logic` Module

### Why convention plugins exist

In a multi-module project without convention plugins, every module's `build.gradle.kts` would repeat the same configuration:

```kotlin
android {
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    // ...
}
```

This violates the DRY (Don't Repeat Yourself) principle, creates merge conflicts, and makes it easy to accidentally use different SDK versions or library versions across modules. Convention plugins solve this by encapsulating all standard configuration into reusable Kotlin classes that apply the same configuration to every module that applies the plugin.

**Each convention plugin:**
1. Applies one or more Gradle plugins (e.g., `com.android.library`, `org.jetbrains.kotlin.android`).
2. Configures the Android extension with SDK versions, Java compatibility, build features.
3. Adds common dependencies to the module's dependency graph.
4. Configures Kotlin compiler options.
5. Optionally adds inter-module dependencies (e.g., feature modules automatically depend on `core-ui` and `core-common`).

### Plugin registration (`build-logic/build.gradle.kts`)

The `gradlePlugin { plugins { ... } }` block registers each convention plugin with its `id` and `implementationClass`. The `id` is what modules use in their `plugins { id("...") }` blocks. The `implementationClass` must be the fully qualified name of the `Plugin<Project>` implementation.

All five plugins use `compileOnly` dependencies on the Android/Kotlin/Hilt/KSP Gradle plugin artifacts because they call the plugin APIs (e.g., `com.android.build.api.dsl.ApplicationExtension`) during plugin application.

### Plugin 1: `camelcreatives.android.application.compose`

**Class:** `AndroidApplicationComposeConventionPlugin` at `build-logic/src/main/kotlin/com/camelcreatives/buildlogic/AndroidApplicationComposeConventionPlugin.kt`

**What it does:**

1. Applies three plugins in `with(pluginManager) { ... }`:
   - `com.android.application` -- makes the module an Android app (produces APK).
   - `org.jetbrains.kotlin.android` -- enables Kotlin compilation.
   - `org.jetbrains.kotlin.plugin.compose` -- enables the Compose compiler.

2. Configures the `ApplicationExtension`:
   - `compileSdk = 35` -- compile against Android 15 SDK.
   - `minSdk = 26` -- minimum Android 8.0 (Oreo).
   - `targetSdk = 35` -- target Android 15.
   - `sourceCompatibility = VERSION_17` and `targetCompatibility = VERSION_17` -- Java 17 bytecode.
   - `buildFeatures { compose = true }` -- enables Compose in the module.

3. Configures Kotlin compilation:
   - `kotlinOptions { jvmTarget = "17" }` -- generates Kotlin bytecode targeting JVM 17.

4. Adds all Compose dependencies automatically:
   - Compose BOM (platform dependency for version alignment).
   - UI, UI Graphics, UI Tooling Preview (implementation scope).
   - Material 3, Material Icons Extended, Animation, Foundation.
   - Activity Compose (for `ComponentActivity.setContent`).
   - Lifecycle Runtime Compose and ViewModel Compose.
   - UI Tooling and UI Test Manifest (debug scope only).

**Applied by:** `:app` module.

### Plugin 2: `camelcreatives.android.library.compose`

**Class:** `AndroidLibraryComposeConventionPlugin`

**What it does:** Same pattern as the application variant but:
- Applies `com.android.library` instead of `com.android.application`.
- Does NOT set `targetSdk` (not applicable for library modules).
- Adds a smaller set of Compose dependencies: BOM, UI, Material 3, UI Tooling Preview, Foundation, and debug UI Tooling.
- Does NOT add Activity Compose or Lifecycle dependencies (not needed for library modules).

**Applied by:** `:core:core-ui` module.

### Plugin 3: `camelcreatives.android.library`

**Class:** `AndroidLibraryConventionPlugin`

**What it does:** The simplest plugin. Applies `com.android.library` and `org.jetbrains.kotlin.android`, sets SDK compatibility (compileSdk=35, minSdk=26, Java 17), but does NOT enable Compose or add any Compose dependencies.

**Applied by:** `:core:core-common` module.

### Plugin 4: `camelcreatives.android.feature`

**Class:** `AndroidFeatureConventionPlugin`

**What it does:** The feature module plugin combines library + Compose + automatic inter-module dependencies:

1. Applies `com.android.library`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`.
2. Configures Android library extension (compileSdk=35, minSdk=26, Java 17, compose=true).
3. Adds dependencies:
   - `project(":core:core-ui")` -- every feature module gets the shared UI components and theme.
   - `project(":core:core-common")` -- every feature module gets dispatchers, extensions, result wrapper.
   - Full Compose stack (BOM, UI, Material 3, Foundation, Animation, UI Tooling Preview, Activity Compose, Lifecycle Runtime+ViewModel).
   - `navigation-compose` -- navigation support for feature-level inner navigation.
   - `hilt-navigation-compose` -- Hilt integration with navigation.

**Applied by:** All five `:feature:feature-*` modules.

### Plugin 5: `camelcreatives.android.hilt`

**Class:** `AndroidHiltConventionPlugin`

**What it does:** A focused plugin for Hilt dependency injection setup:

1. Applies `com.google.dagger.hilt.android` and `com.google.devtools.ksp`.
2. Adds `hilt-android` library to `implementation` scope.
3. Adds `hilt-compiler` to `ksp` scope (annotation processing via KSP, not kapt).

**Applied by:** `:app`, `:core:core-data`, `:feature:feature-recorder`, `:feature:feature-editor`, `:feature:feature-library`, `:feature:feature-settings`, `:feature:feature-onboarding`.

---

## 6. `app/build.gradle.kts`

**Location:** `/home/cameltech/Projects/rekodi/app/build.gradle.kts`

```kotlin
plugins {
    id("camelcreatives.android.application.compose")
    id("camelcreatives.android.hilt")
}

android {
    namespace = "com.camelcreatives.rekodi"

    defaultConfig {
        applicationId = "com.camelcreatives.rekodi"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-common"))
    implementation(project(":feature:feature-recorder"))
    implementation(project(":feature:feature-editor"))
    implementation(project(":feature:feature-library"))
    implementation(project(":feature:feature-settings"))
    implementation(project(":feature:feature-onboarding"))

    implementation(libs.navigation.compose))
    implementation(libs.hilt.navigation.compose))
    implementation(libs.androidx.core.splashscreen))
    implementation(libs.lifecycle.service))

    testImplementation(libs.junit))
}
```

**namespace:** The package namespace for R class generation and manifest merging. Must match the `package` attribute in the merged manifest.

**defaultConfig:**
- `applicationId = "com.camelcreatives.rekodi"` -- The unique Android package identifier for the Play Store. Must never change after the first release.
- `versionCode = 1` -- Monotonically increasing integer for version tracking. Incremented for each release.
- `versionName = "1.0.0"` -- Human-readable version string shown to users.
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` -- Test runner for instrumentation tests (not yet used but configured for future use).

**buildTypes:**
- `release` block enables `isMinifyEnabled = true` (R8 bytecode shrinking) and `isShrinkResources = true` (removes unused resources). Both significantly reduce APK size.
- `proguardFiles(...)` includes the Android default optimization rules (`proguard-android-optimize.txt` from the SDK) plus the project-specific `proguard-rules.pro`.
- There is no `debug` block (uses defaults: no minification, debuggable).

**packaging:**
- `excludes += "/META-INF/{AL2.0,LGPL2.1}"` -- Excludes license files from merged JARs to avoid duplicate file errors during APK packaging. AL2.0 is the Apache License 2.0 meta-inf file; LGPL2.1 is the Lesser GPL meta-inf file.

**dependencies:** The app module assembles the entire application by depending on all core modules and all feature modules. It also adds the root-level dependencies that are not provided by convention plugins:
- `navigation-compose` and `hilt-navigation-compose` for the navigation graph in `RekodiNavHost`.
- `core-splashscreen` for the Android 12+ splash screen API.
- `lifecycle-service` for lifecycle-aware interaction with `RecordingForegroundService`.

---

## 7. ProGuard Rules

**Location:** `/home/cameltech/Projects/rekodi/app/proguard-rules.pro`

```
# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room entities
-keep class com.camelcreatives.rekodi.data.local.entity.** { *; }

# Keep RecordingState for process death recovery
-keep class com.camelcreatives.rekodi.recorder.service.RecordingState { *; }

# MediaProjection
-keep class android.media.projection.** { *; }
```

**Why each rule exists:**

**`-keep class dagger.hilt.** { *; }`:** Hilt generates classes at compile time using KSP. By default, R8 can obfuscate or remove these generated classes because they have no direct references in application code (they are instantiated by Dagger's reflection-free DI mechanism). Keeping all Dagger/Hilt classes prevents runtime `ClassNotFoundException` errors.

**`-keep class javax.inject.** { *; }:** `javax.inject.Inject`, `javax.inject.Singleton`, `javax.inject.Provider` are used by Hilt. These annotations must survive obfuscation because Hilt's generated code references them.

**`-keep class * extends ...FragmentContextWrapper { *; }`:** Hilt generates Fragment context wrapper classes. Without this rule, R8 may remove methods that Hilt's runtime reflection expects to exist, causing crashes in Hilt-injected Fragments (if used in the future).

**`-keep class com.camelcreatives.rekodi.data.local.entity.** { *; }`:** Room entities are data classes with fields accessed by Room's generated DAO implementations via reflection (for `@Query` results mapping). If R8 renames the fields, Room cannot map database columns to Kotlin properties, causing runtime crashes.

**`-keep class ...RecordingState { *; }`:** `RecordingState` (the enum, currently in a file named `RecordingModels.kt` in `feature-recorder`) is serialized/deserialized for process death recovery. R8 must not rename its enum constants because the crash handler references them by name.

**`-keep class android.media.projection.** { *; }`:** `MediaProjection`, `MediaProjectionManager`, and related classes are obtained via runtime system services. R8 may incorrectly strip methods that are only called via reflection or system service proxies.

---

## 8. CI/CD

**Location:** `/home/cameltech/Projects/rekodi/.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
      - name: Build with Gradle
        run: ./gradlew assembleDebug
      - name: Run tests
        run: ./gradlew testDebugUnitTest
```

**Trigger conditions:**
- `push` to `main` -- any direct commit.
- `pull_request` targeting `main` -- any PR opened or updated.

**Job steps:**

1. **`actions/checkout@v4`** -- Checks out the repository code.

2. **`actions/setup-java@v4` with `temurin` distribution** -- Installs JDK 17 from Eclipse Temurin (the standard OpenJDK build). Version 17 is required because AGP 8.7+ and Kotlin 2.1.0 require JDK 17+.

3. **`gradle/actions/setup-gradle@v3`** -- Configures the Gradle wrapper, sets up a Gradle cache for dependencies, and caches the Gradle distribution itself. This step significantly speeds up subsequent builds by avoiding re-downloading dependencies.

4. **`./gradlew assembleDebug`** -- Compiles the debug variant of the app. This includes `compileDebugKotlin`, `compileDebugJavaWithJavac`, `mergeDebugResources`, `dexBuilderDebug`, `packageDebug` tasks for all modules. The `assembleDebug` task was chosen (rather than `assembleRelease`) because it does not require a signing key, and the CI does not have access to release keystores.

5. **`./gradlew testDebugUnitTest`** -- Runs all `src/test/` unit tests for all modules (currently only the `:app` module has a JUnit dependency, but the task runs over all modules). This ensures that any code changes do not break existing unit tests.

### What is NOT in CI

The CI currently does not include:
- **Lint checks** (`./gradlew lint`): No static analysis for XML or Kotlin style issues.
- **Detekt or ktlint**: No Kotlin code style enforcement.
- **Release builds**: The CI does not produce a signed APK/AAB because that requires secure keystore management.
- **Instrumentation tests** (`./gradlew connectedDebugAndroidTest`): No emulator/device tests because GitHub Actions does not provide Android emulator hardware by default (though it can be configured).
- **Dependency analysis**: No check for unused dependencies or version conflicts.

These can be added progressively as the project matures. The current CI provides basic compile and unit test validation for every commit and PR.
