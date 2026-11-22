package com.arnit.imagex.encoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.PlatformRaster
import com.arnit.imagex.formats.ImageFormatId
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Baseline TIFF writer: single strip, uncompressed,
 * 8-bit RGB (or RGBA with an unassociated extra sample).
 */
class TiffEncoder : ImageWriter {
    override val format = ImageFormatId.TIFF
    override val supportsAlpha = true
    override val losslessCapable = true

    override fun encode(image: RasterImage, params: EncodeParams, out: OutputStream, platform: PlatformRaster) {
        val alpha = image.hasAlpha
        val channels = if (alpha) 4 else 3
        val width = image.width
        val height = image.height
        val imageDataSize = width * height * channels

        val ifdEntries = if (alpha) 12 else 11
        val ifdOffset = 8
        val ifdSize = 2 + ifdEntries * 12 + 4
        val valuesOffset = ifdOffset + ifdSize
        val dataStart = valuesOffset + channels * 2

        val header = ByteArrayOutputStream(128)
        header.write('I'.code); header.write('I'.code)
        ByteKit.putLe16(header, 42)
        ByteKit.putLe32(header, ifdOffset)
        ByteKit.putLe16(header, ifdEntries)

        fun entry(tag: Int, type: Int, count: Int, value: Int) {
            ByteKit.putLe16(header, tag)
            ByteKit.putLe16(header, type)
            ByteKit.putLe32(header, count)
            ByteKit.putLe32(header, value)
        }

        entry(256, 4, 1, width)
        entry(257, 4, 1, height)
        entry(258, 3, channels, valuesOffset)
        entry(259, 3, 1, 1)
        entry(262, 3, 1, 2)
        entry(273, 4, 1, dataStart)
        entry(277, 3, 1, channels)
        entry(278, 4, 1, height)
        entry(279, 4, 1, imageDataSize)
        entry(284, 3, 1, 1)
        entry(317, 3, 1, 1)
        if (alpha) entry(338, 3, 1, 2)
        ByteKit.putLe32(header, 0)

        for (i in 0 until channels) ByteKit.putLe16(header, 8)
        while (header.size() < dataStart) header.write(0)

        header.writeTo(out)

        val row = ByteArray(width * channels)
        for (y in 0 until height) {
            java.util.Arrays.fill(row, 0)
            for (x in 0 until width) {
                val p = image.pixel(x, y)
                val o = x * channels
                row[o] = ((p shr 16) and 0xFF).toByte()
                row[o + 1] = ((p shr 8) and 0xFF).toByte()
                row[o + 2] = (p and 0xFF).toByte()
                if (alpha) row[o + 3] = ((p ushr 24) and 0xFF).toByte()
            }
            out.write(row)
        }
        out.flush()
    }
}
