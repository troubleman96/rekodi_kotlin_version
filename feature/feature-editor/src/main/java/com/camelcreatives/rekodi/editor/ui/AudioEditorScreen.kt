package com.camelcreatives.rekodi.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.camelcreatives.rekodi.ui.theme.RekodiAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudioEditorViewModel = hiltViewModel()
) {
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    
    var selectedTool by remember { mutableStateOf("trim") }
    var progress by remember { mutableFloatStateOf(0f) }
    var trimStart by remember { mutableFloatStateOf(0f) }
    var trimEnd by remember { mutableFloatStateOf(1f) }
    var fadeInMs by remember { mutableFloatStateOf(500f) }
    var fadeOutMs by remember { mutableFloatStateOf(500f) }
    var volumeGain by remember { mutableFloatStateOf(1.0f) }

    val tools = listOf("trim", "fade", "volume", "split", "merge")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recording?.fileName ?: "Audio Editor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Waveform display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                WaveformView(
                    peaks = null,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    progress = progress
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { progress = (progress - 0.05f).coerceAtLeast(0f) },
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text("<")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {},
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RekodiAmber),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text("▶", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { progress = (progress + 0.05f).coerceAtMost(1f) },
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(">")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tool selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tools.forEach { tool ->
                    FilterChip(
                        selected = selectedTool == tool,
                        onClick = { selectedTool = tool },
                        label = { Text(tool.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tool-specific controls
            when (selectedTool) {
                "trim" -> TrimControls(
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    onTrimStartChange = { trimStart = it },
                    onTrimEndChange = { trimEnd = it }
                )
                "fade" -> FadeControls(
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs,
                    onFadeInChange = { fadeInMs = it },
                    onFadeOutChange = { fadeOutMs = it }
                )
                "volume" -> VolumeControls(
                    gain = volumeGain,
                    onGainChange = { volumeGain = it }
                )
                "split" -> SplitControls()
                "merge" -> MergeControls()
            }
        }
    }
}

@Composable
fun WaveformView(
    peaks: FloatArray?,
    trimStart: Float,
    trimEnd: Float,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val lineColor = RekodiAmber
    val trimColor = Color.White.copy(alpha = 0.3f)
    val progressColor = Color.White.copy(alpha = 0.5f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        // Draw trim region background
        val trimLeft = width * trimStart
        val trimRight = width * trimEnd
        drawRect(color = trimColor, topLeft = Offset(trimLeft, 0f), size = androidx.compose.ui.geometry.Size(trimRight - trimLeft, height))

        // Draw progress line
        val progX = width * progress
        drawLine(color = progressColor, start = Offset(progX, 0f), end = Offset(progX, height), strokeWidth = 2f)

        // Draw simulated waveform
        if (peaks != null && peaks.isNotEmpty()) {
            val step = width / peaks.size
            for (i in peaks.indices) {
                val amp = peaks[i] * height / 2 * 0.8f
                val x = i * step
                drawLine(lineColor, Offset(x, centerY - amp), Offset(x, centerY + amp), strokeWidth = 2f)
            }
        } else {
            // Placeholder sine wave
            val samples = 200
            val step = width / samples
            for (i in 0 until samples) {
                val amp = kotlin.math.sin(i * 0.1f).toFloat() * height / 4
                val x = i * step
                drawLine(lineColor, Offset(x, centerY - amp), Offset(x, centerY + amp), strokeWidth = 2f)
            }
        }
    }
}

@Composable
fun TrimControls(
    trimStart: Float,
    trimEnd: Float,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit
) {
    Column {
        Text("Start: ${(trimStart * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(value = trimStart, onValueChange = onTrimStartChange, valueRange = 0f..trimEnd)
        Spacer(modifier = Modifier.height(8.dp))
        Text("End: ${(trimEnd * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(value = trimEnd, onValueChange = onTrimEndChange, valueRange = trimStart..1f)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ContentCut, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Apply Trim")
        }
    }
}

@Composable
fun FadeControls(
    fadeInMs: Float,
    fadeOutMs: Float,
    onFadeInChange: (Float) -> Unit,
    onFadeOutChange: (Float) -> Unit
) {
    Column {
        Text("Fade In: ${fadeInMs.toInt()}ms", style = MaterialTheme.typography.bodyMedium)
        Slider(value = fadeInMs, onValueChange = onFadeInChange, valueRange = 0f..5000f)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Fade Out: ${fadeOutMs.toInt()}ms", style = MaterialTheme.typography.bodyMedium)
        Slider(value = fadeOutMs, onValueChange = onFadeOutChange, valueRange = 0f..5000f)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Text("Apply Fade")
        }
    }
}

@Composable
fun VolumeControls(
    gain: Float,
    onGainChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeUp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Volume: ${(gain * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = gain, onValueChange = onGainChange, valueRange = 0f..3f)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Text("Normalize")
        }
    }
}

@Composable
fun SplitControls() {
    Column {
        Text("Move the playhead to the split point and tap Split.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ContentCut, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Split at Playhead")
        }
    }
}

@Composable
fun MergeControls() {
    Column {
        Text("Select clips to merge in order.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Merge, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Merge Selected")
        }
    }
}
