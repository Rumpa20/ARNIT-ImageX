package com.arnit.imagex.encoder

import android.graphics.Bitmap
import android.os.Build
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.AndroidPlatformRaster
import com.arnit.imagex.decoder.BitmapBridge
import com.arnit.imagex.decoder.PlatformRaster
import com.arnit.imagex.formats.ImageFormatId
import java.io.OutputStream

/**
 * Encoders backed by the platform bitmap codecs (JPEG, PNG, WebP).
 * The public Android SDK exposes no HEIF/AVIF encoder, so those formats are
 * decode-only in this build and are never registered as encoders.
 */
class AndroidBitmapEncoder(
    override val format: ImageFormatId,
    private val androidPlatform: AndroidPlatformRaster
) : ImageWriter {

    override val supportsAlpha: Boolean = format != ImageFormatId.JPEG
    override val losslessCapable: Boolean = format == ImageFormatId.PNG ||
        (format == ImageFormatId.WEBP && Build.VERSION.SDK_INT >= 30)

    override fun encode(image: RasterImage, params: EncodeParams, out: OutputStream, platform: PlatformRaster) {
        val target = when {
            format == ImageFormatId.PNG -> Bitmap.CompressFormat.PNG
            format == ImageFormatId.JPEG -> Bitmap.CompressFormat.JPEG
            format == ImageFormatId.WEBP && params.forceLossless && Build.VERSION.SDK_INT >= 30 ->
                Bitmap.CompressFormat.WEBP_LOSSLESS
            format == ImageFormatId.WEBP -> Bitmap.CompressFormat.WEBP
            else -> throw ConversionException(
                ErrorCode.ENCODER_UNAVAILABLE,
                "${format.displayName} encoding is not available. The Android platform exposes a public encoder for JPEG, PNG and WebP; this build never claims encoding for formats it cannot write."
            )
        }
        if (!PlatformEncoderAvailability.canEncode(androidPlatform, target)) {
            throw ConversionException(
                ErrorCode.ENCODER_UNAVAILABLE,
                "This device does not provide a working ${format.displayName} encoder.",
                "Choose another output format; PNG and WebP are always available."
            )
        }

        val toWrite = if (!supportsAlpha && image.hasAlpha) image.flattenedOn() else image
        val bitmap = BitmapBridge.toBitmap(toWrite)
        try {
            val ok = bitmap.compress(target, params.quality.coerceIn(1, 100).let { if (!format.inherentlyLossy && format != ImageFormatId.WEBP) 100 else it }, out)
            if (!ok) {
                throw ConversionException(ErrorCode.ENCODER_FAILED, "Encoding to ${format.displayName} failed.")
            }
            out.flush()
        } finally {
            bitmap.recycle()
        }

    }
}

/** Caches probe results so repeated conversions check the device only once. */
object PlatformEncoderAvailability {
    private val cache = HashMap<Bitmap.CompressFormat, Boolean>()

    fun canEncode(platform: AndroidPlatformRaster, target: Bitmap.CompressFormat): Boolean =
        when (target) {
            Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.WEBP -> true
            else -> cache.getOrPut(target) { platform.probeEncode(target) }
        }
}
