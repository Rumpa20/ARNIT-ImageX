package com.arnit.imagex.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arnit.imagex.core.QualityMode
import com.arnit.imagex.formats.FormatCatalog
import com.arnit.imagex.formats.ImageFormatId
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.components.GradientButton
import com.arnit.imagex.ui.components.InfoBanner
import com.arnit.imagex.ui.components.InfoRow
import com.arnit.imagex.ui.components.SectionCard
import com.arnit.imagex.ui.components.WarningBanner

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAddImages: () -> Unit,
    onUpgrade: () -> Unit
) {
    val items by vm.batchItems.collectAsState()
    val running by vm.batchRunning.collectAsState()
    val saved by vm.batchSaved.collectAsState()
    val isPremium by vm.isPremium.collectAsState()
    val appSettings by vm.appSettings.collectAsState()

    if (!isPremium) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BatchTopBar(onBack)
            SectionCard(title = "Premium feature") {
                Text(
                    "Batch conversion queues up to ${FormatCatalog.PREMIUM_BATCH_LIMIT} images and " +
                        "converts them in one run with one shared output format and quality.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            GradientButton(text = "Upgrade to Premium", onClick = onUpgrade)
            Spacer(Modifier.height(24.dp))
        }
        return
    }

    val availableFormats = vm.availableOutputFormats()
    var outputFormat by remember {
        mutableStateOf(availableFormats.firstOrNull { it == appSettings.defaultFormat } ?: ImageFormatId.PNG)
    }
    var qualityMode by remember { mutableStateOf(appSettings.defaultQuality) }
    var customQuality by remember { mutableFloatStateOf(92f) }

    val saveTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) vm.saveAllBatchOutputs(treeUri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BatchTopBar(onBack)

        SectionCard(
            title = "Queue",
            trailing = {
                if (items.isNotEmpty() && !running) {
                    OutlinedButton(onClick = { vm.clearBatch() }) { Text("Clear") }
                }
            }
        ) {
            if (items.isEmpty()) {
                Text(
                    "No images queued. Add up to ${FormatCatalog.PREMIUM_BATCH_LIMIT} images.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                InfoRow("Images", "${items.size} / ${FormatCatalog.PREMIUM_BATCH_LIMIT}")
            }
            OutlinedButton(onClick = onAddImages, enabled = !running) {
                Text(if (items.isEmpty()) "Add Images" else "Add More Images")
            }
        }

        if (items.isNotEmpty()) {
            SectionCard(title = "Items") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { item ->
                        BatchItemRow(item, formatSize = { vm.formatBytes(it) })
                    }
                }
            }
        }

        if (items.isNotEmpty()) {
            SectionCard(title = "Output settings for all items") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableFormats.forEach { format ->
                        val locked = !vm.outputAllowed(format)
                        FilterChip(
                            selected = format == outputFormat,
                            onClick = {
                                if (locked) onUpgrade() else outputFormat = format
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(format.displayName.substringBefore(" /"))
                                    if (locked) {
                                        Icon(
                                            Icons.Filled.Lock,
                                            contentDescription = "Premium",
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .size(14.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualityMode.entries.forEach { mode ->
                        val locked = !vm.qualityAllowed(mode)
                        FilterChip(
                            selected = mode == qualityMode,
                            onClick = {
                                if (locked) onUpgrade() else qualityMode = mode
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(mode.label)
                                    if (locked) {
                                        Icon(
                                            Icons.Filled.Lock,
                                            contentDescription = "Premium",
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .size(14.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                if (qualityMode == QualityMode.CUSTOM) {
                    Text("Quality: ${customQuality.toInt()}%")
                    Slider(
                        value = customQuality,
                        onValueChange = { customQuality = it },
                        valueRange = 10f..100f,
                        steps = 17
                    )
                }
            }
        }

        if (running) {
            WarningBanner("Batch is running. Cancelling keeps finished outputs and returns the rest to the queue.")
            OutlinedButton(onClick = { vm.cancelBatch() }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel Batch")
            }
        } else if (items.any { !it.done }) {
            GradientButton(
                text = "Convert All (${items.count { !it.done }})",
                enabled = true,
                onClick = {
                    vm.clearBatchSavedFlag()
                    vm.runBatch(outputFormat.name, qualityMode.name, customQuality.toInt())
                }
            )
        }

        if (items.any { it.done } && !running) {
            GradientButton(text = "Save All to Folder", onClick = { saveTreeLauncher.launch(null) })
            InfoBanner(
                "Save All writes the finished images into the folder you choose. " +
                    "The temporary copies are deleted afterwards."
            )
        }
        if (saved) {
            InfoBanner("All converted images were saved to the folder you selected.")
        }
        if (items.any { it.failed }) {
            WarningBanner("One or more images failed; their rows explain the reason. Everything else was converted.")
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BatchTopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            "Batch Convert",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun BatchItemRow(
    item: AppViewModel.BatchItemUi,
    formatSize: (Long) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
        Text(
            when {
                item.failed -> "Failed - ${item.error?.message ?: "unknown error"}"
                item.status == AppViewModel.STATUS_SAVED -> "Saved"
                item.done -> "Done - ${formatSize(item.resultSizeBytes.coerceAtLeast(0L))}"
                item.status == AppViewModel.STATUS_RUNNING -> "Converting - ${item.percent}%"
                else -> "Queued - ${formatSize(item.sizeBytes.takeIf { it >= 0 } ?: 0L)}"
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            color = if (item.failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        if (item.status == AppViewModel.STATUS_RUNNING) {
            LinearProgressIndicator(
                progress = { item.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
