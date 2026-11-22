@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.arnit.imagex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arnit.imagex.BuildConfig
import com.arnit.imagex.core.QualityMode
import com.arnit.imagex.settings.ThemeMode
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.components.SectionCard

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val appSettings by vm.appSettings.collectAsState()
    val isPremium by vm.isPremium.collectAsState()
    val billingState by vm.billingState.collectAsState()
    val entries by vm.historyEntries.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        SectionCard(title = "Theme") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = appSettings.theme == mode,
                        onClick = { vm.updatePreferences { it.setTheme(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size
                        )
                    ) {
                        Text(mode.label)
                    }
                }
            }
        }

        SectionCard(title = "Default output format") {
            DropdownSetting(
                label = appSettings.defaultFormat.displayName,
                options = vm.availableOutputFormats().map { it.displayName },
                selectedIndex = vm.availableOutputFormats().indexOfFirst { it == appSettings.defaultFormat }
                    .takeIf { it >= 0 } ?: 0,
                onSelect = { index ->
                    val format = vm.availableOutputFormats()[index]
                    vm.updatePreferences { it.setDefaultFormat(format) }
                }
            )
        }

        SectionCard(title = "Default quality") {
            DropdownSetting(
                label = appSettings.defaultQuality.label,
                options = QualityMode.entries.map { it.label },
                selectedIndex = QualityMode.entries.indexOf(appSettings.defaultQuality),
                onSelect = { index ->
                    val mode = QualityMode.entries[index]
                    if (vm.qualityAllowed(mode)) {
                        vm.updatePreferences { it.setDefaultQuality(mode) }
                    }
                }
            )
        }

        SectionCard(title = "Conversion history") {
            SettingSwitchRow(
                label = "Record conversion history",
                subtitle = "Metadata only: names, formats, sizes, times, status. Never image contents.",
                checked = appSettings.historyEnabled,
                onChange = { enabled -> vm.updatePreferences { it.setHistoryEnabled(enabled) } }
            )
            if (entries.isNotEmpty()) {
                OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear History (${entries.size})")
                }
            }
        }

        SectionCard(title = "Temporary files") {
            SettingSwitchRow(
                label = "Automatic cleanup",
                subtitle = "Scratch files are removed after save, cancel, failure, on startup and periodically.",
                checked = appSettings.autoCleanup,
                onChange = { enabled -> vm.updatePreferences { it.setAutoCleanup(enabled) } }
            )
            OutlinedButton(onClick = { vm.runCleanupNow() }, modifier = Modifier.fillMaxWidth()) {
                Text("Clean Up Now")
            }
        }

        SectionCard(title = "Billing test mode") {
            SettingSwitchRow(
                label = "Enable billing TEST MODE",
                subtitle = "Developer-only. Lets the Premium flow be exercised without Google Play " +
                    "Billing configured. No purchase is ever faked and no payment is taken. " +
                    "The app is clearly marked TEST MODE while this is on.",
                checked = billingState.testMode,
                onChange = { enabled -> vm.toggleTestMode(enabled) }
            )
            if (isPremium) {
                Text(
                    if (billingState.testMode) "Premium unlocked via TEST MODE."
                    else "Premium unlocked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        SectionCard(title = "Privacy") {
            Text(
                "ARNIT ImageX converts images entirely on this device. The app has no INTERNET " +
                    "permission, so images cannot be uploaded. Temporary conversion files live in " +
                    "app-private storage and are deleted after saving, cancelling, failing, on " +
                    "startup and periodically. Nothing is written to logs, databases or analytics.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SectionCard(title = "About") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ARNIT ImageX", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Universal, private, offline image conversion built with Kotlin and Jetpack Compose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DropdownSetting(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Selected") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    }
                )
            }
        }
    }
}
