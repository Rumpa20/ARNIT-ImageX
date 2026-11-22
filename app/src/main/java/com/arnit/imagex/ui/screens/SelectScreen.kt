package com.arnit.imagex.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arnit.imagex.formats.FormatCatalog
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.components.BrandHeader
import com.arnit.imagex.ui.components.ErrorBanner
import com.arnit.imagex.ui.components.GradientButton
import com.arnit.imagex.ui.components.InfoBanner
import com.arnit.imagex.ui.components.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onPicked: () -> Unit,
    onBatchPicked: () -> Unit
) {
    val source by vm.source.collectAsState()
    val loading by vm.sourceLoading.collectAsState()
    val isPremium by vm.isPremium.collectAsState()
    val batchCount by vm.batchItems.collectAsState()

    var batchMode by remember { mutableStateOf(false) }
    val batchLimit = if (isPremium) FormatCatalog.PREMIUM_BATCH_LIMIT else FormatCatalog.FREE_BATCH_LIMIT

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.loadSource(uri, null) }

    val galleryBatchPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = FormatCatalog.PREMIUM_BATCH_LIMIT)
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.addBatchUris(uris)
            onBatchPicked()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.loadSource(uri, null) }

    val fileBatchPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.addBatchUris(uris)
            onBatchPicked()
        }
    }

    LaunchedEffect(source) {
        if (source != null && !batchMode) onPicked()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(26.dp))
        BrandHeader(title = "", subtitle = "Local, private, no permissions needed")

        Text(
            "Choose an image",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        SectionCard(title = "Mode") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !batchMode,
                    onClick = { batchMode = false },
                    label = { Text("Single image") }
                )
                FilterChip(
                    selected = batchMode,
                    onClick = { batchMode = true },
                    label = { Text("Batch" + if (isPremium) " (up to $batchLimit)" else " (Premium)") }
                )
            }
            if (batchMode && !isPremium) {
                InfoBanner("Batch conversion is a Premium feature; a queue holds one image in the free tier.")
            }
        }

        SectionCard(title = "Gallery") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Uses the Android Photo Picker - no storage permission is requested.",
                    style = MaterialTheme.typography.bodyMedium
                )
                GradientButton(
                    text = if (batchMode) "Select Images for Batch" else "Select from Gallery",
                    onClick = {
                        if (batchMode) {
                            galleryBatchPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            galleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    }
                )
            }
        }

        SectionCard(title = "Files") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Browse any folder - useful for TIFF, ICO, TGA, PSD and other special formats.",
                    style = MaterialTheme.typography.bodyMedium
                )
                GradientButton(
                    text = if (batchMode) "Browse Files for Batch" else "Browse Files",
                    onClick = {
                        if (batchMode) {
                            fileBatchPicker.launch(arrayOf("image/*", "application/octet-stream", "*/*"))
                        } else {
                            filePicker.launch(arrayOf("image/*", "application/octet-stream", "*/*"))
                        }
                    }
                )
            }
        }

        if (loading) {
            SectionCard(title = "Loading") {
                Text("Reading and analysing the selected image...")
            }
        }

        val sourceError = vm.sourceError
        if (sourceError != null) {
            ErrorBanner(message = sourceError.message, hint = sourceError.hint)
        }

        if (source != null) {
            SectionCard(title = "Ready") {
                Text("Image loaded: ${source!!.displayName}")
            }
        }

        if (batchCount.isNotEmpty()) {
            SectionCard(title = "Batch queue") {
                Text(
                    "${batchCount.size} image${if (batchCount.size == 1) "" else "s"} queued (limit $batchLimit).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
