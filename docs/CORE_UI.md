# core-ui Module -- Design System

## Purpose

The `core-ui` module contains the entire Rekodi design system: brand color definitions, Material 3 color schemes for light and dark modes, typography specification, the theme composable, and reusable UI components used across feature modules. Other modules import this module to use the theme, colors, and shared composables.

The module depends on `core-common` and standard Jetpack Compose libraries. It does NOT depend on `core-data` or any feature module, as that would create a circular dependency (feature modules depend on `core-ui`, so `core-ui` cannot depend on features).

---

## File: Color.kt

### Full source

```kotlin
package com.camelcreatives.rekodi.ui.theme

import androidx.compose.ui.graphics.Color

val RekodiAmber = Color(0xFFE8A33D)
val RekodiAmberDark = Color(0xFFC4882E)
val RekodiAmberLight = Color(0xFFFFD070)
val RekodiCharcoal = Color(0xFF1C1B1F)
val RekodiCharcoalLight = Color(0xFF2C2B30)
val RekodiOffWhite = Color(0xFFFAF7F2)
val RekodiSavannahGreen = Color(0xFF3E7C59)
val RekodiSavannahGreenDark = Color(0xFF2E5E43)
val RekodiRed = Color(0xFFE53935)
val RekodiRedPulse = Color(0xFFFF5252)

val LightPrimary = RekodiAmber
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFFF3E0)
val LightOnPrimaryContainer = Color(0xFF2C1A00)
val LightSecondary = RekodiSavannahGreen
val LightOnSecondary = Color(0xFFFFFFFF)
val LightBackground = RekodiOffWhite
val LightOnBackground = Color(0xFF1C1B1F)
val LightSurface = Color(0xFFFFFBFE)
val LightOnSurface = Color(0xFF1C1B1F)
val LightSurfaceVariant = Color(0xFFF0EAE1)
val LightOnSurfaceVariant = Color(0xFF49473F)
val LightError = RekodiRed

val DarkPrimary = RekodiAmberLight
val DarkOnPrimary = Color(0xFF2C1A00)
val DarkPrimaryContainer = Color(0xFF4A2E00)
val DarkOnPrimaryContainer = Color(0xFFFFDEA8)
val DarkSecondary = Color(0xFF7DD49A)
val DarkOnSecondary = Color(0xFF00391E)
val DarkBackground = RekodiCharcoal
val DarkOnBackground = Color(0xFFE5E1DC)
val DarkSurface = RekodiCharcoalLight
val DarkOnSurface = Color(0xFFE5E1DC)
val DarkSurfaceVariant = Color(0xFF49473F)
val DarkOnSurfaceVariant = Color(0xFFCAC4B5)
val DarkError = Color(0xFFFFB4AB)
```

### Design decisions

**Why amber as the primary brand color?**

The name "Rekodi" is Swahili for "record" (as in "record a video"). The app is developed by Camel Creatives and has a Tanzanian/Swahili inspiration. The amber color (`#E8A33D`) is inspired by Tanzanian sunsets -- the golden-orange hue of the sun setting over the savannah. Amber is:

- **Warm and approachable**: Unlike a cold blue or aggressive red, amber feels friendly and creative. This aligns with the recording use case (capturing moments, creating content).
- **High contrast**: On dark backgrounds, amber stands out well (WCAG AA compliant at large text sizes). The light variant (`#FFD070`) provides sufficient contrast on dark surfaces.
- **Distinctive**: Most apps use blue (Material default), red (YouTube), or purple (Twitch). Amber gives Rekodi a unique identity.

**Why charcoal as the dark mode background?**

`#1C1B1F` is a very dark, slightly warm gray. It is not pure black (`#000000`), which can cause eye strain due to high contrast with white text. The slightly elevated luminance (~3% brightness) reduces eye fatigue while still providing the battery benefits of dark mode on OLED screens. `#2C2B30` (charcoal light) is used for surfaces and cards to create subtle depth through elevation.

**Why off-white as the light mode background?**

`#FAF7F2` is a warm off-white with a slight yellow tint. Pure white (`#FFFFFF`) can appear harsh, especially under bright lighting. The warm tint complements the amber primary and creates a cohesive visual identity. The `surfaceVariant` (`#F0EAE1`) is slightly darker for elevated surfaces.

**Color role mapping:**

Each Material 3 color role is mapped:
- **Primary/OnPrimary**: Amber/White. Used for FABs, buttons, active tab indicators.
- **PrimaryContainer/OnPrimaryContainer**: Light amber/Dark brown. Used for selected chips, highlighted cards.
- **Secondary/OnSecondary**: Savannah green/White. Used for secondary actions, switches, secondary buttons. Green was chosen because it complements amber (opposite on the color wheel) and has natural associations with "record" (green = go, recording indicator in some apps).
- **Error**: Red. Standard Material error color.
- **Surface**: Near-white (light) / Charcoal-light (dark). Cards, dialogs, bottom sheets.
- **OnSurface/OnSurfaceVariant**: The text colors for primary and secondary text on surfaces.

---

## File: Theme.kt

### Full source

```kotlin
package com.camelcreatives.rekodi.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = LightError,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
)

@Composable
fun RekodiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```

### Analysis

**`LightColorScheme` / `DarkColorScheme`**: These are private vals that define the brand color scheme using `lightColorScheme()` and `darkColorScheme()` builder functions. Not all Material 3 color roles are specified (e.g., `tertiary`, `outline`, `surfaceTint` are omitted). The unspecified roles receive default Material values, which is acceptable for this application.

**`RekodiTheme` composable parameters:**

- `darkTheme: Boolean = isSystemInDarkTheme()`: Defaults to the system dark theme setting. Users can override this via the setting `ReSettings.darkMode`, which would be read at a higher level and passed in. The three modes are: "System" (use this default), "Light" (force `false`), "Dark" (force `true`).
- `dynamicColor: Boolean = true`: Whether to use Material You dynamic color (Android 12+). Defaults to `true`. Users can disable this in settings. When `dynamicColor` is `true` AND the device runs Android 12+ (API 31+), the system's wallpaper-extracted color scheme is used instead of the brand amber. This provides a personalized experience.
- `content: @Composable () -> Unit`: The composable content tree to be themed.

**Color scheme selection logic:**

1. If `dynamicColor == true` AND device is Android 12+ (`SDK_INT >= S`): Call `dynamicDarkColorScheme(context)` or `dynamicLightColorScheme(context)`. These functions read the user's wallpaper colors from the system and generate a Material 3 color scheme. The generated scheme may not contain amber at all -- it reflects the user's personal style.
2. Else if `darkTheme == true`: Use the brand `DarkColorScheme` with amber primary.
3. Else: Use the brand `LightColorScheme`.

**Material You fallback rationale:**

When dynamic color is enabled but the device is pre-Android 12, the system dynamic color APIs are unavailable. Rather than adding a version check inside the `dynamicColor` condition and silently falling through, the code structure falls naturally: `dynamicColor && SDK_INT >= S` fails for pre-S devices, so `darkTheme` determines the scheme.

---

## File: Type.kt

### Full source

```kotlin
package com.camelcreatives.rekodi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RekodiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

### Analysis

The typography follows Material 3 type scale conventions. Key decisions:

- **Font family**: `FontFamily.SansSerif` maps to the system Roboto font (on most devices) or the Google Sans equivalent on Pixel devices. The project does not bundle a custom font to keep the APK size small. If a custom font were needed (e.g., Inter for better readability), it would be added as a resource and referenced via `FontFamily()`.

- **Weight distribution**:
  - Display styles: Bold (700) for maximum impact on large titles.
  - Headline styles: SemiBold (600) for section headers.
  - Title styles: Medium (500) for card titles and navigation.
  - Body styles: Normal (400) for reading text.
  - Label styles: Medium (500) for buttons, chips, and small labels.

- **Negative letter spacing on `displayLarge`**: `-0.25.sp` tightens the character spacing on the largest text size, which improves readability by preventing characters from appearing too widely spaced at large sizes. This is the Material 3 convention.

- **Missing styles**: The `Typography` object does not specify all 15 Material 3 text styles. Unspecified styles (e.g., `displaySmall`, `headlineSmall`, `titleSmall`, `bodySmall`, `labelMedium`) fall back to Material Design 3 defaults, which are acceptable.

- **Reading accessibility**: The body text sizes (16sp/`bodyLarge`, 14sp/`bodyMedium`) are within the commonly recommended range for mobile reading. Line heights are 1.5x the font size (24sp for 16sp, 20sp for 14sp), which provides adequate line spacing for readability.

---

## File: RekodiComponents.kt

### Full source

This file contains three composable components: `RecordButton`, `RecordingTimer`, and `RecordingCard`.

### RecordButton

```kotlin
@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .size(72.dp)
            .scale(if (isRecording) pulseScale else 1f),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRecording) RekodiRed else RekodiAmber
        ),
        content = {
            Box(
                modifier = Modifier
                    .size(if (isRecording) 24.dp else 28.dp)
                    .clip(if (isRecording) RoundedCornerShape(4.dp) else CircleShape)
                    .background(if (isRecording) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onPrimary)
            )
        }
    )
}
```

**Design decisions and line-by-line analysis:**

- **Parameters**: `isRecording` controls visual state (amber idle vs red recording). `onClick` is the action handler. `modifier` allows parent composables to size/position the button.

- **Pulse animation** (lines 48-57): `rememberInfiniteTransition` creates a transition that runs forever. `animateFloat` oscillates between `1.0` and `1.08` over 600ms with `RepeatMode.Reverse` (scale up, scale down, repeat). This creates a subtle breathing effect during recording, drawing the user's attention to the button as an active control.

- **Size** (line 62): Fixed 72dp diameter. This is large enough to be easily tappable (well above the 48dp minimum touch target) without dominating the layout.

- **Scale modifier** (line 63): When `isRecording` is true, the scale animation is applied. When false, the button remains at 1.0 (no pulse). The pulse only activates during active recording.

- **Shape** (line 64): `CircleShape` for a circular record button, matching the standard media recording iconography.

- **Colors** (lines 65-67): Amber when idle, red (`RekodiRed`) when recording. This is the universal recording convention: red = recording, gray/amber = idle/stopped.

- **Inner icon** (lines 69-74): A `Box` that changes shape and color to represent the current state:
  - **Idle** (not recording): Circle (28dp), white (or `onPrimary` color). This represents the "record" icon (a solid circle).
  - **Recording**: Rounded rectangle (24dp, 4dp corner radius), white. This represents the "stop" icon (a square). The transition from circle to rounded rectangle is not animated (simply conditionally rendered) but the size difference (28dp to 24dp) provides a subtle visual cue.

- **Why a Box for the icon?**: Rather than using an image asset or vector drawable, a simple colored shape is used. This avoids dependency on icon resources and ensures the icon matches the button colors perfectly (white on colored background). The `clip` modifier creates the shape, `background` fills it.

### RecordingTimer

```kotlin
@Composable
fun RecordingTimer(
    elapsedSeconds: Long,
    modifier: Modifier = Modifier
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeStr = String.format("%02d:%02d", minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.animateContentSize()
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RekodiRedPulse.copy(alpha = dotAlpha))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = timeStr,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
```

**Analysis:**

- **Time formatting** (lines 84-86): Converts `elapsedSeconds` to `MM:SS` format. If the recording exceeds 99 minutes 59 seconds, it will still display `MM:SS` (truncating hours). This is a deliberate simplification -- most screen recordings are under 2 hours. The `formatDuration()` function from `core-common` is not used here because the component always wants `MM:SS` display.

- **Pulsing dot** (lines 88-97): An 8dp red circle that fades between `alpha = 1.0` (fully opaque) and `alpha = 0.3` (dim). The animation uses `tween(800)` -- 800ms per half-cycle, 1600ms for a complete fade in and out. This provides a gentle pulsing indicator that the timer is "alive" (recording is active), without being visually distracting.

- **animateContentSize** (line 101): The `Row` has `animateContentSize()` modifier. When the time text changes (every second), the row's width changes. `animateContentSize` smoothly interpolates the size change rather than jumping, which prevents layout jitter in the parent composable.

- **Typography** (lines 112-113): Uses `titleMedium` style with `FontWeight.Bold`. The bold weight ensures the timer is highly visible at a glance.

- **Why not use the notification timer?**: The `RecordingTimer` composable is used in the recording screen UI (within the app). The recording service notification has its own separate timer rendering. Both read from the same `elapsedSeconds` StateFlow.

### RecordingCard

```kotlin
@Composable
fun RecordingCard(
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing?.invoke()
        }
    }
}
```

**Analysis:**

- **Card width** (line 129): `fillMaxWidth()` means the card expands to the available width. This is appropriate for list items in the library screen.

- **Shape** (line 130): `RoundedCornerShape(16.dp)`. Material 3 Card default shape is 12dp. Using 16dp gives a slightly more pronounced rounding that aligns with the app's friendly, approachable design language.

- **Colors** (line 131-133): `surfaceVariant` provides a subtle background that distinguishes the card from the screen background. Using `surfaceVariant` instead of `surface` creates visual separation between adjacent cards in a list without relying solely on elevation shadows.

- **Elevation** (line 134): 2dp elevation. Material 3 Card default is 1dp. The slightly higher elevation gives cards more prominence, making the list feel more interactive.

- **Layout** (lines 136-157): A `Row` with:
  - Title/subtitle `Column`: Uses `weight(1f)` to fill remaining space, ensuring the trailing content is always right-aligned.
  - Title: `titleMedium` style for the recording name.
  - Subtitle: `bodySmall` style with `onSurfaceVariant` color (secondary text). Typically displays duration, date, file size.
  - `trailing` slot: An optional composable lambda for right-aligned content (e.g., favorite icon, more options menu, duration badge).

---

## Build Configuration

**File:** `core-ui/build.gradle.kts`

```kotlin
plugins {
    id("camelcreatives.android.library.compose")
}

android {
    namespace = "com.camelcreatives.rekodi.ui"
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.coil.compose)
}
```

The convention plugin `camelcreatives.android.library.compose` (`AndroidLibraryComposeConventionPlugin`):
- Applies `com.android.library`, `org.jetbrains.kotlin.android`, and `org.jetbrains.kotlin.plugin.compose` (Kotlin Compose compiler plugin).
- Enables `buildFeatures { compose = true }`.
- Adds Compose BOM, `compose-ui`, `compose-material3`, `compose-ui-tooling-preview`, `compose-foundation` as default dependencies.
- Adds `compose-ui-tooling` for debug builds.

Additional dependencies:
- `libs.compose.material.icons.extended`: Extended Material Icons set. Provides additional icons beyond the default set (e.g., record, stop, pause, play icons). Needed for UI controls.
- `libs.coil.compose`: Coil image loading for Compose. Used for loading recording thumbnails and video frame previews in the library UI.

The module depends on `core-common` for utility functions (e.g., `formatDuration`, `formatFileSize`).

---

## Accessibility Considerations

1. **Color contrast**: The amber-on-white and white-on-amber combinations exceed WCAG AA contrast ratios at text sizes above 18dp (14sp). The dark theme provides even higher contrast with light amber on charcoal.
2. **Touch targets**: All interactive components (RecordButton at 72dp, RecordingCard's clickable area) exceed the 48dp minimum touch target.
3. **Content descriptions**: Components are designed to receive content descriptions via `Modifier.semantics { }` from the parent composable. The components themselves do not hardcode descriptions to allow contextual labeling.
4. **Dynamic text**: All text sizes are in `sp` units, respecting the user's system font size setting. Layouts use `dp` and adapt to text scaling.
5. **Animation respect**: Compose's `animateFloat` and `animateContentSize` respect the user's "Remove animations" system setting (they snap to end state when animations are disabled).
