package com.arnit.imagex.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arnit.imagex.billing.BillingGateway
import com.arnit.imagex.billing.BillingState
import com.arnit.imagex.billing.PlanId
import com.arnit.imagex.billing.TestModeBillingGateway
import com.arnit.imagex.converter.ConversionEngine
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ConversionProgress
import com.arnit.imagex.core.ConversionSettings
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.QualityMode
import com.arnit.imagex.decoder.BitmapBridge
import com.arnit.imagex.decoder.DecodeLimits
import com.arnit.imagex.di.ServiceLocator
import com.arnit.imagex.formats.FormatCatalog
import com.arnit.imagex.formats.CodecFactory
import com.arnit.imagex.formats.FormatSupport
import com.arnit.imagex.formats.ImageFormatId
import com.arnit.imagex.history.HistoryEntry
import com.arnit.imagex.history.HistoryRepository
import com.arnit.imagex.premium.PremiumRepository
import com.arnit.imagex.settings.AppSettings
import com.arnit.imagex.settings.SettingsRepository
import com.arnit.imagex.storage.DocumentStore
import com.arnit.imagex.storage.TempFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File

/**
 * App-wide state holder. Holds NO image content across conversions except the
 * current in-flight one, and never logs or persists pixel data.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val appScope = viewModelScope
    private val settings = ServiceLocator.settings(application)
    private val history = ServiceLocator.history(application)
    private val premium = ServiceLocator.premium(application)
    private val billing = ServiceLocator.billing(application)
    private val temp = ServiceLocator.tempFileManager(application)
    private val engine = ServiceLocator.engine(application)
    private val decoders = CodecFactory.createDecoders(ServiceLocator.platformRaster(application))

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    val isPremium: StateFlow<Boolean> = premium.isPremium
    val billingState: StateFlow<BillingState> = billing.state

    val historyEntries: StateFlow<List<HistoryEntry>> = history.entries

    // ------------------------------------------------------------------
    // Source selection / inspection
    // ------------------------------------------------------------------

    data class SourceState(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val mime: String?,
        val bytes: ByteArray,
        val detectedFormatName: String?,
        val detectedAlpha: Boolean,
        val isAnimated: Boolean,
        val width: Int,
        val height: Int,
        val previewImage: ImageBitmap?
    )

    private val _source = MutableStateFlow<SourceState?>(null)
    val source: StateFlow<SourceState?> = _source

    private val _sourceLoading = MutableStateFlow(false)
    val sourceLoading: StateFlow<Boolean> = _sourceLoading

    private var _sourceError: ConversionException? = null
    val sourceError: ConversionException? get() = _sourceError

    fun loadSource(uri: Uri, mimeHint: String?) {
        appScope.launch {
            _sourceLoading.value = true
            _sourceError = null
            _source.value = null
            try {
                val picked = DocumentStore.probe(app, uri, mimeHint)
                val bytes = DocumentStore.readBytes(app, uri)
                val detected = decoders.detectFormat(bytes, picked.displayName, picked.mime)
                val inspect = decoders.detectAndDecode(
                    bytes, picked.displayName, picked.mime,
                    DecodeLimits(maxDimension = INSPECT_MAX_DIMENSION, allowOversizedDownscale = true)
                )
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val realW = bounds.outWidth.takeIf { it > 0 } ?: inspect.width
                val realH = bounds.outHeight.takeIf { it > 0 } ?: inspect.height
                val previewBitmap = BitmapBridge.toBitmap(inspect).asImageBitmap()
                _source.value = SourceState(
                    uri = uri,
                    displayName = picked.displayName,
                    sizeBytes = bytes.size.toLong(),
                    mime = picked.mime,
                    bytes = bytes,
                    detectedFormatName = detected.displayName,
                    detectedAlpha = inspect.hasAlpha,
                    isAnimated = detected.animatedPossible,
                    width = realW,
                    height = realH,
                    previewImage = previewBitmap
                )
            } catch (e: ConversionException) {
                _sourceError = e
            } catch (e: Exception) {
                _sourceError = ConversionException(
                    ErrorCode.IO_ERROR, "The file could not be loaded.", e.message, e
                )
            } finally {
                _sourceLoading.value = false
            }
        }
    }

    fun clearSource() {
        _source.value = null
        _sourceError = null
    }

    private val encoders = CodecFactory.createEncoders(ServiceLocator.platformRaster(app))

    val outputFormats: List<ImageFormatId> = FormatCatalog.outputOrder(encoders.encodableFormatIds())

    fun availableOutputFormats(): List<ImageFormatId> = outputFormats

    fun formatSupports(): List<FormatSupport> = FormatCatalog.supports(decoders, encoders)

    fun outputAllowed(format: ImageFormatId): Boolean = premium.outputAllowed(format)

    fun qualityAllowed(mode: QualityMode): Boolean = premium.qualityAllowed(mode)

    fun planMaxDimension(): Int = premium.limits().maxDimension

    fun formatBytes(bytes: Long): String = engine.formatBytes(bytes)

    // ------------------------------------------------------------------
    // Single conversion
    // ------------------------------------------------------------------

    sealed class ConversionPhase {
        data object Setup : ConversionPhase()
        data object Running : ConversionPhase()
        data object Done : ConversionPhase()
        data class Failed(val error: ConversionException) : ConversionPhase()
    }

    data class ConversionUi(
        val phase: ConversionPhase = ConversionPhase.Setup,
        val progress: ConversionProgress? = null,
        val result: ConversionEngine.Result? = null,
        val saved: Boolean = false
    )

    private val _conversionUi = MutableStateFlow(ConversionUi())
    val conversionUi: StateFlow<ConversionUi> = _conversionUi

    private var conversionJob: Job? = null

    fun startConversion(outputSettings: ConversionSettings) {
        val src = _source.value ?: return
        if (_conversionUi.value.phase == ConversionPhase.Running) return

        val limits = premium.limits()
        val sourceInfo = ConversionEngine.SourceInfo(
            fileName = src.displayName,
            declaredMime = src.mime,
            declaredSizeBytes = src.sizeBytes,
            openSource = { ByteArrayInputStream(src.bytes) }
        )

        _conversionUi.value = ConversionUi(phase = ConversionPhase.Running)
        conversionJob = appScope.launch {
            try {
                val result = engine.convert(
                    ConversionEngine.Request(
                        source = sourceInfo,
                        settings = outputSettings,
                        maxDimension = limits.maxDimension,
                        premiumUnlocked = premium.isPremium.value
                    ) { progress ->
                        _conversionUi.value = _conversionUi.value.copy(progress = progress)
                    }
                )
                recordHistory(result, _source.value?.displayName ?: result.outputFile.name)
                _conversionUi.value = ConversionUi(
                    phase = ConversionPhase.Done,
                    progress = ConversionProgress(ConversionProgress.Stage.DONE, 100),
                    result = result
                )
            } catch (e: ConversionException) {
                _conversionUi.value = ConversionUi(phase = ConversionPhase.Failed(e))
            } catch (e: Exception) {
                _conversionUi.value = ConversionUi(
                    phase = ConversionPhase.Failed(
                        ConversionException(ErrorCode.IO_ERROR, e.message ?: "Unexpected error.")
                    )
                )
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        _conversionUi.value = ConversionUi(phase = ConversionPhase.Setup)
    }

    fun resetConversion() {
        conversionJob?.cancel()
        _conversionUi.value = ConversionUi()
    }

    private suspend fun recordHistory(result: ConversionEngine.Result, originalName: String) {
        if (!appSettings.value.historyEnabled) return
        history.record(
            HistoryEntry(
                fileName = originalName,
                inputFormat = result.inputFormat.displayName,
                outputFormat = result.outputFormat.displayName,
                timestampMs = System.currentTimeMillis(),
                originalSizeBytes = result.originalSizeBytes,
                convertedSizeBytes = result.convertedSizeBytes,
                status = "Success",
                width = result.width,
                height = result.height
            )
        )
    }

    // ------------------------------------------------------------------
    // Save / share
    // ------------------------------------------------------------------

    fun suggestedFileName(): String {
        val result = _conversionUi.value.result ?: return "converted.png"
        val base = _source.value?.displayName?.substringBeforeLast('.') ?: "image"
        return "$base.${result.outputFormat.extensions.first()}"
    }

    fun saveResultTo(destination: Uri): Boolean {
        val result = _conversionUi.value.result ?: return false
        return try {
            DocumentStore.saveTo(app, result.outputFile, destination)
            _conversionUi.value = _conversionUi.value.copy(saved = true)
            viewModelScope.launch { history.record(
                HistoryEntry(
                    fileName = destination.lastPathSegment ?: suggestedFileName(),
                    inputFormat = result.inputFormat.displayName,
                    outputFormat = result.outputFormat.displayName,
                    timestampMs = System.currentTimeMillis(),
                    originalSizeBytes = result.originalSizeBytes,
                    convertedSizeBytes = result.convertedSizeBytes,
                    status = "Saved",
                    width = result.width,
                    height = result.height
                )
            ) }
            true
        } catch (e: ConversionException) {
            e.message?.let { android.util.Log.w(TAG, "save failed: $it") }
            false
        }
    }

    fun shareResult() {
        val result = _conversionUi.value.result ?: return
        val file = result.outputFile
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = result.outputFormat.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share converted image").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            app.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // Batch conversion
    // ------------------------------------------------------------------

    data class BatchItemUi(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val mime: String?,
        val status: String = STATUS_QUEUED,
        val percent: Int = 0,
        val resultSizeBytes: Long = -1L,
        val error: ConversionException? = null
    ) {
        val done: Boolean get() = status == STATUS_DONE
        val failed: Boolean get() = status.startsWith(STATUS_FAILED)
    }

    private val _batchItems = MutableStateFlow<List<BatchItemUi>>(emptyList())
    val batchItems: StateFlow<List<BatchItemUi>> = _batchItems

    private val _batchRunning = MutableStateFlow(false)
    val batchRunning: StateFlow<Boolean> = _batchRunning

    private var batchJob: Job? = null
    private val batchOutputFiles = LinkedHashMap<Uri, File>()
    private val batchOutputNames = HashMap<Uri, String>()

    fun addBatchItems(items: List<DocumentStore.PickedFile>) {
        val limit = premium.limits().batchLimit
        val current = _batchItems.value.toMutableList()
        for (item in items) {
            if (current.size >= limit) break
            if (current.none { it.uri == item.uri }) {
                current.add(BatchItemUi(item.uri, item.displayName, item.sizeBytes, item.mime))
            }
        }
        _batchItems.value = current
    }

    /** Probes every picked URI on a worker thread; corrupt/unreadable picks are skipped. */
    fun addBatchUris(uris: List<Uri>) {
        appScope.launch(Dispatchers.IO) {
            val picked = uris.mapNotNull { uri ->
                try {
                    DocumentStore.probe(app, uri, null)
                } catch (_: Exception) {
                    null
                }
            }
            addBatchItems(picked)
        }
    }

    fun clearBatch() {
        if (_batchRunning.value) return
        _batchItems.value = emptyList()
        batchOutputFiles.clear()
        batchOutputNames.clear()
    }

    fun runBatch(outputFormatName: String, qualityModeName: String, customQuality: Int) {
        if (_batchRunning.value) return
        val items = _batchItems.value.filter { !it.done }
        if (items.isEmpty()) return
        val limits = premium.limits()
        val running = _batchItems.value.toMutableList()
        _batchRunning.value = true
        batchJob = appScope.launch {
            try {
                for (item in items) {
                    val listIndex = running.indexOfFirst { it.uri == item.uri }
                    if (listIndex < 0) continue
                    running[listIndex] = running[listIndex].copy(status = STATUS_RUNNING, percent = 0)
                    _batchItems.value = running.toList()
                    try {
                        val result = engine.convert(
                            ConversionEngine.Request(
                                source = ConversionEngine.SourceInfo(
                                    fileName = item.displayName,
                                    declaredMime = item.mime,
                                    declaredSizeBytes = item.sizeBytes
                                ) {
                                    app.contentResolver.openInputStream(item.uri)
                                        ?: throw java.io.IOException("The selected image could not be opened. It may have been moved or deleted.")
                                },
                                settings = ConversionSettings(
                                    outputFormatId = outputFormatName,
                                    qualityMode = QualityMode.valueOf(qualityModeName),
                                    customQuality = customQuality
                                ),
                                maxDimension = limits.maxDimension,
                                premiumUnlocked = premium.isPremium.value
                            ) { p ->
                                running[listIndex] = running[listIndex].copy(percent = p.percent)
                                _batchItems.value = running.toList()
                            }
                        )
                        batchOutputFiles[item.uri] = result.outputFile
                        batchOutputNames[item.uri] =
                            item.displayName.substringBeforeLast('.') + "." + result.outputFormat.extensions.first()
                        recordBatchHistory(item, result)
                        running[listIndex] = running[listIndex].copy(
                            status = STATUS_DONE, percent = 100, resultSizeBytes = result.convertedSizeBytes
                        )
                    } catch (e: ConversionException) {
                        if (e.code == ErrorCode.CANCELLED) throw e
                        running[listIndex] = running[listIndex].copy(status = "$STATUS_FAILED ${e.message}", error = e)
                    }
                    _batchItems.value = running.toList()
                }
            } catch (e: ConversionException) {
                _batchItems.value = running.toList().map {
                    if (!it.done) it.copy(status = "$STATUS_FAILED ${e.message}") else it
                }
            } finally {
                _batchRunning.value = false
            }
        }
    }

    private suspend fun recordBatchHistory(item: BatchItemUi, result: ConversionEngine.Result) {
        if (!appSettings.value.historyEnabled) return
        history.record(
            HistoryEntry(
                fileName = item.displayName,
                inputFormat = result.inputFormat.displayName,
                outputFormat = result.outputFormat.displayName,
                timestampMs = System.currentTimeMillis(),
                originalSizeBytes = result.originalSizeBytes,
                convertedSizeBytes = result.convertedSizeBytes,
                status = "Success (batch)",
                width = result.width,
                height = result.height
            )
        )
    }

    private val _batchSaved = MutableStateFlow(false)
    val batchSaved: StateFlow<Boolean> = _batchSaved

    /**
     * Writes every finished batch output into the user-chosen tree via SAF.
     * The temporary copies are deleted on success; failures are reported and
     * kept so the user can retry.
     */
    fun saveAllBatchOutputs(treeUri: Uri) {
        if (_batchRunning.value) return
        appScope.launch(Dispatchers.IO) {
            val outputs = pendingBatchOutputs()
            if (outputs.isEmpty()) return@launch
            val failures = DocumentStore.saveAllToTree(app, treeUri, outputs)
            if (failures.isEmpty()) {
                batchOutputFiles.clear()
                batchOutputNames.clear()
                _batchSaved.value = true
                _batchItems.value = _batchItems.value.map { it.copy(status = STATUS_SAVED) }
            } else {
                _batchSaved.value = false
            }
        }
    }

    fun clearBatchSavedFlag() {
        _batchSaved.value = false
    }

    fun cancelBatch() {
        batchJob?.cancel()
        _batchRunning.value = false
        _batchItems.value = _batchItems.value.map {
            if (!it.done && !it.failed) it.copy(status = STATUS_QUEUED, percent = 0) else it
        }
    }

    fun pendingBatchOutputs(): List<Pair<File, String>> =
        batchOutputFiles.entries.mapNotNull { (uri, file) ->
            if (file.exists()) file to (batchOutputNames[uri] ?: file.name) else null
        }

    fun clearBatchOutputs() {
        batchOutputFiles.values.forEach { temp.delete(it) }
        batchOutputFiles.clear()
        batchOutputNames.clear()
        _batchItems.value = emptyList()
    }

    // ------------------------------------------------------------------
    // Premium / settings
    // ------------------------------------------------------------------

    fun purchase(plan: PlanId) {
        appScope.launch { billing.purchase(plan) }
    }

    fun restorePurchases() {
        appScope.launch { billing.restore() }
    }

    fun activateVerifiedCashfreePremium(plan: String?) {
        val mapped = when (plan?.lowercase()) {
            "monthly" -> PlanId.Monthly
            "yearly" -> PlanId.Yearly
            "lifetime" -> PlanId.Lifetime
            else -> null
        }
        premium.setExternalEntitlement(true, mapped)
    }

    fun toggleTestMode(enabled: Boolean) {
        (billing as? TestModeBillingGateway)?.toggleTestMode(enabled)
    }

    fun updatePreferences(update: suspend (SettingsRepository) -> Unit) {
        appScope.launch { update(settings) }
    }

    fun clearHistory() {
        appScope.launch { history.clearAll() }
    }

    fun deleteHistoryEntry(id: String) {
        appScope.launch { history.delete(id) }
    }

    fun runCleanupNow() {
        temp.cleanupAll()
    }

    companion object {
        const val INSPECT_MAX_DIMENSION = 2048
        const val STATUS_QUEUED = "Queued"
        const val STATUS_RUNNING = "Converting"
        const val STATUS_DONE = "Done"
        const val STATUS_SAVED = "Saved"
        const val STATUS_FAILED = "Failed:"
        private const val TAG = "AppViewModel"
    }
}
