package com.arnit.imagex.decoder

import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.ImageFormatId
import java.util.Locale

/** WBMP type 0 (WAP bitmap) decoder: monochrome 1-bit images. */
class WbmpDecoder : ImageReader {
    override val format = ImageFormatId.WBMP

    override fun canHandle(data: ByteArray, fileName: String?): Boolean {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.US)
        if (ext == "wbmp") {
            return data.size >= 2 && (data[0].toInt() and 0xFF) == 0
        }
        return false
    }

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage =
        try {
            parse(data, limits)
        } catch (e: ConversionException) {
            throw e
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The WBMP image could not be decoded.", e.message, e)
        }

    private fun parse(data: ByteArray, limits: DecodeLimits): RasterImage {
        var p = 0
        val type = readVarint(data, p)?.also { p = it.second }?.first ?: run {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "WBMP header is truncated.")
        }
        if (type != 0) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported WBMP type $type (only type 0 is supported).")
        }
        val extOctets = readVarint(data, p)?.also { p = it.second }?.first
            ?: throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "WBMP header is truncated.")
        if (extOctets != 0) {
            val expectedEnd = p + extOctets
            if (expectedEnd >= data.size) {
                throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "WBMP extension header is truncated.")
            }
            p = expectedEnd
        }
        val width = readVarint(data, p)?.also { p = it.second }?.first
            ?: throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "WBMP width is truncated.")
        val height = readVarint(data, p)?.also { p = it.second }?.first
            ?: throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "WBMP height is truncated.")
        if (width <= 0 || height <= 0) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "WBMP has invalid dimensions.")
        }
        checkSize(width, height, limits)

        val rowBytes = (width + 7) / 8
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val o = p + y * rowBytes + x / 8
                if (o >= data.size) {
                    throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "WBMP pixel data is truncated.")
                }
                val bit = (data[o].toInt() shr (7 - (x % 8))) and 0x01
                val gray = if (bit == 1) 0 else 255
                pixels[y * width + x] = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
            }
        }
        return RasterImage(width, height, pixels)
    }

    /** Multi-byte 7-bit varint; returns value + next position. */
    private fun readVarint(data: ByteArray, start: Int): Pair<Int, Int>? {
        var v = 0
        var i = start
        while (i < data.size && i - start < 5) {
            val b = data[i].toInt() and 0xFF
            v = (v shl 7) or (b and 0x7F)
            i++
            if (b and 0x80 == 0) return v to i
        }
        return null
    }
}
