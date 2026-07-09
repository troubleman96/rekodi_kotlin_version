# feature-editor Module -- Audio Editor

## Module Purpose

The feature-editor module provides an in-app audio editing capability, allowing users to trim, split, fade, merge, and normalize audio from their screen recordings. This module is entirely optional for the core recording feature but adds significant value by enabling users to polish their recordings without leaving the app. The editor operates on the audio track extracted from MP4 recordings or standalone M4A audio files.

## Architecture Overview

The module is structured into three sub-packages:

- **model/** -- Data classes and enums for audio clips, waveform data, edit actions, and undo state
- **engine/** -- The core AudioEngine singleton that performs all audio processing using MediaExtractor, MediaCodec, and MediaMuxer
- **ui/** -- The Jetpack Compose audio editor screen with waveform visualization, transport controls, and tool-specific panels

Dependencies: This module depends on `:core:core-common` for `RekodiResult`, `:core:core-ui` for theme colors (RekodiAmber), and standard Android media APIs. It does NOT depend on any external audio processing library -- all operations are implemented using platform APIs.

---

### model/AudioEditModels.kt

**Package:** `com.camelcreatives.rekodi.editor.model`

**`AudioClip` data class (lines 3-12):**

Represents a segment of audio within the editor timeline. Fields:

- `id: String` -- Auto-generated UUID for uniquely identifying clips. Generated via `UUID.randomUUID().toString()`.
- `filePath: String` -- Absolute path to the source audio file. Required.
- `startMs: Long` -- Start position in milliseconds within the source file. Used for trimming.
- `endMs: Long` -- End position in milliseconds. Together with startMs defines the segment to extract.
- `durationMs: Long` -- Total duration of the clip (may differ from endMs - startMs for the root clip).
- `fadeInMs: Long` -- Duration of fade-in effect in milliseconds. Default 0.
- `fadeOutMs: Long` -- Duration of fade-out effect in milliseconds. Default 0.
- `volumeGain: Float` -- Multiplier for volume. 1.0 = original, 0.0 = silent, 2.0 = double. Default 1.0.

The UUID-based ID allows clips to be tracked across undo/redo operations without relying on list indices.

**`WaveformPeaks` data class (lines 14-31):**

Holds pre-computed waveform data for rendering and visualization. Fields:

- `samples: FloatArray` -- Down-sampled peak amplitudes. Each float is in range 0.0 to 1.0, representing the maximum absolute amplitude within a chunk of audio.
- `sampleRate: Int` -- The sample rate of the original audio (e.g. 44100 or 48000).
- `totalDurationMs: Long` -- Total duration of the audio in milliseconds.

The class provides `peakCount: Int` as a computed property.

This class manually implements `equals()` and `hashCode()` using `contentEquals()` and `contentHashCode()` because `samples` is a `FloatArray`. Standard data class equality would compare by reference for arrays, which is incorrect. The overridden equals compares the array contents structurally.

**`EditAction` enum (lines 34-36):**

Enumerates the types of edit operations the user can perform:

- `TRIM` -- Remove audio outside a selected region.
- `SPLIT` -- Split a clip at the playhead position.
- `FADE_IN` -- Apply a fade-in effect.
- `FADE_OUT` -- Apply a fade-out effect.
- `VOLUME` -- Adjust volume gain.
- `NOISE_REDUCTION` -- Reduce background noise (defined but not implemented).
- `SILENCE_TRIM` -- Remove silent sections (defined but not implemented).

**`UndoState` data class (lines 38-41):**

A snapshot used for undo/redo functionality:

- `clips: List<AudioClip>` -- The complete list of clips at a given point in time.
- `description: String` -- A human-readable description of the action (e.g. "Trim audio", "Apply fade").

This enables an undo stack by pushing snapshots before each mutation and restoring them on undo.

---

### engine/AudioEngine.kt

**Package:** `com.camelcreatives.rekodi.editor.engine`

This is a `@Singleton` annotated class injected via Hilt, taking `@ApplicationContext` as a constructor parameter. It contains all the actual audio processing logic. Every public operation returns `RekodiResult<T>` for structured error handling rather than throwing exceptions.

**`extractWaveform()` (lines 25-96):**

Decodes an audio file and extracts a down-sampled waveform representation. This is the most complex method in the class because it performs full PCM decoding.

Algorithm:
1. **Open MediaExtractor:** Sets the data source to the given filePath.
2. **Select Audio Track:** Calls `selectAudioTrack()` to find the first audio track. Returns null if none found.
3. **Read Format:** Gets the track format, extracting sample rate and duration.
4. **Create and Configure MediaCodec Decoder:** Creates a decoder by MIME type, configures it, and starts it. The decoder converts the compressed audio (e.g. AAC) into raw PCM samples.
5. **PCM Extraction Loop:**
   - Reads compressed data from the extractor via `readSampleData()`.
   - Feeds it to the decoder via `queueInputBuffer()`.
   - Retrieves decoded PCM output via `dequeueOutputBuffer()`.
   - Copies the output byte buffer into a `ShortBuffer` (PCM 16-bit signed little-endian) and stores each chunk in a list.
   - The loop continues until both input and output EOS flags are seen.
6. **Cleanup:** Stops and releases the decoder and extractor.
7. **Compute Peaks:** Flattens the PCM chunks into one `ShortArray`, then calls `computePeaks()` to down-sample to the target number of peaks.
8. **Return:** Creates a `WaveformPeaks` object with the down-sampled amplitudes, sample rate, and duration.

Error handling: The entire method is wrapped in try-catch and returns null on any failure. This is a pragmatic choice for waveform extraction where partial results are better than crashing.

**`trimAudio()` (lines 98-136):**

Copies a segment of audio from startMs to endMs into a new output file. This is a direct sample-copy operation (no re-encoding), which is fast and lossless.

Algorithm:
1. Open MediaExtractor on the input file, select audio track.
2. Create MediaMuxer with MPEG4 output format.
3. Call `extractor.seekTo(startMs * 1000, SEEK_TO_CLOSEST_SYNC)` to jump to the start position.
4. Loop reading samples from the extractor with `readSampleData()`.
5. For each sample, check if its presentation time exceeds endUs. If so, stop.
6. Write each sample to the muxer with an adjusted presentation time (subtract startUs so the output starts at time 0).
7. Stop and release muxer and extractor.

Error handling: Returns `RekodiResult.Error` on failure with descriptive message.

**`applyFade()` (lines 138-185):**

Applies linear fade-in and/or fade-out effects. This method currently does NOT actually modify PCM sample amplitudes -- it has placeholder variables for gain calculation but does not apply them to the buffered samples. The fade variables (`gain`) are computed but never used to scale the audio data.

Algorithm:
1. Open extractor and muxer as in trimAudio.
2. Loop through samples.
3. Calculate a progress value: `presentationTimeUs / duration`.
4. If within the fade-in region (presentationTimeUs < fadeInEnd), compute a linear gain from 0 to 1.
5. If within the fade-out region (presentationTimeUs > fadeOutStart), compute a linear gain from 1 to 0.
6. (Not implemented) Apply the gain to the PCM samples before writing.

The current implementation writes all samples unmodified. The flags variable is set to 0 for fade regions (which may corrupt the output). This method is clearly a work-in-progress. A proper implementation would need to:
- Decode PCM samples (like extractWaveform does)
- Apply gain multipliers sample-by-sample
- Re-encode to the output format

**`mergeAudio()` (lines 187-232):**

Concatenates multiple AudioClip segments sequentially into one output file. This is a "tape-style" merge where clips are appended one after another, with each clip's presentation timestamps offset by the total duration of previous clips.

Algorithm:
1. Create MediaMuxer for output.
2. For each clip in the list:
   a. Open MediaExtractor on the clip's file.
   b. Select the audio track. Skip clip if no audio track found.
   c. If this is the first clip with a track, add the track to the muxer and start it.
   d. Seek to the clip's startMs position.
   e. Loop reading samples until clip.endMs is reached.
   f. Write each sample with presentation time adjusted: `totalOffsetUs + pts - startUs`.
   g. After the clip, advance `totalOffsetUs` by the clip's duration in microseconds.
3. Stop and release muxer.

This correctly handles clips from different source files and even different formats (as long as the tracks are compatible). The first track's format is used for the muxer, so all clips should ideally have the same audio format.

**`selectAudioTrack()` (lines 234-241):**

A private utility method that iterates through all tracks in an extractor and returns the index of the first track whose MIME type starts with "audio/". Returns null if no audio track is found.

**`computePeaks()` (lines 243-257):**

Down-samples a PCM ShortArray to a target number of peaks for waveform display:

1. Calculates chunkSize as `max(1, pcm.size / targetPeaks)`.
2. Creates a FloatArray of size targetPeaks.
3. For each target peak index:
   a. Takes the corresponding chunk of PCM samples.
   b. Computes the maximum absolute amplitude within that chunk, normalized to 0.0-1.0 by dividing by `Short.MAX_VALUE` (32767).
   c. Stores the peak.

This produces the data needed for the WaveformView to render visual bars.

---

### ui/AudioEditorScreen.kt

**Package:** `com.camelcreatives.rekodi.editor.ui`

A Jetpack Compose screen providing the full audio editing interface. The screen is built as a scaffold with a top app bar, waveform display, transport controls, tool selection chips, and tool-specific control panels.

**`AudioEditorScreen()` (lines 56-186):**

Parameters:
- `onNavigateBack: () -> Unit` -- Callback for the back arrow.
- `modifier: Modifier` -- Standard Compose modifier.

State variables (all managed via `remember` and `mutableStateOf`):
- `selectedTool` -- One of "trim", "fade", "volume", "split", "merge". Controls which tool panel is visible.
- `progress` -- Playhead position from 0.0 to 1.0.
- `trimStart` -- Trim region start from 0.0 to 1.0.
- `trimEnd` -- Trim region end from 0.0 to 1.0.
- `fadeInMs` -- Fade-in duration in milliseconds (0-5000).
- `fadeOutMs` -- Fade-out duration in milliseconds (0-5000).
- `volumeGain` -- Volume multiplier (0.0 to 3.0).

Layout (top to bottom):

1. **TopAppBar:** Title "Audio Editor" with back navigation icon.

2. **Waveform Display (lines 93-106):** A Box (160dp tall) with rounded corners and surfaceVariant background, containing the `WaveformView` composable. The waveform shows a placeholder sine wave when no peaks are loaded.

3. **Transport Controls (lines 111-143):** A Row centered horizontally with three buttons:
   - Previous (48dp circle, "<" text): Decrements progress by 5%.
   - Play (64dp circle, amber background): Currently a no-op placeholder.
   - Next (48dp circle, ">" text): Increments progress by 5%.

4. **Tool Selection (lines 148-159):** A Row of FilterChips for "Trim", "Fade", "Volume", "Split", "Merge". The selected chip determines which tool panel is shown below.

5. **Tool-Specific Controls (lines 164-183):** A `when` block that renders the appropriate composable based on `selectedTool`:
   - "trim" -> `TrimControls`
   - "fade" -> `FadeControls`
   - "volume" -> `VolumeControls`
   - "split" -> `SplitControls`
   - "merge" -> `MergeControls`

**`WaveformView()` (lines 188-233):**

A custom Compose Canvas that draws the audio waveform visualization. Parameters:
- `peaks: FloatArray?` -- The waveform peak data, or null for placeholder.
- `trimStart/trimEnd: Float` -- The selected trim region as fractions (0.0-1.0).
- `progress: Float` -- The playhead position as a fraction.

Drawing operations:
1. **Trim Region Background:** Draws a semi-transparent white rectangle over the selected trim region (from `trimStart * width` to `trimEnd * width`). This highlights the region that will be preserved when trimming.
2. **Progress Line:** Draws a vertical line at `progress * width` with moderate opacity, representing the current playhead position.
3. **Waveform Bars:**
   - If peaks are available: Divides the width by `peaks.size` and draws vertical lines for each peak, centered vertically. The amplitude is scaled to 80% of half the view height.
   - If no peaks (placeholder): Draws 200 simulated sine wave bars using `kotlin.math.sin(i * 0.1f)` scaled to 25% of view height. This provides visual feedback even before an audio file is loaded.

**`TrimControls()` (lines 235-255):**

Provides two sliders for selecting the trim region:
- Start Slider: Range 0.0 to `trimEnd` (cannot exceed end).
- End Slider: Range `trimStart` to 1.0 (cannot precede start).
- "Apply Trim" button with a cut icon. Currently a no-op.

The mutual constraints (start <= end) are enforced through the slider value ranges.

**`FadeControls()` (lines 257-275):**

Two sliders for fade durations:
- Fade In: 0ms to 5000ms (5 seconds).
- Fade Out: 0ms to 5000ms.
- "Apply Fade" button. Currently a no-op.

**`VolumeControls()` (lines 277-294):**

- Displays a volume icon and the current gain as a percentage.
- Slider range: 0.0 to 3.0 (0% to 300% volume).
- "Normalize" button. Currently a no-op. Normalization would analyze the audio to find the peak amplitude and scale the entire file to 0dB (full scale).

**`SplitControls()` (lines 296-307):**

- Instructional text: "Move the playhead to the split point and tap Split."
- "Split at Playhead" button with cut icon. Currently a no-op.

**`MergeControls()` (lines 309-319):**

- Instructional text: "Select clips to merge in order."
- "Merge Selected" button with merge icon. Currently a no-op.

---

## Integration Points

1. **Navigation:** The editor is navigated to from the Library's RecordingDetailScreen via the "Edit Audio" button. The route is `audio_editor/{recordingId}` defined in `RekodiNavHost.Routes`.

2. **Data Flow:** The recording's file path is passed via the recordingId parameter. The editor would retrieve the RecordingEntity from the repository, extract the audio waveform using AudioEngine, and present editing controls.

## Known Issues and Future Work

- All Apply buttons are no-ops. The UI framework and state management are in place, but the actual engine method calls are not wired.
- `applyFade()` in AudioEngine does not actually modify PCM samples. The gain calculation variables are computed but never applied.
- The editor currently has no way to load a specific file -- the `peaks` parameter to WaveformView is hardcoded to null.
- NOISE_REDUCTION and SILENCE_TRIM EditAction values exist but have no corresponding engine methods.
- Undo/Redo (UndoState) is defined in the model but not implemented in the UI.
- There is no audio playback mechanism -- the play button and progress slider are presentational only.
- The transport controls use simple text characters ("<", ">", "PLAY") rather than Material icons.
