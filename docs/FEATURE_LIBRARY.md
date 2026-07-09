# feature-library Module -- Recording Gallery

## Module Purpose

The feature-library module implements the main screen of the Rekodi application -- a searchable, sortable, filterable gallery of all screen recordings. It also provides a detail view where users can inspect recording metadata, share, edit audio, or delete recordings. This is the user's primary point of interaction with the app after recordings are made.

## Architecture Overview

The module consists of two Jetpack Compose screens:

- **LibraryScreen** -- The main gallery with search bar, filter chips, recording list, empty state, and a FAB to start recording
- **RecordingDetailScreen** -- A metadata detail view with actions for share, audio editing, and deletion

Dependencies: This module depends on `:core:core-data` for RecordingEntity and RecordingRepository, `:core:core-common` for utility functions (formatDuration, formatFileSize), and `:core:core-ui` for theme colors (RekodiAmber, RekodiRed). It also uses Hilt for dependency injection.

---

### ui/LibraryScreen.kt

**Package:** `com.camelcreatives.rekodi.library.ui`

**`LibraryScreen()` (lines 72-183):**

This is the main gallery composable. Parameters:

- `onNavigateToSettings: () -> Unit` -- Opens the settings screen.
- `onNavigateToEditor: (Long) -> Unit` -- Opens the audio editor for a given recording ID.
- `onNavigateToDetail: (Long) -> Unit` -- Opens the detail screen for a given recording ID.
- `modifier: Modifier` -- Standard Compose modifier.

**State Management (lines 79-83):**

Three pieces of local state are managed with `remember` and `mutableStateOf`:

- `searchQuery: String` -- The current search text in the OutlinedTextField.
- `filter: String` -- The active filter chip: "all", "video", "audio", or "favorites".
- `recordings: List<RecordingEntity>` -- The displayed list of recordings. Currently initialized to an empty list.

Note on line 83: `val recordingRepository: RecordingRepository? = null` -- This is a placeholder. The repository is not actually injected or connected, meaning the recordings list remains empty in the current implementation. A ViewModel would normally be used here to observe Room flows and apply filtering/searching.

**Scaffold Structure (lines 85-182):**

1. **TopAppBar (lines 87-98):** Displays the "Rekodi" title (bold) and a settings icon button in the actions slot. The container color is set to the surface color.

2. **FloatingActionButton (lines 99-110):** An amber-colored FAB with a Videocam icon. This triggers the screen recording flow (currently a no-op onClick). The FAB uses `containerColor = RekodiAmber` and tints the icon with `onPrimary` for contrast.

3. **Content Column (lines 112-182):** Inside the scaffold's padding, a Column holds:
   - Search bar (OutlinedTextField with search icon, rounded corners, single line)
   - Filter chips (FlowRow of FilterChips)
   - Recording list or empty state

**Search Bar (lines 118-126):**

An `OutlinedTextField` with:
- `value` bound to `searchQuery`
- Placeholder text: "Search recordings..."
- Leading icon: Search icon
- `singleLine = true`
- Rounded shape (12dp radius)

**Filter Chips (lines 130-146):**

A `FlowRow` (experimental API) of `FilterChip` composables for four filters:
- "All" -- No icon (null leadingIcon)
- "Video" -- Videocam icon
- "Audio" -- Audiotrack icon
- "Favorites" -- Favorite (heart) icon

Each chip's `selected` state is determined by comparing `filter` to the chip's value. The chip labels use `replaceFirstChar { it.uppercase() }` for proper capitalization.

**Empty State (lines 150-168):**

When `recordings` is empty, a centered `Box` displays:
- Title: "No recordings yet" (headlineSmall style)
- Subtitle: "Tap the record button to start capturing your screen" (bodyMedium style, using `onSurfaceVariant` color for subtlety)

**Recording List (lines 169-180):**

When recordings are present, a `LazyColumn` with 8dp spacing renders `RecordingListItem` composables.

**`RecordingListItem()` (lines 185-278):**

A clickable `Card` composable for each recording. Parameters:
- `recording: RecordingEntity` -- The data to display.
- `onClick: () -> Unit` -- Navigates to the detail screen.

Card properties:
- `fillMaxWidth()` width
- 12dp rounded corners
- `surfaceVariant` background color
- 1dp elevation

Layout (horizontal Row with 12dp padding):
1. **Thumbnail Placeholder (left):** A 56x56dp Card with `surface` background containing a centered amber icon:
   - Videocam if mimeType starts with "video"
   - Audiotrack if not
   - Icon is 24dp, tinted `RekodiAmber`

2. **Metadata Column (center, weight 1f):**
   - File name: titleSmall, medium weight, single line with ellipsis overflow
   - Duration and file size: bodySmall, onSurfaceVariant color, displayed via `formatDuration()` and `formatFileSize()` utility functions
   - Tap count (if > 0): labelSmall, amber color

3. **Action Column (right):** Two IconButtons stacked vertically:
   - Favorite toggle: Shows filled heart icon if `isFavorite` (tinted amber) or unfilled heart border if not (tinted onSurfaceVariant). Currently a no-op.
   - Share button: Share icon, onSurfaceVariant tint. Currently a no-op.

**Design decisions:**
- The library screen uses local state with `remember` rather than a ViewModel. This is a simplification that works for UI prototyping but means the recordings list will never populate from the database. A `HiltViewModel` with `collectAsStateWithLifecycle` would be the production approach.
- The `FlowRow` for filter chips is marked as `@ExperimentalLayoutApi`, which means it may change in future Compose versions.
- The FAB uses Videocam icon, which doubles as the app's primary recording action.

---

### ui/RecordingDetailScreen.kt

**Package:** `com.camelcreatives.rekodi.library.ui`

**`RecordingDetailScreen()` (lines 44-124):**

A detail screen that displays full metadata for a single recording and provides action buttons. Parameters:
- `onNavigateBack: () -> Unit` -- Pop the back stack.
- `onNavigateToEditor: (Long) -> Unit` -- Navigate to the audio editor with the recording ID.
- `modifier: Modifier` -- Standard modifier.

**Scaffold Structure:**

1. **TopAppBar:** Title "Recording Details" with back navigation arrow.

2. **Content Column (with vertical scroll):**
   - **Title:** "Recording Name" (headlineSmall, bold) -- This is hardcoded placeholder text. Production code would show `recording.fileName`.
   - **Metadata Rows (lines 80-86):** Seven `InfoRow` composables displaying:
     - Duration (via `formatDuration(0)` -- hardcoded to 0)
     - File Size (via `formatFileSize(0)` -- hardcoded to 0)
     - Resolution: "1920 x 1080" (hardcoded)
     - Frame Rate: "30 fps" (hardcoded)
     - Bitrate: "8 Mbps" (hardcoded)
     - Tap Count: "0 taps" (hardcoded)
     - Date: "Just now" (hardcoded)
   - **Action Buttons Row (lines 90-110):** Two buttons side by side with equal weight:
     - Share button: Amber background, share icon + "Share" text
     - Edit Audio button: Amber background, ContentCut icon + "Edit Audio" text, calls `onNavigateToEditor(0)` with hardcoded recording ID 0
   - **Delete Button (lines 114-121):** Full-width button with `RekodiRed` background, delete icon + "Delete Recording" text. Currently a no-op.

**`InfoRow()` (lines 126-155):**

A reusable private composable that displays a label-value pair in a card:
- Takes `label` and `value` strings.
- Renders a Card with 8dp rounded corners and `surfaceVariant` background.
- Inside, a Row with horizontal padding of 16dp, vertical padding of 12dp.
- `SpaceBetween` arrangement puts the label on the left (onSurfaceVariant color) and value on the right (medium weight).
- Each card has vertical margin of 4dp.

**Design decisions:**
- All metadata values are hardcoded placeholders. A production implementation would receive a `recordingId` parameter, fetch the `RecordingEntity` from the repository via a ViewModel, and populate these fields dynamically.
- The `onNavigateToEditor` callback passes hardcoded ID 0. The route `audio_editor/{recordingId}` would need the actual recording ID.
- The InfoRow pattern provides a consistent, clean metadata display that adapts to any label/value pair.

---

## Integration Points

1. **Navigation:** The LibraryScreen is the HOME route in `RekodiNavHost`. It provides navigation callbacks to Settings, Editor, and Detail screens.

2. **Data Layer:** The screen expects to use `RecordingRepository` to observe Room database flows. The `RecordingDao` already provides filtered queries (`getAllRecordings`, `getVideoRecordings`, `getAudioRecordings`, `getFavoriteRecordings`, `searchRecordings`) that the library can leverage.

3. **Audio Editor:** The detail screen's "Edit Audio" button navigates to `AudioEditorScreen` with the recording ID, enabling audio editing from the gallery.

## Known Issues and Future Work

- The recordings list is always empty because `RecordingRepository` is not injected or wired to a ViewModel.
- All metadata values in RecordingDetailScreen are hardcoded placeholders.
- The favorite toggle and share button in RecordingListItem are no-ops.
- The search bar updates `searchQuery` state but does not filter the recordings list.
- The filter chips update `filter` state but do not trigger database queries.
- The FAB onClick is a no-op; it should trigger `MediaProjectionTrampolineActivity` to start a new recording.
- The delete button has no confirmation dialog and does not call the repository.
