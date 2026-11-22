package com.arnit.imagex.decoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.ImageFormatId
import java.util.Locale

/**
 * Targa (TGA) decoder supporting: types 1/2/3/9/10/11, colormaps,
 * 8/16/24/32 bpp, RLE raw runs and the TGA-2 footer signature.
 */
class TgaDecoder : ImageReader {
    override val format = ImageFormatId.TGA

    override fun canHandle(data: ByteArray, fileName: String?): Boolean {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.US)
        if (ext in setOf("tga", "icb", "vda", "vst")) return true
        // TGA-2 footer contains "TRUEVISION-XFILE"
        if (data.size >= 18) {
            val footer = data.copyOfRange(data.size - 18, data.size)
            if (String(footer, Charsets.US_ASCII).contains("TRUEVISION-XFILE")) return true
        }
        return false
    }

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage {
        return try {
            parse(data, limits)
        } catch (e: ConversionException) {
            throw e
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The TGA image could not be decoded.", e.message, e)
        }
    }

    private fun parse(data: ByteArray, limits: DecodeLimits): RasterImage {
        if (data.size < 18) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TGA file is too short.")
        val idLength = data[0].toInt() and 0xFF
        val colorMapType = data[1].toInt() and 0xFF
        val imageType = data[2].toInt() and 0xFF

        if (imageType == 0) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TGA file has no image data.")
        val rle = imageType >= 9
        if (imageType !in setOf(1, 2, 3, 9, 10, 11)) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported TGA image type $imageType.")
        }

        val cmapFirst = ByteKit.le16(data, 3)
        val cmapCount = ByteKit.le16(data, 5)
        val cmapBpp = data[7].toInt() and 0xFF
        val width = ByteKit.le16(data, 12)
        val height = ByteKit.le16(data, 14)
        if (width <= 0 || height <= 0) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TGA has invalid dimensions.")
        checkSize(width, height, limits)

        val bpp = data[16].toInt() and 0xFF
        val descriptor = data[17].toInt() and 0xFF
        val topOrigin = descriptor and 0x20 != 0
        val alphaBits = descriptor and 0x0F

        var p = 18 + idLength

        val cmap: IntArray? = if (colorMapType == 1) {
            if (p + cmapCount * ((cmapBpp + 7) / 8) > data.size) {
                throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TGA colormap is truncated.")
            }
            IntArray(cmapCount) { idx -> readColor(data, p + idx * ((cmapBpp + 7) / 8), cmapBpp) }
        } else null

        val bppBytes = (bpp + 7) / 8
        if (!rle && p + width * height * bppBytes > data.size) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TGA data is truncated.")
        }

        val pixels = IntArray(width * height)

        var i = p
        fun readRawPixel(): Int {
            val value = when {
                imageType in setOf(1, 9) && cmap != null -> {
                    val index = data[i].toInt() and 0xFF
                    i++
                    cmap[cmapFirst + index]
                }
                bpp == 8 && imageType in setOf(3, 11) -> {
                    val v = data[i].toInt() and 0xFF
                    i++
                    gray(v)
                }
                bpp == 16 -> {
                    val v = ByteKit.le16(data, i)
                    i += 2
                    val r = ByteKit.expand5((v shr 10) and 0x1F)
                    val g = ByteKit.expand5((v shr 5) and 0x1F)
                    val b = ByteKit.expand5(v and 0x1F)
                    0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                }
                bpp == 24 -> {
                    val b = data[i].toInt() and 0xFF
                    val g = data[i + 1].toInt() and 0xFF
                    val r = data[i + 2].toInt() and 0xFF
                    i += 3
                    0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                }
                bpp == 32 -> {
                    val b = data[i].toInt() and 0xFF
                    val g = data[i + 1].toInt() and 0xFF
                    val r = data[i + 2].toInt() and 0xFF
                    val a = data[i + 3].toInt() and 0xFF
                    i += 4
                    if (alphaBits == 0) 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                    else (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                else -> throw ConversionException(
                    ErrorCode.DECODER_FAILED, "Unsupported TGA bit depth: $bpp bits."
                )
            }
            return value
        }

        var idx = 0
        if (rle) {
            while (idx < pixels.size) {
                if (i >= data.size) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TGA RLE data is truncated.")
                val packet = data[i].toInt()
                i++
                val count = (packet and 0x7F) + 1
                if (packet and 0x80 != 0) {
                    val value = readRawPixel()
                    repeat(count) { if (idx < pixels.size) pixels[idx++] = value }
                } else {
                    repeat(count) { if (idx < pixels.size) pixels[idx++] = readRawPixel() }
                }
            }
        } else {
            while (idx < pixels.size) {
                if (i >= data.size) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TGA data is truncated.")
                pixels[idx++] = readRawPixel()
            }
        }

        // Fix all-black alpha with zero alpha bits (common in legacy 32-bit TGAs)
        if (bpp == 32 && alphaBits == 0) {
            val allOpaque = pixels.any { it ushr 24 == 0xFF }
            if (!allOpaque) {
                for (j in pixels.indices) pixels[j] = 0xFF000000.toInt() or (pixels[j] and 0xFFFFFF)
            }
        }

        val oriented = if (topOrigin) pixels else flipRows(pixels, width, height)
        return RasterImage(width, height, oriented)
    }

    private fun readColor(data: ByteArray, offset: Int, bpp: Int): Int = when (bpp) {
        15, 16 -> {
            val v = ByteKit.le16(data, offset)
            val r = ByteKit.expand5((v shr 10) and 0x1F)
            val g = ByteKit.expand5((v shr 5) and 0x1F)
            val b = ByteKit.expand5(v and 0x1F)
            0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        24 -> {
            val b = data[offset].toInt() and 0xFF
            val g = data[offset + 1].toInt() and 0xFF
            val r = data[offset + 2].toInt() and 0xFF
            0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        32 -> {
            val b = data[offset].toInt() and 0xFF
            val g = data[offset + 1].toInt() and 0xFF
            val r = data[offset + 2].toInt() and 0xFF
            val a = data[offset + 3].toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        else -> 0xFF000000.toInt()
    }

    private fun gray(v: Int): Int {
        val rgba = v
        val c = rgba and 0xFF
        return 0xFF000000.toInt() or (c shl 16) or (c shl 8) or c
    }

    private fun flipRows(pixels: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(pixels.size)
        for (y in 0 until h) {
            System.arraycopy(pixels, y * w, out, (h - 1 - y) * w, w)
        }
        return out
    }
}

internal fun checkSize(width: Int, height: Int, limits: DecodeLimits) {
    val maxDim = maxOf(width, height)
    if (maxDim > limits.maxDimension && !limits.allowOversizedDownscale) {
        throw ConversionException(
            ErrorCode.IMAGE_TOO_LARGE,
            "The image is ${width}x$height which exceeds the ${limits.maxDimension}px limit.",
            "Enable the oversized-image option to decode with a controlled downscale, or upgrade for larger limits."
        )
    }
}
