package com.arnit.imagex.converter

import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ConversionProgress
import com.arnit.imagex.core.ErrorMessages
import com.arnit.imagex.core.ConversionSettings
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.ProgressListener
import com.arnit.imagex.core.QualityMode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.DecodeLimits
import com.arnit.imagex.decoder.DecoderRegistry
import com.arnit.imagex.encoder.EncoderRegistry
import com.arnit.imagex.encoder.EncodeParams
import com.arnit.imagex.formats.FormatCatalog
import com.arnit.imagex.formats.ImageFormatId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/**
 * The conversion pipeline: read source -> detect format -> decode -> EXIF
 * orientation -> (optional, user-chosen) resize -> alpha handling -> encode.
 * Runs entirely off the main thread, is cooperative-cancellable at every
 * stage, and never stores user images anywhere but the passed scratch file.
 */
class ConversionEngine(
    private val decoders: DecoderRegistry,
    private val encoders: EncoderRegistry,
    private val tank: TempTank
) {

    data class SourceInfo(
        val fileName: String,
        val declaredMime: String?,
        val declaredSizeBytes: Long,
        val openSource: () -> InputStream
    )

    data class Request(
        val source: SourceInfo,
        val settings: ConversionSettings,
        val maxDimension: Int,
        val premiumUnlocked: Boolean,
        val listener: ProgressListener? = null
    )

    data class Result(
        val outputFile: File,
        val inputFormat: ImageFormatId,
        val outputFormat: ImageFormatId,
        val originalSizeBytes: Long,
        val convertedSizeBytes: Long,
        val width: Int,
        val height: Int,
        val warnings: List<String>
    ) {
        val sizeDeltaPercent: Int
            get() = if (originalSizeBytes <= 0) 0
            else (((convertedSizeBytes - originalSizeBytes) * 100.0) / originalSizeBytes).toInt()
    }

    suspend fun convert(request: Request): Result = withContext(Dispatchers.IO) {
        val listener = request.listener
        fun report(stage: ConversionProgress.Stage, percent: Int, detail: String? = null) {
            listener?.onProgress(ConversionProgress(stage, percent, detail))
        }

        try {
            // 1. Read the source into memory (also enforces source size sanity)
            report(ConversionProgress.Stage.COPYING_SOURCE, 0)
            val data = readSource(request.source) { percent, read, total ->
                report(ConversionProgress.Stage.COPYING_SOURCE, percent, formatBytes(read) + if (total > 0) " / " + formatBytes(total) else "")
            }
            coroutineContext.ensureActive()
            if (tank.freeSpaceBytes() < RUNTIME_SAFETY_MARGIN) {
                throw ConversionException(ErrorCode.INSUFFICIENT_STORAGE, "Not enough free app cache storage to convert.")
            }

            // 2. Detect the real format from content
            val sniffedFormat = decoders.detectFormat(data, request.source.fileName, request.source.declaredMime)
            val expectedExtensionLike = formatExtensionMatches(request.source.fileName, sniffedFormat)
            val warnings = mutableListOf<String>()
            if (!expectedExtensionLike) {
                warnings.add(
                    "The file extension does not match the detected ${sniffedFormat.displayName} content; the real format was used."
                )
            }

            // 3. Decode (EXIF orientation handled inside the decoder layer)
            report(ConversionProgress.Stage.DECODING, 10)
            val limits = DecodeLimits(
                maxDimension = request.maxDimension,
                allowOversizedDownscale = request.settings.allowOversizedDownscale
            )
            val decoded = decoders.detectAndDecode(data, request.source.fileName, request.source.declaredMime, limits)
            coroutineContext.ensureActive()
            if (sniffedFormat.animatedPossible) {
                warnings.add("Animated input: only the first frame was converted.")
            }

            // 4. Optional, explicitly requested resize
            var image = decoded
            report(ConversionProgress.Stage.TRANSFORMING, 35)
            if (request.settings.resizeEnabled) {
                val (targetW, targetH) = resolveResize(request.settings, decoded)
                image = decoded.resizedTo(targetW, targetH)
            }

            // 5. Transparency handling
            val outputFormat = ImageFormatId.valueOf(request.settings.outputFormatId)
            val writer = encoders.writerFor(outputFormat)
                ?: throw ConversionException(
                    ErrorCode.ENCODER_UNAVAILABLE,
                    "${outputFormat.displayName} cannot be encoded on this build."
                )
            if (!writer.supportsAlpha && image.hasAlpha) {
                warnings.add("Transparency was flattened onto white because ${outputFormat.displayName} does not support alpha.")
                image = image.flattenedOn()
            }
            if (outputFormat.inherentlyLossy && request.settings.qualityMode in setOf(QualityMode.LOSSLESS)) {
                warnings.add("${outputFormat.displayName} cannot store pixels losslessly; maximum quality was used instead.")
            }
            coroutineContext.ensureActive()
            if (image.hasAlpha && outputFormat == ImageFormatId.JPEG) {
                warnings.add("Converting a transparent image to JPEG discards transparency.")
            }

            // 6. Encode to the private temporary tank
            report(ConversionProgress.Stage.ENCODING, 55)
            val outputFile = tank.newOutputFile(outputFormat)
            try {
                val params = EncodeParams.from(
                    request.settings.qualityMode,
                    request.settings.customQuality,
                    outputFormat,
                    writer.losslessCapable
                )
                outputFile.outputStream().use { out ->
                    encoders.encode(image, outputFormat, params, out, tank.platformRaster)
                }
                if (!outputFile.exists() || outputFile.length() <= 0L) {
                    throw ConversionException(ErrorCode.ENCODER_FAILED, "No output data was produced.")
                }
            } catch (t: Throwable) {
                outputFile.delete()
                throw t
            }

            report(ConversionProgress.Stage.DONE, 100)
            Result(
                outputFile = outputFile,
                inputFormat = sniffedFormat,
                outputFormat = outputFormat,
                originalSizeBytes = request.source.declaredSizeBytes,
                convertedSizeBytes = outputFile.length(),
                width = image.width,
                height = image.height,
                warnings = warnings
            )
        } catch (e: ConversionException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw ConversionException(ErrorCode.CANCELLED, ErrorMessages.forCode(ErrorCode.CANCELLED), cause = e)
        } catch (e: OutOfMemoryError) {
            throw ConversionException(ErrorCode.OUT_OF_MEMORY, ErrorMessages.forCode(ErrorCode.OUT_OF_MEMORY), cause = e)
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.IO_ERROR, ErrorMessages.forCode(ErrorCode.IO_ERROR, e.message), cause = e)
        }
    }

    private fun readSource(source: SourceInfo, onProgress: (Int, Long, Long) -> Unit): ByteArray {
        val out = ByteArrayOutputStream(maxOf(source.declaredSizeBytes.toInt(), 64 * 1024))
        val buffer = ByteArray(128 * 1024)
        var total = 0L
        val stream = try {
            source.openSource()
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.IO_ERROR, "The image could not be opened.", e.message, e)
        }
        stream.use { input ->
            while (true) {
                val n = try {
                    input.read(buffer)
                } catch (e: Exception) {
                    throw ConversionException(ErrorCode.IO_ERROR, "Reading the image failed.", e.message, e)
                }
                if (n < 0) break
                total += n
                if (total > HARD_SOURCE_LIMIT) {
                    throw ConversionException(ErrorCode.IMAGE_TOO_LARGE, "Source files above 512 MB are not supported.")
                }
                out.write(buffer, 0, n)
                val percent = if (source.declaredSizeBytes > 0) {
                    ((total * 100) / source.declaredSizeBytes).toInt().coerceIn(0, 9)
                } else 4
                onProgress(percent, total, source.declaredSizeBytes)
            }
        }
        if (total == 0L) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "The selected file is empty.")
        }
        return out.toByteArray()
    }

    private fun resolveResize(settings: ConversionSettings, decoded: RasterImage): Pair<Int, Int> {
        val w = settings.resizeWidth
        val h = settings.resizeHeight
        if (w <= 0 && h <= 0) return decoded.width to decoded.height
        return when {
            !settings.keepAspect -> (if (w > 0) w else decoded.width) to (if (h > 0) h else decoded.height)
            w > 0 && h > 0 -> {
                val scale = max(w.toDouble() / decoded.width, h.toDouble() / decoded.height)
                (decoded.width * scale).toInt().coerceAtLeast(1) to
                    (decoded.height * scale).toInt().coerceAtLeast(1)
            }
            w > 0 -> {
                val scale = w.toDouble() / decoded.width
                w to (decoded.height * scale).toInt().coerceAtLeast(1)
            }
            else -> {
                val scale = h.toDouble() / decoded.height
                (decoded.width * scale).toInt().coerceAtLeast(1) to h
            }
        }
    }

    private fun formatExtensionMatches(fileName: String?, actual: ImageFormatId): Boolean {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase() ?: return true
        if (ext.isEmpty()) return true
        return ext in actual.extensions
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1 shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1 shl 10 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        const val HARD_SOURCE_LIMIT = 512L * 1024 * 1024
        const val RUNTIME_SAFETY_MARGIN = 8L * 1024 * 1024

        /** Adaptive resize target clamp used when the user picks preset percentages. */
        fun clampForPlan(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> =
            if (maxOf(width, height) > maxDimension) {
                val scale = maxDimension.toDouble() / maxOf(width, height)
                (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
            } else width to height
    }
}

/** Convenience bucket the engine writes its temporary outputs into. */
class TempTank(val platformRaster: com.arnit.imagex.decoder.PlatformRaster, private val provider: () -> java.io.File) {
    private val dir: java.io.File get() = provider()

    private val extensionFor = mapOf(
        ImageFormatId.JPEG to "jpg",
        ImageFormatId.PNG to "png",
        ImageFormatId.WEBP to "webp",
        ImageFormatId.HEIC to "heic",
        ImageFormatId.AVIF to "avif",
        ImageFormatId.BMP to "bmp",
        ImageFormatId.TIFF to "tiff",
        ImageFormatId.TGA to "tga",
        ImageFormatId.ICO to "ico",
        ImageFormatId.WBMP to "wbmp",
        ImageFormatId.PNM to "ppm"
    )

    fun newOutputFile(format: ImageFormatId): File {
        val dir = dir
        if (!dir.exists()) dir.mkdirs()
        val ext = extensionFor[format] ?: "img"
        return File(dir, "arnit_${java.util.UUID.randomUUID()}.$ext")
    }

    fun freeSpaceBytes(): Long = dir.usableSpace

    fun allowDownscaleFor(request: ConversionEngine.Request): Boolean =
        request.settings.allowOversizedDownscale

    fun withinPlan(decoded: RasterImage, request: ConversionEngine.Request): Boolean =
        maxOf(decoded.width, decoded.height) <= request.maxDimension
}

/** Batch orchestration on top of [ConversionEngine]. */
class BatchConverter(private val engine: ConversionEngine) {

    data class ItemState(
        val source: ConversionEngine.SourceInfo,
        val settings: ConversionSettings,
        val status: Status,
        val result: ConversionEngine.Result? = null,
        val error: ConversionException? = null
    ) {
        enum class Status { PENDING, RUNNING, DONE, FAILED, CANCELLED }
    }

    suspend fun convertAll(
        items: List<Pair<ConversionEngine.SourceInfo, ConversionSettings>>,
        maxDimension: Int,
        premium: Boolean,
        listener: (Int, BatchItemUpdate) -> Unit
    ): List<ConversionEngine.Result?> {
        val results = arrayOfNulls<ConversionEngine.Result>(items.size)
        items.forEachIndexed { index, (source, settings) ->
            listener(index, BatchItemUpdate.Started)
            try {
                results[index] = engine.convert(
                    ConversionEngine.Request(source, settings, maxDimension, premium) { p ->
                        listener(index, BatchItemUpdate.Progress(p))
                    }
                )
                listener(index, BatchItemUpdate.Done)
            } catch (e: ConversionException) {
                if (e.code == ErrorCode.CANCELLED) throw e
                listener(index, BatchItemUpdate.Failed(e))
            }
        }
        return results.toList()
    }

    sealed class BatchItemUpdate {
        object Started : BatchItemUpdate()
        data class Progress(val progress: ConversionProgress) : BatchItemUpdate()
        object Done : BatchItemUpdate()
        data class Failed(val error: ConversionException) : BatchItemUpdate()
    }
}
