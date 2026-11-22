package com.arnit.imagex.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.arnit.imagex.converter.ConversionEngine
import com.arnit.imagex.core.ConversionProgress
import com.arnit.imagex.core.ConversionSettings
import com.arnit.imagex.core.QualityMode
import com.arnit.imagex.formats.FormatCatalog
import com.arnit.imagex.formats.ImageFormatId
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.components.ErrorBanner
import com.arnit.imagex.ui.components.GradientButton
import com.arnit.imagex.ui.components.InfoBanner
import com.arnit.imagex.ui.components.InfoRow
import com.arnit.imagex.ui.components.SectionCard
import com.arnit.imagex.ui.components.WarningBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConvertScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    onUpgrade: () -> Unit
) {
    val source by vm.source.collectAsState()
    val conversionUi by vm.conversionUi.collectAsState()
    val isPremium by vm.isPremium.collectAsState()
    val appSettings by vm.appSettings.collectAsState()

    if (source == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No image selected.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    val availableFormats = vm.availableOutputFormats()
    var outputFormat by remember(source) {
        mutableStateOf(
            if (availableFormats.contains(appSettings.defaultFormat)) {
                appSettings.defaultFormat
            } else {
                availableFormats.firstOrNull() ?: ImageFormatId.PNG
            }
        )
    }
    var qualityMode by remember(source) { mutableStateOf(appSettings.defaultQuality) }
    var customQuality by remember(source) { mutableFloatStateOf(92f) }
    var resizeEnabled by remember(source) { mutableStateOf(false) }
    var resizeWidth by remember(source) { mutableStateOf("") }
    var resizeHeight by remember(source) { mutableStateOf("") }
    var keepAspect by remember(source) { mutableStateOf(true) }
    var allowDownscale by remember(source) { mutableStateOf(false) }

    val src = source!!
    val discardWarning = !outputFormat.supportsAlpha && src.detectedAlpha

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/*")
    ) { uri ->
        if (uri != null) vm.saveResultTo(uri)
    }

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
                "Convert",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        when (val phase = conversionUi.phase) {
            is AppViewModel.ConversionPhase.Running -> {
                ProcessingCard(
                    progress = conversionUi.progress,
                    onCancel = { vm.cancelConversion() }
                )
            }

            is AppViewModel.ConversionPhase.Failed -> {
                ErrorBanner(message = phase.error.message, hint = phase.error.hint)
                OutlinedButton(onClick = { vm.resetConversion() }) { Text("Try again") }
                OutlinedButton(onClick = onBack) { Text("Choose another image") }
            }

            is AppViewModel.ConversionPhase.Done -> {
                ResultSection(
                    vm = vm,
                    result = conversionUi.result!!,
                    saved = conversionUi.saved,
                    onSave = { saveLauncher.launch(vm.suggestedFileName()) },
                    onShare = { vm.shareResult() },
                    onPickAnother = onPickAnother
                )
            }

            else -> SetupSection(
                vm = vm,
                isPremium = isPremium,
                source = src,
                selectedFormat = outputFormat,
                onSelectFormat = { format ->
                    if (vm.outputAllowed(format)) outputFormat = format else onUpgrade()
                },
                availableFormats = availableFormats,
                qualityMode = qualityMode,
                onSelectQuality = { mode ->
                    if (vm.qualityAllowed(mode)) qualityMode = mode else onUpgrade()
                },
                customQuality = customQuality,
                onCustomQuality = { customQuality = it },
                resizeEnabled = resizeEnabled,
                onResizeEnabled = { resizeEnabled = it },
                resizeWidth = resizeWidth,
                onResizeWidth = { resizeWidth = it.filter(Char::isDigit) },
                resizeHeight = resizeHeight,
                onResizeHeight = { resizeHeight = it.filter(Char::isDigit) },
                keepAspect = keepAspect,
                onKeepAspect = { keepAspect = it },
                allowDownscale = allowDownscale,
                onAllowDownscale = { allowDownscale = it },
                discardWarning = discardWarning,
                onConvert = {
                    val parsedW = resizeWidth.toIntOrNull() ?: 0
                    val parsedH = resizeHeight.toIntOrNull() ?: 0
                    vm.startConversion(
                        ConversionSettings(
                            outputFormatId = outputFormat.name,
                            qualityMode = qualityMode,
                            customQuality = customQuality.toInt(),
                            resizeEnabled = resizeEnabled && (parsedW > 0 || parsedH > 0),
                            resizeWidth = parsedW,
                            resizeHeight = parsedH,
                            keepAspect = keepAspect,
                            allowOversizedDownscale = allowDownscale
                        )
                    )
                }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupSection(
    vm: AppViewModel,
    isPremium: Boolean,
    source: AppViewModel.SourceState,
    selectedFormat: ImageFormatId,
    onSelectFormat: (ImageFormatId) -> Unit,
    availableFormats: List<ImageFormatId>,
    qualityMode: QualityMode,
    onSelectQuality: (QualityMode) -> Unit,
    customQuality: Float,
    onCustomQuality: (Float) -> Unit,
    resizeEnabled: Boolean,
    onResizeEnabled: (Boolean) -> Unit,
    resizeWidth: String,
    onResizeWidth: (String) -> Unit,
    resizeHeight: String,
    onResizeHeight: (String) -> Unit,
    keepAspect: Boolean,
    onKeepAspect: (Boolean) -> Unit,
    allowDownscale: Boolean,
    onAllowDownscale: (Boolean) -> Unit,
    discardWarning: Boolean,
    onConvert: () -> Unit
) {
    SectionCard(title = "Input") {
        val preview = source.previewImage
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = "Image preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                contentScale = ContentScale.Fit
            )
        }
        InfoRow("File", source.displayName)
        InfoRow("Format detected", source.detectedFormatName ?: "Unknown")
        InfoRow("Size", vm.formatBytes(source.sizeBytes))
        InfoRow("Resolution", "${source.width} x ${source.height}")
        InfoRow("Transparency", if (source.detectedAlpha) "Present" else "None")
        if (source.isAnimated) {
            InfoRow("Animation", "First frame will be used")
        }
    }

    SectionCard(title = "Output format") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            availableFormats.forEach { format ->
                val premiumLocked = !vm.outputAllowed(format)
                FilterChip(
                    selected = format == selectedFormat,
                    onClick = { onSelectFormat(format) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(format.displayName.substringBefore(" /"))
                            if (premiumLocked) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Premium",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                )
            }
        }
        Text(
            if (selectedFormat.supportsAlpha) {
                "This format preserves transparency."
            } else {
                "This format does not support transparency."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (selectedFormat.inherentlyLossy) {
            WarningBanner(
                "Converting to ${selectedFormat.displayName} is inherently lossy; original pixels cannot be recovered exactly."
            )
        }
    }

    SectionCard(title = "Quality") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QualityMode.entries.forEach { mode ->
                val locked = !vm.qualityAllowed(mode)
                FilterChip(
                    selected = mode == qualityMode,
                    onClick = { onSelectQuality(mode) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mode.label)
                            if (locked) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Premium",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
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
                onValueChange = onCustomQuality,
                valueRange = 10f..100f,
                steps = 17
            )
        }
    }

    SectionCard(title = "Resolution") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Resize (optional)", modifier = Modifier.weight(1f))
            Switch(checked = resizeEnabled, onCheckedChange = onResizeEnabled)
        }
        Text(
            "Original resolution is always preserved unless you enable this.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (resizeEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = resizeWidth,
                    onValueChange = onResizeWidth,
                    label = { Text("Width") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = resizeHeight,
                    onValueChange = onResizeHeight,
                    label = { Text("Height") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep aspect ratio", modifier = Modifier.weight(1f))
                Switch(checked = keepAspect, onCheckedChange = onKeepAspect)
            }
            if (!isPremium) {
                InfoBanner("Advanced resizing (upscaling, separate dimensions) is a Premium feature.")
            }
        }
    }

    val planLimit = if (isPremium) FormatCatalog.PREMIUM_MAX_DIMENSION else FormatCatalog.FREE_MAX_DIMENSION
    val oversize = maxOf(source.width, source.height) > planLimit
    if (oversize) {
        WarningBanner(
            "This image is larger than the ${planLimit}px limit. " +
                "Decode needs a controlled downscale unless Premium limits are raised."
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Allow controlled downscale for this conversion", modifier = Modifier.weight(1f))
            Switch(checked = allowDownscale, onCheckedChange = onAllowDownscale)
        }
    }

    if (discardWarning) {
        WarningBanner(
            "Transparency will be flattened onto white, because ${selectedFormat.displayName} does not support alpha."
        )
    }

    GradientButton(
        text = "Convert",
        onClick = onConvert,
        enabled = true
    )
}

@Composable
private fun ProcessingCard(progress: ConversionProgress?, onCancel: () -> Unit) {
    SectionCard(title = "Converting") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when (progress?.stage) {
                    ConversionProgress.Stage.COPYING_SOURCE -> "Reading source"
                    ConversionProgress.Stage.DECODING -> "Decoding"
                    ConversionProgress.Stage.TRANSFORMING -> "Preparing pixels"
                    ConversionProgress.Stage.ENCODING -> "Encoding"
                    ConversionProgress.Stage.DONE -> "Finishing"
                    null -> "Starting"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(progress?.detail ?: "", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { (progress?.percent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun ResultSection(
    vm: AppViewModel,
    result: ConversionEngine.Result,
    saved: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPickAnother: () -> Unit
) {
    var previewBitmap by remember(result.outputFile) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(result.outputFile) {
        previewBitmap = decodeFilePreview(result.outputFile)
    }

    SectionCard(title = "Converted") {
        previewBitmap?.let { preview ->
            Image(
                bitmap = preview,
                contentDescription = "Converted image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Fit
            )
        }
        InfoRow("Output format", result.outputFormat.displayName)
        InfoRow("Resolution", "${result.width} x ${result.height}")
        InfoRow("Original size", vm.formatBytes(result.originalSizeBytes))
        InfoRow("New size", vm.formatBytes(result.convertedSizeBytes))
        val delta = result.sizeDeltaPercent
        Text(
            text = when {
                delta < 0 -> "New file is ${-delta}% smaller than the original"
                delta > 0 -> "New file is $delta% larger than the original"
                else -> "File size unchanged"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        result.warnings.forEach { warning -> InfoBanner(warning) }
        if (saved) {
            InfoBanner("Saved to the location you selected.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
        }
        OutlinedButton(onClick = onPickAnother, modifier = Modifier.fillMaxWidth()) {
            Text("Convert Another")
        }
    }
}

private suspend fun decodeFilePreview(file: File): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        var sample = 1
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest > 0 && longest / (sample * 2) >= 1440) sample *= 2
        if (longest <= 0) return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

