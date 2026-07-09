# feature-onboarding Module -- First-Run Experience

## Module Purpose

The feature-onboarding module provides a first-run experience that introduces new users to the Rekodi application's key features and explains the rationale behind required permissions. The onboarding flow consists of four horizontally swipeable pages (using Jetpack Compose's HorizontalPager) with navigation controls including Skip, Next, page indicators, and a final "Get Started" button.

## Architecture Overview

The module consists of a single file containing:

- **OnboardingPage** data class -- Model for each page's content
- **OnboardingScreen** composable -- The full-screen pager with navigation

Dependencies: This module depends on `:core:core-ui` for theme colors (RekodiAmber). It uses the `foundation.pager` API from Compose for horizontal swiping.

---

### ui/OnboardingScreen.kt

**Package:** `com.camelcreatives.rekodi.onboarding`

**`OnboardingPage` data class (lines 33-37):**

A simple model for each onboarding page:

- `title: String` -- The page headline.
- `description: String` -- The explanatory text body.
- `icon: String` -- A string representation of an emoji icon, displayed in `displayLarge` text style. Using emoji strings rather than drawable resources simplifies the implementation but means the icons are platform-dependent in appearance.

**`OnboardingScreen()` (lines 39-160):**

The main onboarding composable. Parameters:
- `onComplete: () -> Unit` -- Callback invoked when the user completes or skips onboarding. This should navigate to the HOME route and mark onboarding as complete (so it does not show again).
- `modifier: Modifier` -- Standard Compose modifier.

**Page Definitions (lines 44-65):**

Four pages are defined as a list of `OnboardingPage` instances:

1. **Page 1 -- "Welcome to Rekodi":**
   - Icon: Film projector emoji
   - Description: "Record your screen and audio with a beautiful floating control bubble. Rekodi Kila Kitu." (Kiswahili for "Record Everything")
   - Purpose: Brand introduction and value proposition.

2. **Page 2 -- "Floating Control Bubble":**
   - Icon: Bubbles emoji
   - Description: "A draggable overlay lets you start, pause, and stop recordings from any app."
   - Purpose: Explains the key UX innovation -- the floating bubble that makes recording controllable from anywhere.

3. **Page 3 -- "Built-in Audio Editor":**
   - Icon: Scissors emoji
   - Description: "Trim, split, merge, fade, and clean up your recordings without leaving the app."
   - Purpose: Highlights the integrated audio editing feature.

4. **Page 4 -- "Ready to Go":**
   - Icon: Rocket emoji
   - Description: "We will ask for a few permissions to enable recording. Each is explained and optional."
   - Purpose: Sets expectations for the permission flow that follows.

**Pager State (lines 67-68):**

```kotlin
val pagerState = rememberPagerState(pageCount = { pages.size })
val scope = rememberCoroutineScope()
```

`rememberPagerState` creates the HorizontalPager state with a page count of 4. The `scope` is used for launching the `animateScrollToPage` coroutine when the Next button is pressed.

**Layout Structure (lines 70-159):**

The entire screen is a Column with `fillMaxSize()` and the background color set to `MaterialTheme.colorScheme.background`:

1. **Skip Button (lines 75-84):** A Row at the top aligned to the end (right side) containing a `TextButton` labeled "Skip". Clicking it calls `onComplete` immediately, skipping all onboarding pages.

2. **HorizontalPager (lines 86-118):** Takes up the `weight(1f)` remaining space. Each page renders a Column with:
   - The page's icon emoji in `displayLarge` style (36sp bold), content aligned, 120dp size constraint.
   - 32dp spacer.
   - The page's title in `headlineMedium` style (20sp semibold), bold weight, center-aligned.
   - 16dp spacer.
   - The page's description in `bodyLarge` style (16sp), center-aligned, using `onSurfaceVariant` color for softer appearance.

3. **Page Indicators (lines 120-138):** A Row of Box composables centered horizontally. For each page:
   - A circle (`CircleShape` clip) with size 10dp for the active page, 8dp for inactive pages.
   - Active indicator uses `RekodiAmber` color. Inactive uses `outlineVariant`.
   - 4dp padding between indicators.

4. **Next/Get Started Button (lines 140-156):** A full-width Button:
   - Label: "Next" if not on the last page, "Get Started" on the last page.
   - Colors: `RekodiAmber` container color.
   - On click: If not on last page, uses `scope.launch` to animate to the next page via `pagerState.animateScrollToPage()`. If on last page, calls `onComplete()`.

5. **Bottom Spacer (line 158):** 16dp spacer at the very bottom for visual balance.

---

## Design Decisions

1. **Pager instead of standalone screens:** Using HorizontalPager provides a smooth swipe-based navigation that is intuitive and familiar to mobile users. The user can swipe left/right or use the Next button.

2. **No persistence:** The onboarding screen does not persist its completion state. This is handled upstream -- the `RekodiNavHost` or MainActivity should check a DataStore preference and either show onboarding or skip to HOME.

3. **Emoji icons:** Using emoji strings avoids the need for custom drawable resources. This simplifies the module but means the icons lack the custom branding that illustrator-based assets would provide.

4. **Minimal text:** Each page has a single title and single description line. This keeps the onboarding concise and avoids overwhelming new users.

---

## Integration Points

1. **Navigation:** The onboarding route is `Routes.ONBOARDING` in `RekodiNavHost`. It is not currently the start destination -- `Routes.HOME` is. The app would need to conditionally set the start destination based on whether onboarding has been completed.

2. **Permission Flow:** The onboarding concludes with "Ready to Go" which sets expectations for permissions. The actual permission requests happen in `RecorderPermissionScreen` in the feature-recorder module, which is triggered when the user first tries to record.

## Known Issues and Future Work

- The onboarding completion state is not persisted. A `SettingsDataStore` preference (e.g., `onboarding_completed`) should be checked before showing onboarding on subsequent launches.
- The emoji icons are platform-dependent and may render differently across devices and Android versions. Custom vector drawables would be more consistent.
- There is no way to re-trigger onboarding from settings (a common pattern for "Show me again").
- No animation effects on page transitions (e.g., fade, slide, parallax) besides the default pager slide.
- The page indicator dots use a ternary for sizing (10dp active vs 8dp inactive) but the pager's `currentPage` comparison could also animate the transition between sizes.
