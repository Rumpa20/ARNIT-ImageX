package com.arnit.imagex.encoder

import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.QualityMode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.PlatformRaster
import com.arnit.imagex.formats.ImageFormatId
import java.io.OutputStream

/**
 * Everything the engine needs to write one output format.
 * Encoders must throw [ConversionException] on failure - never crash.
 */
interface ImageWriter {
    val format: ImageFormatId

    /** Whether encoding keeps meaningful alpha information. */
    val supportsAlpha: Boolean

    /** Whether a truly lossless bitstream is possible for this format here. */
    val losslessCapable: Boolean

    fun encode(image: RasterImage, params: EncodeParams, out: OutputStream, platform: PlatformRaster)
}

/**
 * Resolved encode configuration. [quality] is 1..100 and is only meaningful
 * for lossy formats; [forceLossless] requests a lossless bitstream where
 * the target format supports it (WebP lossless, PNG often by nature...).
 */
data class EncodeParams(
    val quality: Int,
    val forceLossless: Boolean
) {
    companion object {
        fun from(
            mode: QualityMode,
            customQuality: Int,
            format: ImageFormatId,
            losslessCapable: Boolean
        ): EncodeParams {
            val losslessTarget = !format.inherentlyLossy
            val quality = if (losslessTarget) {
                100
            } else {
                when (mode) {
                    QualityMode.LOSSLESS, QualityMode.ORIGINAL -> 100
                    QualityMode.HIGH -> 92
                    QualityMode.BALANCED -> 80
                    QualityMode.SMALLER -> 60
                    else -> customQuality.coerceIn(1, 100)
                }
            }
            val forceLossless = losslessTarget && losslessCapable &&
                (mode == QualityMode.LOSSLESS || (mode == QualityMode.ORIGINAL && format == ImageFormatId.WEBP))
            return EncodeParams(quality, forceLossless)
        }
    }
}

/** Central plugin point for encoders, mirroring [com.arnit.imagex.decoder.DecoderRegistry]. */
class EncoderRegistry {
    private val writers = linkedMapOf<ImageFormatId, ImageWriter>()

    fun register(writer: ImageWriter) {
        writers[writer.format] = writer
    }

    fun writerFor(format: ImageFormatId): ImageWriter? = writers[format]

    fun encodableFormatIds(): Set<ImageFormatId> = writers.keys

    fun encode(image: RasterImage, format: ImageFormatId, params: EncodeParams, out: OutputStream, platform: PlatformRaster) {
        val writer = writers[format]
            ?: throw ConversionException(
                ErrorCode.ENCODER_UNAVAILABLE,
                "${format.displayName} cannot be encoded on this build.",
                format.description.ifBlank { null }
            )
        try {
            writer.encode(image, params, out, platform)
        } catch (e: ConversionException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw ConversionException(ErrorCode.OUT_OF_MEMORY, "Not enough memory to encode the image.", null, e)
        } catch (e: Exception) {
            throw ConversionException(
                ErrorCode.ENCODER_FAILED,
                "Encoding to ${format.displayName} failed.", e.message, e
            )
        }
    }
}
