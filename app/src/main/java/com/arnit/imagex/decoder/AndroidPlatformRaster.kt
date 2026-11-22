package com.arnit.imagex.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.ImageFormatId
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * [PlatformRaster] implementation on top of android.graphics.BitmapFactory /
 * Bitmap.compress. Memory-safe: bounds are probed first and an inSampleSize is
 * chosen whenever the raster would exceed the allowed maximum dimension.
 */
class AndroidPlatformRaster : PlatformRaster {

    override fun decode(data: ByteArray, maxDimension: Int, enforceLimit: Boolean): RasterImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "The image data could not be read.")
        }

        val effectiveMax = if (maxDimension <= 0) Int.MAX_VALUE else maxDimension
        var sampleSize = 1
        if (enforceLimit && maxOf(srcW, srcH) > effectiveMax) {
            while (maxOf(srcW, srcH) / (sampleSize * 2) >= effectiveMax) sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
            ?: throw ConversionException(
                ErrorCode.DECODER_FAILED,
                "The device decoder could not read this image. It may be corrupted or use an unsupported variant."
            )
        return try {
            BitmapBridge.toRasterImage(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override fun encodePng(image: RasterImage, out: OutputStream) {
        encode(image, Bitmap.CompressFormat.PNG, 100, out, lossless = true)
    }

    fun encode(image: RasterImage, format: Bitmap.CompressFormat, quality: Int, out: OutputStream, lossless: Boolean) {
        val bitmap = BitmapBridge.toBitmap(image)
        try {
            val ok = bitmap.compress(format, quality.coerceIn(1, 100).let {
                if (lossless) 100 else it
            }, out)
            if (!ok) {
                throw ConversionException(ErrorCode.ENCODER_FAILED, "The device encoder refused the image.")
            }
            out.flush()
        } finally {
            bitmap.recycle()
        }
    }

    /** Probes whether the device encoder actually accepts a format before relying on it. */
    fun probeEncode(format: Bitmap.CompressFormat): Boolean {
        return try {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(-1)
            val buffer = ByteArrayOutputStream()
            val ok = try {
                bitmap.compress(format, 90, buffer)
            } finally {
                bitmap.recycle()
            }
            ok && buffer.size() > 0
        } catch (t: Throwable) {
            false
        }
    }

    fun compressFormat(format: ImageFormatId): Bitmap.CompressFormat? = when (format) {
        ImageFormatId.JPEG -> Bitmap.CompressFormat.JPEG
        ImageFormatId.PNG -> Bitmap.CompressFormat.PNG
        ImageFormatId.WEBP -> Bitmap.CompressFormat.WEBP
        else -> null
    }
}
