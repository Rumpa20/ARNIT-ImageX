package com.arnit.imagex.decoder

import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.ImageFormatId
import java.util.Locale

/**
 * Netpbm decoder for the portable anymap family:
 * P1/P4 (PBM bitmap), P2/P5 (PGM gray), P3/P6 (PPM color),
 * ASCII and binary variants, 8 and 16-bit maxval.
 */
class PnmDecoder : ImageReader {
    override val format = ImageFormatId.PNM

    override fun canHandle(data: ByteArray, fileName: String?): Boolean {
        if (data.size >= 2 && data[0] == 'P'.code.toByte() &&
            data[1].toInt() in '1'.code..'6'.code
        ) return true
        val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.US)
        return ext in setOf("ppm", "pgm", "pbm", "pnm")
    }

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage =
        try {
            parse(data, limits)
        } catch (e: ConversionException) {
            throw e
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The PNM image could not be decoded.", e.message, e)
        }

    private class HeaderCursor(val data: ByteArray) {
        var pos = 0

        fun skipWhitespaceAndComments() {
            while (pos < data.size) {
                val c = data[pos]
                when {
                    c == '#'.code.toByte() -> {
                        while (pos < data.size && data[pos] != '\n'.code.toByte()) pos++
                    }
                    c == ' '.code.toByte() || c == '\t'.code.toByte() ||
                        c == '\n'.code.toByte() || c == '\r'.code.toByte() -> pos++
                    else -> return
                }
            }
        }

        fun nextValue(): Int {
            skipWhitespaceAndComments()
            if (pos >= data.size) return -1
            var v = 0
            var digits = 0
            while (pos < data.size) {
                val c = data[pos]
                if (c in '0'.code.toByte()..'9'.code.toByte()) {
                    v = v * 10 + (c - '0'.code.toByte())
                    digits++
                    pos++
                } else break
            }
            return if (digits == 0) -1 else v
        }
    }

    private fun parse(data: ByteArray, limits: DecodeLimits): RasterImage {
        if (data.size < 2) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PNM file is too short.")
        val type = data[1].toInt().toChar()
        if (type !in '1'..'6') {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported PNM subtype P$type.")
        }
        val ascii = type <= '3'
        val imgType = when {
            type == '1' || type == '4' -> TYPE_BW
            type == '2' || type == '5' -> TYPE_GRAY
            else -> TYPE_RGB
        }
        val cursor = HeaderCursor(data)
        cursor.pos = 2
        val width = cursor.nextValue()
        val height = cursor.nextValue()
        if (width <= 0 || height <= 0) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PNM has invalid dimensions.")
        }
        checkSize(width, height, limits)

        var maxval = 65535
        if (imgType != TYPE_BW) {
            maxval = cursor.nextValue().also {
                if (it < 1 || it > 65535) {
                    throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PNM maxval is invalid.")
                }
            }
        }

        // After the header values a single whitespace separates binary payload
        if (!ascii) cursor.pos++

        val channels = if (imgType == TYPE_RGB) 3 else 1
        val samples = IntArray(width * height * channels)
        val count = samples.size

        if (ascii) {
            for (i in 0 until count) {
                val v = cursor.nextValue()
                if (v < 0) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PNM data is truncated.")
                samples[i] = v
            }
        } else {
            val start = cursor.pos
            if (imgType == TYPE_BW) {
                val rowBytes = (width + 7) / 8
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val o = start + y * rowBytes + x / 8
                        if (o >= data.size) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PNM data is truncated.")
                        val bit = (data[o].toInt() shr (7 - (x % 8))) and 0x01
                        samples[y * width + x] = bit
                    }
                }
            } else {
                val bytesPerSample = if (maxval > 255) 2 else 1
                val needed = count * bytesPerSample
                if (start + needed > data.size) {
                    throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "PNM data is truncated.")
                }
                for (i in 0 until count) {
                    samples[i] = if (bytesPerSample == 1) {
                        data[start + i].toInt() and 0xFF
                    } else {
                        ((data[start + i * 2].toInt() and 0xFF) shl 8) or (data[start + i * 2 + 1].toInt() and 0xFF)
                    }
                }
            }
        }

        if (imgType == TYPE_BW) {
            // Both P1 and P4 use 1 = black
            for (j in samples.indices) samples[j] = if (samples[j] == 1) 0 else 255
        } else {
            val scale = 255.0 / maxval
            for (j in samples.indices) samples[j] = (samples[j] * scale).toInt().coerceIn(0, 255)
        }

        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val c = channels * i
                pixels[i] = if (channels == 1) {
                    0xFF000000.toInt() or (samples[c] shl 16) or (samples[c] shl 8) or samples[c]
                } else {
                    0xFF000000.toInt() or (samples[c] shl 16) or (samples[c + 1] shl 8) or samples[c + 2]
                }
            }
        }
        return RasterImage(width, height, pixels)
    }

    private companion object {
        const val TYPE_BW = 0
        const val TYPE_GRAY = 1
        const val TYPE_RGB = 2
    }
}
