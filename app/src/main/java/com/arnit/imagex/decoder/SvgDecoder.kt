package com.arnit.imagex.decoder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.FormatDetector
import com.arnit.imagex.formats.ImageFormatId
import com.caverock.androidsvg.SVG
import java.util.Locale

/** SVG reader that rasterizes at the SVG's own document size (never upscaled). */
class SvgDecoder : ImageReader {
    override val format = ImageFormatId.SVG

    override fun canHandle(data: ByteArray, fileName: String?): Boolean {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.US)
        if (ext == "svg" || ext == "svgz") return true
        val head = if (data.size > 256) data.copyOf(256) else data
        return FormatDetector.looksLikeSvg(head)
    }

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage =
        try {
            parse(data, limits)
        } catch (e: ConversionException) {
            throw e
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The SVG could not be rendered.", e.message, e)
        }

    private fun parse(data: ByteArray, limits: DecodeLimits): RasterImage {
        val text = data.toString(Charsets.UTF_8)
        val svg = try {
            SVG.getFromString(text)
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The SVG markup could not be parsed.", e.message, e)
        }

        val docW = svg.documentWidth.takeIf { it > 0f } ?: viewBoxSize(svg)?.first ?: 512f
        val docH = svg.documentHeight.takeIf { it > 0f } ?: viewBoxSize(svg)?.second ?: 512f

        var w = docW.toInt().coerceAtLeast(1)
        var h = docH.toInt().coerceAtLeast(1)

        // SVG has no fixed raster size: never fabricate resolution beyond the document
        val maxDim = limits.maxDimension
        if (maxOf(w, h) > maxDim) {
            if (!limits.allowOversizedDownscale) {
                throw ConversionException(
                    ErrorCode.IMAGE_TOO_LARGE,
                    "The SVG document is ${w}x$h which exceeds the ${limits.maxDimension}px limit.",
                    "Enable the oversized-image option to render with a controlled downscale."
                )
            }
            val scale = maxDim.toDouble() / maxOf(w, h)
            w = (w * scale).toInt().coerceAtLeast(1)
            h = (h * scale).toInt().coerceAtLeast(1)
        }

        val picture: Picture = svg.renderToPicture(w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPicture(picture)
        return try {
            BitmapBridge.toRasterImage(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun viewBoxSize(svg: SVG): Pair<Float, Float>? {
        return try {
            val rect = svg.documentViewBox ?: return null
            rect.width().toFloat() to rect.height().toFloat()
        } catch (t: Throwable) {
            null
        }
    }
}
