package com.arnit.imagex.decoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.ImageFormatId

/**
 * Photoshop (PSD) decoder for the merged (flattened) composite image only.
 * Supported: version 1, 8-bit depth, RGB and grayscale color modes,
 * raw / RLE / Zip compression of the image data section.
 * Layers are not rasterized; the composite is what every viewer shows by default.
 */
class PsdDecoder : ImageReader {
    override val format = ImageFormatId.PSD

    override fun canHandle(data: ByteArray, fileName: String?): Boolean =
        data.size >= 4 && data[0].toInt() == 0x38 && data[1].toInt() == 0x42 &&
            data[2].toInt() == 0x50 && data[3].toInt() == 0x53

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage =
        try {
            parse(data, limits)
        } catch (e: ConversionException) {
            throw e
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The PSD file could not be decoded.", e.message, e)
        }

    private fun parse(data: ByteArray, limits: DecodeLimits): RasterImage {
        if (data.size < 26) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD file is too short.")
        val version = ByteKit.be16(data, 4)
        if (version != 1) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "PSB/PSD v2 large-document files are not supported.")
        }
        val channels = ByteKit.be16(data, 12)
        val height = ByteKit.be32(data, 14)
        val width = ByteKit.be32(data, 18)
        val depth = ByteKit.be16(data, 22)
        val colorMode = ByteKit.be16(data, 24)

        if (width <= 0 || height <= 0) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD has invalid dimensions.")
        }
        checkSize(width, height, limits)
        if (depth != 8) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Only 8-bit PSD composites are supported (found $depth-bit).")
        }
        if (colorMode != 3 && colorMode != 1) {
            throw ConversionException(
                ErrorCode.DECODER_FAILED,
                "Unsupported PSD color mode $colorMode. Supported: RGB and grayscale."
            )
        }

        var p = 26
        p = skipSection4(data, p)   // color mode data
        p = skipSection4(data, p)   // image resources
        p = skipSection4(data, p)   // layer and mask information
        if (p + 2 > data.size) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD image data section missing.")
        }

        val compression = ByteKit.be16(data, p)
        p += 2

        val bytesPerRow = width
        val total = channels * height * bytesPerRow

        var channelData: Array<ByteArray>
        when (compression) {
            0 -> {
                if (p + total > data.size) {
                    throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD raw image data is truncated.")
                }
                channelData = splitChannelMajor(data, p, channels, height, bytesPerRow)
            }
            1 -> {
                val rowCounts = ShortArray(channels * height)
                if (p + rowCounts.size * 2 > data.size) {
                    throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD RLE table is truncated.")
                }
                for (i in rowCounts.indices) rowCounts[i] = ByteKit.be16(data, p + i * 2).toShort()
                var q = p + rowCounts.size * 2
                channelData = Array(channels) { ByteArray(height * bytesPerRow) }
                for (c in 0 until channels) {
                    for (y in 0 until height) {
                        val count = rowCounts[c * height + y].toInt() and 0xFFFF
                        if (q + count > data.size) {
                            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD RLE data is truncated.")
                        }
                        val row = ByteKit.unpackBits(data, q, q + count, bytesPerRow)
                        if (row.size < bytesPerRow) {
                            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD RLE row is incomplete.")
                        }
                        System.arraycopy(row, 0, channelData[c], y * bytesPerRow, bytesPerRow)
                        q += count
                    }
                }
            }
            2, 3 -> {
                // Zip compression: a single zlib stream, channel-major, no row counts.
                // Mode 3 stores deltas that must be un-predicted per row.
                if (p >= data.size) {
                    throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD zip data is truncated.")
                }
                val inflated = ByteKit.inflate(data, p, data.size - p, total)
                if (inflated.size < total) {
                    throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD zip stream is incomplete.")
                }
                channelData = splitChannelMajor(inflated, 0, channels, height, bytesPerRow)
                if (compression == 3) {
                    for (c in 0 until channels) {
                        val buf = channelData[c]
                        for (y in 0 until height) {
                            val base = y * bytesPerRow
                            for (x in 1 until bytesPerRow) {
                                buf[base + x] = ((buf[base + x].toInt() and 0xFF) +
                                    (buf[base + x - 1].toInt() and 0xFF) and 0xFF).toByte()
                            }
                        }
                    }
                }
            }
            else -> throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported PSD compression $compression.")
        }

        // correct channel plan: PSD stores channel-major (channel 0 = first component)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * bytesPerRow + x
                val pixel = when {
                    colorMode == 3 -> {
                        val r = channelData[0][i].toInt() and 0xFF
                        val g = channelData[1][i].toInt() and 0xFF
                        val b = channelData[2][i].toInt() and 0xFF
                        val a = if (channels >= 4) channelData[3][i].toInt() and 0xFF else 255
                        (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    else -> { // grayscale
                        val g = channelData[0][i].toInt() and 0xFF
                        val a = if (channels >= 2) channelData[1][i].toInt() and 0xFF else 255
                        (a shl 24) or (g shl 16) or (g shl 8) or g
                    }
                }
                pixels[y * width + x] = pixel
            }
        }
        return RasterImage(width, height, pixels)
    }

    private fun splitChannelMajor(
        data: ByteArray, start: Int, channels: Int, height: Int, bytesPerRow: Int
    ): Array<ByteArray> {
        val perChannel = height * bytesPerRow
        val out = Array(channels) { c ->
            data.copyOfRange(start + c * perChannel, start + c * perChannel + perChannel)
        }
        return out
    }

    /** Skips a length-prefixed PSD section (4-byte big-endian length). */
    private fun skipSection4(data: ByteArray, from: Int): Int {
        if (from + 4 > data.size) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD section header is truncated.")
        }
        val len = ByteKit.be32(data, from)
        val end = from + 4 + len
        if (end > data.size) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PSD section is truncated.")
        }
        return end
    }
}
