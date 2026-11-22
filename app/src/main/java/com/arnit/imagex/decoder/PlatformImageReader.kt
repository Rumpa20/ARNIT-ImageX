package com.arnit.imagex.decoder

import com.arnit.imagex.core.ExifReader
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.FormatDetector
import com.arnit.imagex.formats.ImageFormatId

/**
 * Reader for the formats decoded by the platform's own bitmap codecs
 * (JPEG/PNG/WEBP/BMP/GIF/HEIC/AVIF). Only needed HEIF/AVIF variants are
 * registered by the codec factory when the OS can decode them.
 */
class PlatformImageReader(
    override val format: ImageFormatId,
    private val platform: PlatformRaster
) : ImageReader {

    override fun canHandle(data: ByteArray, fileName: String?): Boolean {
        val sniff = FormatDetector.sniffAll(data, null, format.mime) ?: return false
        return sniff.format == format
    }

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage {
        val image = try {
            platform.decode(data, limits.maxDimension, enforceLimit = !limits.allowOversizedDownscale)
        } catch (e: ConversionException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw ConversionException(ErrorCode.OUT_OF_MEMORY, "Not enough memory to decode this image.", null, e)
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The image could not be decoded.", e.message, e)
        }
        if (limits.applyExifOrientation && format in DecoderRegistry.EXIF_CAPABLE_FORMATS) {
            val orientation = ExifReader.orientationOf(data)
            if (orientation in 2..8) return image.applyExifOrientation(orientation)
        }
        return image
    }
}
