package com.arnit.imagex.decoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.ImageFormatId

/**
 * DirectDraw Surface (DDS) decoder for mip level 0 of:
 *  - Uncompressed 32/24 bpp RGB/RGBA
 *  - Block compressed DXT1 (BC1), DXT2/3 (BC2), DXT4/5 (BC3)
 * Other fourccs and the DX10 extended header are rejected honestly.
 */
class DdsDecoder : ImageReader {
    override val format = ImageFormatId.DDS

    override fun canHandle(data: ByteArray, fileName: String?): Boolean =
        data.size >= 4 &&
            (data[0].toInt() and 0xFF) == 0x44 && (data[1].toInt() and 0xFF) == 0x44 &&
            (data[2].toInt() and 0xFF) == 0x53 && (data[3].toInt() and 0xFF) == 0x20

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage =
        try {
            parse(data, limits)
        } catch (e: ConversionException) {
            throw e
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The DDS file could not be decoded.", e.message, e)
        }

    private fun parse(data: ByteArray, limits: DecodeLimits): RasterImage {
        if (data.size < 128) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "DDS file is too short.")
        val height = ByteKit.le32(data, 12)
        val width = ByteKit.le32(data, 16)
        if (width <= 0 || height <= 0) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "DDS has invalid dimensions.")
        }
        checkSize(width, height, limits)

        val fourcc = String(data, 84, 4, Charsets.US_ASCII).trim('\u0000')
        val rgbBitCount = ByteKit.le32(data, 88)

        val pixels = when (fourcc) {
            "" -> decodeUncompressed(data, width, height, rgbBitCount)
            "DXT1" -> decodeBlockCompressed(data, width, height, bc1 = true, bc3 = false)
            "DXT2", "DXT3" -> decodeBlockCompressed(data, width, height, bc1 = false, bc3 = false)
            "DXT4", "DXT5" -> decodeBlockCompressed(data, width, height, bc1 = false, bc3 = true)
            "DX10" -> throw ConversionException(
                ErrorCode.DECODER_FAILED, "DX10 (extended header) DDS files are not supported by this build."
            )
            else -> throw ConversionException(
                ErrorCode.DECODER_FAILED, "Unsupported DDS compression '$fourcc'. Supported: DXT1, DXT3, DXT5, raw RGBA."
            )
        }
        return RasterImage(width, height, pixels)
    }

    private fun decodeUncompressed(data: ByteArray, w: Int, h: Int, bitCount: Int): IntArray {
        if (bitCount != 24 && bitCount != 32) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported uncompressed DDS bit depth $bitCount.")
        }
        val bytesPerPixel = bitCount / 8
        val rowBytes = ((w * bitCount + 31) / 32) * 4
        val start = 128
        if (start + rowBytes * h > data.size) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "DDS pixel data is truncated.")
        }
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val o = start + y * rowBytes + x * bytesPerPixel
                val b = data[o].toInt() and 0xFF
                val g = data[o + 1].toInt() and 0xFF
                val r = data[o + 2].toInt() and 0xFF
                val a = if (bitCount == 32) data[o + 3].toInt() and 0xFF else 255
                pixels[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }

    private fun decodeBlockCompressed(data: ByteArray, w: Int, h: Int, bc1: Boolean, bc3: Boolean): IntArray {
        val blockSize = if (bc1) 8 else 16
        val blocksX = (w + 3) / 4
        val blocksY = (h + 3) / 4
        val bytesPerRow = blocksX * blockSize
        val start = 128
        if (start + bytesPerRow * blocksY > data.size) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "DDS block data is truncated.")
        }
        val pixels = IntArray(w * h)
        val block = IntArray(16)

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                var o = start + by * bytesPerRow + bx * blockSize
                decodeBc1Color(data, o, block, allowThreeColor = bc1)
                if (!bc1) {
                    o += 8 // color half consumed; alpha half follows
                    if (bc3) {
                        decodeBc3Alpha(data, o, block)
                    } else {
                        decodeBc2Alpha(data, o, block)
                    }
                }
                for (py in 0 until 4) {
                    for (px in 0 until 4) {
                        val x = bx * 4 + px
                        val y = by * 4 + py
                        if (x < w && y < h) pixels[y * w + x] = block[py * 4 + px]
                    }
                }
            }
        }
        return pixels
    }

    /** BC1 color half; with allowThreeColor=false the 2-bit index always picks an opaque color. */
    private fun decodeBc1Color(data: ByteArray, o: Int, block: IntArray, allowThreeColor: Boolean) {
        val c0 = ByteKit.le16(data, o)
        val c1 = ByteKit.le16(data, o + 2)
        val color0 = ByteKit.rgb565(c0) or 0xFF000000.toInt()
        val color1 = ByteKit.rgb565(c1) or 0xFF000000.toInt()
        val threeColor = allowThreeColor && c0 <= c1

        val c2 = mixRgb(color0, color1, 2, 1)
        val c3 = mixRgb(color0, color1, 1, 2)
        val color2 = c2
        val color3 = if (threeColor) 0 else c3

        for (row in 0 until 4) {
            val indices = ByteKit.le32(data, o + 4 + row * 4)
            for (col in 0 until 4) {
                val idx = (indices shr (col * 2)) and 0x3
                block[row * 4 + col] = when {
                    idx == 0 -> color0
                    idx == 1 -> color1
                    idx == 2 -> color2
                    else -> color3
                }
            }
        }
    }

    private fun decodeBc2Alpha(data: ByteArray, o: Int, block: IntArray) {
        for (i in 0 until 16) {
            val byte = data[o + i / 2].toInt() and 0xFF
            val a4 = if (i % 2 == 0) byte ushr 4 else byte and 0x0F
            val a8 = a4 shl 4 or a4
            block[i] = (a8 shl 24) or (block[i] and 0x00FFFFFF)
        }
    }

    private fun decodeBc3Alpha(data: ByteArray, o: Int, block: IntArray) {
        val a0 = data[o].toInt() and 0xFF
        val a1 = data[o + 1].toInt() and 0xFF
        val palette = IntArray(8)
        palette[0] = a0
        palette[1] = a1
        if (a0 > a1) {
            palette[2] = (6 * a0 + a1) / 7
            palette[3] = (5 * a0 + 2 * a1) / 7
            palette[4] = (4 * a0 + 3 * a1) / 7
            palette[5] = (3 * a0 + 4 * a1) / 7
            palette[6] = (2 * a0 + 5 * a1) / 7
            palette[7] = (a0 + 6 * a1) / 7
        } else {
            palette[2] = (4 * a0 + a1) / 5
            palette[3] = (3 * a0 + 2 * a1) / 5
            palette[4] = (2 * a0 + 3 * a1) / 5
            palette[5] = (a0 + 4 * a1) / 5
            palette[6] = 0
            palette[7] = 255
        }
        var bitPos = 16
        for (i in 0 until 16) {
            var v = 0
            for (k in 0 until 3) {
                val byte = data[o + (bitPos + k) / 8].toInt() and 0xFF
                val bit = (byte shr ((bitPos + k) % 8)) and 0x01
                v = v or (bit shl k)
            }
            bitPos += 3
            block[i] = (palette[v.coerceIn(0, 7)] shl 24) or (block[i] and 0x00FFFFFF)
        }
    }

    private fun mixRgb(c0: Int, c1: Int, w0: Int, w1: Int): Int {
        val r = ((c0 shr 16 and 0xFF) * w0 + (c1 shr 16 and 0xFF) * w1) / (w0 + w1)
        val g = ((c0 shr 8 and 0xFF) * w0 + (c1 shr 8 and 0xFF) * w1) / (w0 + w1)
        val b = ((c0 and 0xFF) * w0 + (c1 and 0xFF) * w1) / (w0 + w1)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }
}
