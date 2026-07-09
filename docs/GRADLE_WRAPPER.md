# Gradle Wrapper

## Purpose

The Gradle Wrapper is a mechanism for running Gradle builds without requiring a pre-installed Gradle distribution on the build machine. It consists of a small JAR file (gradle-wrapper.jar), a properties file (gradle-wrapper.properties), and platform-specific shell scripts (gradlew for POSIX, gradlew.bat for Windows). The wrapper ensures that all developers and CI systems use the exact same Gradle version, eliminating "works on my machine" build issues caused by Gradle version differences.

For the Rekodi project, the Gradle Wrapper is essential because:
1. It pins the Gradle version to 8.7 (as specified in `gradle-wrapper.properties`), which is the version the project's build scripts have been tested against.
2. It eliminates the need for each developer to install Gradle manually or manage multiple Gradle versions.
3. It enables CI/CD systems (like GitHub Actions) to build the project without pre-configuring a Gradle installation.
4. It provides a consistent entry point (`./gradlew`) that works identically across macOS, Linux, and Windows.

---

## Files

### gradlew (POSIX Shell Script)

The `gradlew` script is a POSIX-compliant shell script (133 lines) that performs the following functions:

1. **APP_HOME Resolution (lines 26-40):** Determines the application's home directory by resolving any symbolic links in the script's own path. This ensures the wrapper works correctly even when invoked through symlinks.

2. **CLASSPATH Setup (line 68):** Sets the classpath to point to `$APP_HOME/gradle/wrapper/gradle-wrapper.jar`. This JAR file contains the `GradleWrapperMain` class that handles downloading and executing the specified Gradle version.

3. **JVM Discovery (lines 71-92):** Locates the Java runtime:
   - First checks the `JAVA_HOME` environment variable and validates it points to a valid Java installation.
   - Falls back to `java` on the system PATH if `JAVA_HOME` is not set.
   - Provides clear error messages if Java cannot be found.

4. **OS-Specific Adjustments (lines 56-66):** Detects Cygwin, MSYS/MinGW, Darwin (macOS), and NonStop operating systems for proper path handling.

5. **Execution (lines 113-133):** Launches the JVM with the wrapper JAR, passing through:
   - `DEFAULT_JVM_OPTS` -- Default JVM options (can be set in `gradle.properties` or environment).
   - `JAVA_OPTS` -- User-specified JVM options.
   - `GRADLE_OPTS` -- Gradle-specific options.
   - All command-line arguments passed to `gradlew`.

### gradlew.bat (Windows Batch Script)

The Windows counterpart follows the same logic as `gradlew` but uses Windows batch syntax. Key differences:
- Uses `%APP_HOME%` (Windows variable syntax) instead of `$APP_HOME`.
- Uses `set` and `if` batch commands for flow control.
- Uses `%JAVA_HOME%` for Java path resolution.
- Invokes Java with `"%JAVACMD%"` syntax.

### gradle/wrapper/gradle-wrapper.properties

This file configures which Gradle distribution the wrapper downloads:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

Key properties:
- **distributionUrl:** The URL to download Gradle 8.7. The `https\://` escaping is standard properties file format for the colon. The `-bin` suffix means the binary-only distribution (no sources or documentation).
- **networkTimeout:** 10 seconds for network operations.
- **validateDistributionUrl:** When true, the wrapper validates the distribution URL before downloading (security measure against URL injection attacks).
- **distributionBase/distributionPath/zipStoreBase/zipStorePath:** Control where the downloaded Gradle distribution is cached locally. `GRADLE_USER_HOME` defaults to `~/.gradle` on Unix and `%USERPROFILE%\.gradle` on Windows.

### gradle-wrapper.jar

This is a binary JAR file approximately 60KB in size. It contains the `org.gradle.wrapper.GradleWrapperMain` class and its dependencies. The JAR is checked into version control (as is standard practice) so that the repository is self-contained -- anyone who clones it can build without any prior Gradle installation.

The wrapper JAR's responsibilities include:
1. Reading `gradle-wrapper.properties` to determine which Gradle version to use.
2. Checking the local Gradle cache (`~/.gradle/wrapper/dists/`) for the distribution.
3. Downloading the distribution from the specified URL if not cached.
4. Extracting the distribution.
5. Executing Gradle with the provided arguments.
6. Managing the download progress display.

---

## How the Wrapper Works (Execution Flow)

1. The user runs `./gradlew build` (or any Gradle task).
2. The shell script locates Java and launches the wrapper JAR.
3. The wrapper JAR reads `gradle-wrapper.properties` and determines that Gradle 8.7 is needed.
4. The wrapper checks `~/.gradle/wrapper/dists/gradle-8.7-bin/` for an existing installation.
5. If not found, it downloads `gradle-8.7-bin.zip` from `services.gradle.org`.
6. It extracts the ZIP to the wrapper cache.
7. It launches the Gradle 8.7 runtime, passing through the original command-line arguments (`build` in this case).
8. Gradle reads `settings.gradle.kts` and `build.gradle.kts` files and executes the requested tasks.

This flow means the first build is slightly slower (due to the download), but subsequent builds use the cached distribution.

---

## Setup Instructions

### Generating the gradle-wrapper.jar

The `gradle-wrapper.jar` is not a file you create manually. It is generated by Gradle's built-in `wrapper` task. If you need to regenerate it (e.g., after upgrading the Gradle version), follow these steps:

#### On a machine with Gradle installed:

```bash
# Navigate to the project root
cd /home/cameltech/Projects/rekodi

# Run the wrapper task with the desired Gradle version
gradle wrapper --gradle-version 8.7

# Or, to update an existing wrapper:
gradle wrapper --gradle-version 8.7 --update-distribution-url
```

This command will:
1. Download Gradle 8.7 (if not already cached).
2. Generate/update `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`.
3. Update `gradle/wrapper/gradle-wrapper.properties` with the specified version.

#### Without Gradle installed:

If no Gradle installation is available, you can:
1. Download the Gradle wrapper JAR from the official Gradle repository: `https://services.gradle.org/distributions/gradle-8.7-bin.zip`
2. Extract the ZIP.
3. Copy `gradle-wrapper.jar` from the extracted archive's `lib/` directory to your project's `gradle/wrapper/`.
4. Copy `gradlew` and `gradlew.bat` from the extracted archive to your project root.
5. Create `gradle/wrapper/gradle-wrapper.properties` with the content shown above.

### Verifying the Wrapper

After setup, verify the wrapper works:

```bash
./gradlew --version
```

This should display Gradle version 8.7 and the JVM information.

### Upgrading the Gradle Version

To upgrade to a newer Gradle version:

```bash
./gradlew wrapper --gradle-version 8.8
```

This updates the wrapper to download Gradle 8.8 on future builds. The `gradle-wrapper.properties` distribution URL is updated automatically.

---

## Project-Specific Configuration

For the Rekodi project, the Gradle wrapper is configured to use:
- **Gradle 8.7** -- A stable version compatible with Android Gradle Plugin (AGP) and Kotlin 2.0+.
- **Kotlin JVM Target 17** -- As defined in the build-logic convention plugins.
- **Compile SDK 35** -- Android 15 API level.
- **Minimum SDK 26** -- Android 8.0 (Oreo), the minimum supported version.

The build is structured as a multi-module Gradle project with convention plugins in `build-logic/` and feature modules under `feature/`. The wrapper ensures all eight modules (`:app`, `:core:core-ui`, `:core:core-data`, `:core:core-common`, `:feature:feature-recorder`, `:feature:feature-editor`, `:feature:feature-library`, `:feature:feature-settings`, `:feature:feature-onboarding`) are built with identical Gradle configuration.

---

## Best Practices

- Always commit the wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) to version control. They are small and essential for reproducible builds.
- Never commit the downloaded Gradle distribution or its extracted contents -- they are cached locally in `~/.gradle/wrapper/dists/` and should not be in version control.
- The `gradle-wrapper.properties` file is the only wrapper file that should be edited directly (to change the Gradle version). The scripts and JAR should be regenerated using the `wrapper` task.
- CI systems should run `./gradlew` (not `gradle`) to ensure they use the project's specified Gradle version.
- The `GRADLE_USER_HOME` directory can be cached in CI to avoid re-downloading Gradle on every build.
