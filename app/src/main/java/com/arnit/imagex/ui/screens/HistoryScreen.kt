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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arnit.imagex.history.HistoryEntry
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.components.InfoBanner
import com.arnit.imagex.ui.components.InfoRow
import com.arnit.imagex.ui.components.SectionCard
import java.text.DateFormat
import java.util.Date

/**
 * Metadata-only conversion history. No image contents are stored or shown.
 * The whole history can be disabled in Settings; entries can be deleted
 * individually or all at once.
 */
@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val entries by vm.historyEntries.collectAsState()
    val appSettings by vm.appSettings.collectAsState()

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
                "Recent Conversions",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        InfoBanner(
            "Only names, formats, sizes, times and status are stored - never image contents. " +
                "History can be switched off in Settings."
        )

        if (!appSettings.historyEnabled) {
            SectionCard(title = "History is off") {
                Text(
                    "New conversions are not recorded. Turn history on in Settings to record future ones.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (entries.isEmpty()) {
            SectionCard(title = "Nothing here yet") {
                Text(
                    if (appSettings.historyEnabled) {
                        "Conversions you complete will appear here."
                    } else {
                        "History is disabled, so nothing was recorded."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            OutlinedButton(
                onClick = { vm.clearHistory() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear History") }
            entries.forEach { entry ->
                HistoryCard(
                    entry = entry,
                    originalSize = vm.formatBytes(entry.originalSizeBytes),
                    convertedSize = vm.formatBytes(entry.convertedSizeBytes)
                ) {
                    vm.deleteHistoryEntry(entry.id)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    originalSize: String,
    convertedSize: String,
    onDelete: () -> Unit
) {
    SectionCard(
        title = entry.fileName,
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                DateFormat.getDateTimeInstance().format(Date(entry.timestampMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            InfoRow("Input -> Output", "${entry.inputFormat} -> ${entry.outputFormat}")
            InfoRow("Original size", originalSize)
            InfoRow("Converted size", convertedSize)
            if (entry.width > 0 && entry.height > 0) {
                InfoRow("Resolution", "${entry.width} x ${entry.height}")
            }
            Text(
                entry.status,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (entry.status.startsWith("Failed")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}
