package com.camelcreatives.rekodi.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection("Video") {
                SettingsDropdown("Resolution", listOf("Auto", "720p", "1080p", "Native"), "Auto")
                SettingsDropdown("Frame Rate", listOf("30", "60"), "30")
                SettingsDropdown("Bitrate", listOf("Low", "Medium", "High", "Custom"), "Medium")
                SettingsDropdown("Orientation", listOf("Auto", "Portrait", "Landscape"), "Auto")
                SettingsToggle("Countdown Timer", true)
                SettingsToggle("Stop on Lock Screen", false)
            }

            SettingsSection("Audio") {
                SettingsDropdown("Source", listOf("Mute", "Microphone", "Internal", "Mic+Internal"), "Mic+Internal")
                SettingsDropdown("Sample Rate", listOf("44100", "48000"), "44100")
                SettingsDropdown("Channels", listOf("Mono", "Stereo"), "Stereo")
                SettingsToggle("Noise Suppression", false)
            }

            SettingsSection("Floating Bubble") {
                SettingsToggle("Enable Bubble", true)
                SettingsToggle("Hide During Recording", false)
            }

            SettingsSection("Zoom & Tap") {
                SettingsToggle("Enable Zoom Effect", false)
                SettingsDropdown("Style", listOf("Ripple", "Ripple+Magnifier", "Off"), "Ripple")
                SettingsToggle("Tap Counter", true)
            }

            SettingsSection("Storage") {
                SettingsToggle("Auto-delete after 30 days", false)
            }

            SettingsSection("Appearance") {
                SettingsDropdown("Theme", listOf("System", "Light", "Dark"), "System")
                SettingsToggle("Dynamic Color (Material You)", true)
                SettingsDropdown("Language", listOf("English", "Kiswahili"), "English")
            }

            SettingsSection("About") {
                Text(
                    text = "Rekodi v1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Made by Camel Creatives, Tanzania \uD83C\uDDF9\uD83C\uDDFF",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsDropdown(
    label: String,
    options: List<String>,
    defaultOption: String
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(defaultOption) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selected = option
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    defaultChecked: Boolean
) {
    var checked by remember { mutableStateOf(defaultChecked) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { checked = it }
        )
    }
}
