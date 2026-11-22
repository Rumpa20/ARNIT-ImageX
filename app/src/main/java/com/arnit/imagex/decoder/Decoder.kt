package com.arnit.imagex.decoder

import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.FormatDetector
import com.arnit.imagex.formats.ImageFormatId
import java.io.OutputStream

/** Decoding limits resolved from plan + user settings. */
data class DecodeLimits(
    val maxDimension: Int,
    val allowOversizedDownscale: Boolean = false,
    val applyExifOrientation: Boolean = true
)

/**
 * Platform raster decoding (encoding too) of the formats the OS engine already
 * handles natively (JPEG/PNG/WEBP/GIF/BMP/HEIF/AVIF). Pure codecs depend on this
 * abstraction only, which is what makes them unit-testable on the JVM.
 */
interface PlatformRaster {
    fun decode(data: ByteArray, maxDimension: Int, enforceLimit: Boolean): RasterImage
    fun encodePng(image: RasterImage, out: OutputStream)
}

/**
 * A single-format decoder returning [RasterImage]. Implementations must throw
 * [ConversionException] with a precise [ErrorCode] - never crash.
 */
interface ImageReader {
    val format: ImageFormatId

    /** Whether this reader accepts the given bytes (magic/extension check). */
    fun canHandle(data: ByteArray, fileName: String?): Boolean

    fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage
}

/**
 * Central plugin point: every reader is registered here. Adding a new image
 * codec means implementing [ImageReader] and calling [register] - the UI,
 * engine and format list pick it up automatically.
 */
class DecoderRegistry(private val platformRaster: PlatformRaster) {

    private val readers = linkedMapOf<ImageFormatId, ImageReader>()

    fun register(reader: ImageReader) {
        readers[reader.format] = reader
    }

    fun readerFor(format: ImageFormatId): ImageReader? = readers[format]

    fun decodableFormatIds(): Set<ImageFormatId> = readers.keys

    /**
     * Detects the real format of the data and returns the matching reader.
     * Returns null-with-reason? No - throws exactly like the rest of the pipeline
     * so callers get a single, honest failure path.
     */
    fun detectAndDecode(
        data: ByteArray,
        fileName: String?,
        declaredMime: String?,
        limits: DecodeLimits
    ): RasterImage {
        val sniff = FormatDetector.sniffAll(data, fileName, declaredMime)
            ?: throw ConversionException(
                ErrorCode.UNSUPPORTED_FORMAT,
                "${describe(fileName)} is not a recognized image format.",
                "Supported inputs are listed on the Supported Formats screen."
            )
        val reader = readers[sniff.format]
            ?: throw ConversionException(
                ErrorCode.UNSUPPORTED_FORMAT,
                "${sniff.format.displayName} files cannot be decoded on this build.",
                sniff.format.description.ifBlank { null }
            )
        val fixExif = limits.applyExifOrientation && sniff.format in EXIF_CAPABLE_FORMATS
        return reader.decode(data, limits.copy(applyExifOrientation = fixExif), platformRaster)
    }

    fun detectFormat(data: ByteArray, fileName: String?, declaredMime: String?): ImageFormatId =
        FormatDetector.sniffAll(data, fileName, declaredMime)?.format
            ?: throw ConversionException(
                ErrorCode.UNSUPPORTED_FORMAT,
                "${describe(fileName)} is not a recognized image format."
            )

    private fun describe(fileName: String?): String =
        fileName ?: "The selected file"

    companion object {
        /** OS-decodable formats whose EXIF orientation the app serves itself. */
        val EXIF_CAPABLE_FORMATS: Set<ImageFormatId> = setOf(
            ImageFormatId.JPEG, ImageFormatId.HEIC, ImageFormatId.PNG, ImageFormatId.WEBP, ImageFormatId.AVIF
        )
    }
}
