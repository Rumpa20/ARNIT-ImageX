package com.arnit.imagex.decoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.ImageFormatId

/**
 * ICO/CUR decoder. Picks the largest embedded entry and decodes it as
 * PNG (delegating to the platform raster decoder) or as an embedded
 * Windows BMP (1/4/8/24/32bpp with AND mask transparency).
 */
class IcoDecoder : ImageReader {
    override val format = ImageFormatId.ICO

    override fun canHandle(data: ByteArray, fileName: String?): Boolean =
        data.size >= 4 && (data[0].toInt() and 0xFF) == 0 && (data[1].toInt() and 0xFF) == 0 &&
            (data[2].toInt() and 0xFF) == 1

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage =
        try {
            parse(data, limits, platform)
        } catch (e: ConversionException) {
            throw e
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The ICO file could not be decoded.", e.message, e)
        }

    private fun parse(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage {
        if (data.size < 6) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "ICO file is too short.")
        val count = ByteKit.le16(data, 4)
        if (count <= 0 || data.size < 6 + count * 16) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "ICO directory is invalid.")
        }

        val entries = (0 until count).map { i ->
            val o = 6 + i * 16
            Entry(
                width = data[o].toInt().and(0xFF).let { if (it == 0) 256 else it },
                height = data[o + 1].toInt().and(0xFF).let { if (it == 0) 256 else it },
                size = ByteKit.le32(data, o + 8),
                offset = ByteKit.le32(data, o + 12)
            )
        }
            .filter { it.offset >= 0 && it.size > 0 && it.offset + it.size <= data.size }
            .sortedByDescending { it.width * it.height }

        if (entries.isEmpty()) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "ICO directory points nowhere.")
        }

        var lastError: ConversionException? = null
        for (entry in entries) {
            checkSize(entry.width, entry.height, limits)
            val bytes = data.copyOfRange(entry.offset, entry.offset + entry.size)
            val outcome = runCatching {
                if (ByteKit.startsWith(bytes, 0, PNG_SIGNATURE)) {
                    platform.decode(bytes, Int.MAX_VALUE, false)
                } else {
                    decodeBmpEntry(bytes, limits)
                }
            }
            val decoded = outcome.getOrNull()
            if (decoded != null) return decoded
            lastError = outcome.exceptionOrNull() as? ConversionException
                ?: lastError ?: ConversionException(ErrorCode.DECODER_FAILED, "ICO entry could not be decoded.")
        }
        throw lastError ?: ConversionException(ErrorCode.DECODER_FAILED, "No decodable entry found in the ICO file.")
    }

    private fun decodeBmpEntry(bytes: ByteArray, limits: DecodeLimits): RasterImage {
        if (bytes.size < 40) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "ICO BMP entry is too short.")
        val headerSize = ByteKit.le32(bytes, 0)
        if (headerSize < 40) throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported ICO BMP header.")
        val width = ByteKit.le32(bytes, 4)
        val height = ByteKit.le32(bytes, 8) / 2
        val bitCount = ByteKit.le16(bytes, 14)
        val compression = ByteKit.le32(bytes, 16)
        if (compression != 0 && !(compression == 3 && bitCount in intArrayOf(16, 32))) {
            throw ConversionException(
                ErrorCode.DECODER_FAILED,
                "Unsupported ICO compression $compression. PNG-based icons are represented in this build."
            )
        }
        checkSize(width, height, limits)

        val colorTableSize = when (bitCount) {
            1 -> 2; 4 -> 16; 8 -> 256; else -> 0
        }
        val paletteStart = 40
        val palette = IntArray(colorTableSize) { i ->
            val o = paletteStart + i * 4
            val b = bytes[o].toInt() and 0xFF
            val g = bytes[o + 1].toInt() and 0xFF
            val r = bytes[o + 2].toInt() and 0xFF
            0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val xorStart = paletteStart + colorTableSize * 4
        if (compression == 3) {
            val rMask = ByteKit.le32(bytes, 40 + 4 * 1)
            val gMask = ByteKit.le32(bytes, 40 + 4 * 2)
            val bMask = ByteKit.le32(bytes, 40 + 4 * 3)
            val aMask = ByteKit.le32(bytes, 40 + 4 * 4)
            return decodeBitFields(bytes, xorStart, width, height, rMask, gMask, bMask, aMask)
        }

        val pixels = IntArray(width * height)
        val rowBytes = ((width * bitCount + 31) / 32) * 4
        val andStart = xorStart + rowBytes * height
        val andRowBytes = ((width + 31) / 32) * 4
        if (andStart + andRowBytes * height > bytes.size && bitCount in intArrayOf(1, 4, 8, 24)) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "ICO AND mask is missing.")
        }

        for (y in 0 until height) {
            val srcRow = height - 1 - y
            for (x in 0 until width) {
                val pixel: Int = when (bitCount) {
                    32 -> {
                        val o = xorStart + srcRow * rowBytes + x * 4
                        val b = bytes[o].toInt() and 0xFF
                        val g = bytes[o + 1].toInt() and 0xFF
                        val r = bytes[o + 2].toInt() and 0xFF
                        val a = bytes[o + 3].toInt() and 0xFF
                        (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    24 -> {
                        val o = xorStart + srcRow * rowBytes + x * 3
                        val b = bytes[o].toInt() and 0xFF
                        val g = bytes[o + 1].toInt() and 0xFF
                        val r = bytes[o + 2].toInt() and 0xFF
                        0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                    }
                    8 -> palette[bytes[xorStart + srcRow * rowBytes + x].toInt() and 0xFF]
                    4 -> {
                        val o = xorStart + srcRow * rowBytes + x / 2
                        val v = (bytes[o].toInt() shr (if (x % 2 == 0) 4 else 0)) and 0x0F
                        palette[v]
                    }
                    1 -> {
                        val o = xorStart + srcRow * rowBytes + x / 8
                        val v = (bytes[o].toInt() shr (7 - (x % 8))) and 0x01
                        palette[v]
                    }
                    else -> throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported ICO bit depth $bitCount.")
                }
                val opaque = bitCount == 32 || run {
                    val o = andStart + srcRow * andRowBytes + x / 8
                    ((bytes[o].toInt() shr (7 - x % 8)) and 0x01) == 0
                }
                pixels[y * width + x] =
                    if (opaque) 0xFF000000.toInt() or (pixel and 0xFFFFFF)
                    else pixel and 0x00FFFFFF
            }
        }
        return RasterImage(width, height, pixels)
    }

    private fun decodeBitFields(
        bytes: ByteArray, start: Int, w: Int, h: Int,
        rMask: Int, gMask: Int, bMask: Int, aMask: Int
    ): RasterImage {
        val pixels = IntArray(w * h)
        val rowBytes = ((w * 32 + 31) / 32) * 4
        for (y in 0 until h) {
            for (x in 0 until w) {
                val o = start + y * rowBytes + x * 4
                val v = ByteKit.le32(bytes, o)
                val r = channel(v, rMask)
                val g = channel(v, gMask)
                val b = channel(v, bMask)
                val a = channel(v, aMask)
                pixels[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return RasterImage(w, h, pixels)
    }

    private fun channel(v: Int, mask: Int): Int {
        if (mask == 0) return 0xFF
        val bits = mask.countOneBits()
        if (bits == 0 || bits > 8) return 0
        val raw = (v and mask) ushr mask.countTrailingZeroBits()
        return (raw * 255) / ((1 shl bits) - 1)
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }

    private data class Entry(val width: Int, val height: Int, val size: Int, val offset: Int)
}
