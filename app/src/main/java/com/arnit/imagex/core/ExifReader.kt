package com.arnit.imagex.core

import com.arnit.imagex.core.ByteKit

/**
 * Minimal pure EXIF orientation reader for JPEG (APP1 Exif) and classic TIFF
 * containers. HEIC/AVIF device decoders apply orientation themselves; those
 * inputs do not need this.
 */
object ExifReader {

    const val MAX_ORIENTATION = 8

    fun orientationOf(data: ByteArray): Int {
        if (data.size < 12) return 1
        if (data[0].toInt() and 0xFF == 0xFF && data[1].toInt() and 0xFF == 0xD8) {
            return jpegOrientation(data)
        }
        if (data[0] == 'I'.code.toByte() && data[1] == 'I'.code.toByte() ||
            data[0] == 'M'.code.toByte() && data[1] == 'M'.code.toByte()
        ) {
            return tiffOrientation(data, 0)
        }
        return 1
    }

    private fun jpegOrientation(data: ByteArray): Int {
        var p = 2
        while (p + 4 <= data.size) {
            if (data[p].toInt() and 0xFF != 0xFF) return 1
            val marker = data[p + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker in 0xD0..0xD9 || marker == 0x01) {
                p += 2
                continue
            }
            if (marker == 0xDA) return 1 // start of scan, no more EXIF
            val length = ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
            if (length < 2) return 1
            if (marker == 0xE1) {
                val segStart = p + 4
                if (segStart + 10 <= data.size &&
                    data[segStart] == 'E'.code.toByte() && data[segStart + 1] == 'x'.code.toByte() &&
                    data[segStart + 2] == 'i'.code.toByte() && data[segStart + 3] == 'f'.code.toByte() &&
                    data[segStart + 4].toInt() == 0
                ) {
                    return tiffOrientation(data, segStart + 6)
                }
            }
            p += 2 + length
        }
        return 1
    }

    private fun tiffOrientation(data: ByteArray, tiffStart: Int): Int {
        if (tiffStart + 8 > data.size) return 1
        val le = data[tiffStart].toInt() == 0x49 && data[tiffStart + 1].toInt() == 0x49
        if ((if (le) ByteKit.le16(data, tiffStart + 2) else ByteKit.be16(data, tiffStart + 2)) != 42) return 1
        val ifdOffset = if (le) ByteKit.le32(data, tiffStart + 4) else ByteKit.be32(data, tiffStart + 4)
        val ifd = tiffStart + ifdOffset
        if (ifd + 2 > data.size) return 1
        val count = if (le) ByteKit.le16(data, ifd) else ByteKit.be16(data, ifd)
        var o = ifd + 2
        repeat(count) {
            if (o + 12 > data.size) return 1
            val tag = if (le) ByteKit.le16(data, o) else ByteKit.be16(data, o)
            if (tag == 274) {
                val type = if (le) ByteKit.le16(data, o + 2) else ByteKit.be16(data, o + 2)
                return when (type) {
                    3 -> if (le) ByteKit.le16(data, o + 8) else ByteKit.be16(data, o + 8)
                    4 -> if (le) ByteKit.le32(data, o + 8) else ByteKit.be32(data, o + 8)
                    else -> 1
                }.coerceIn(1, MAX_ORIENTATION)
            }
            o += 12
        }
        return 1
    }
}
