package com.arnit.imagex.encoder

import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.PlatformRaster
import com.arnit.imagex.formats.ImageFormatId
import java.io.OutputStream

/**
 * WBMP type 0 writer. Converting a color image to WBMP necessarily discards
 * color information (monochrome 1-bit); luma threshold 50% is used.
 */
class WbmpEncoder : ImageWriter {
    override val format = ImageFormatId.WBMP
    override val supportsAlpha = false
    override val losslessCapable = false

    override fun encode(image: RasterImage, params: EncodeParams, out: OutputStream, platform: PlatformRaster) {
        out.write(0)
        out.write(0)
        writeVarint(out, image.width)
        writeVarint(out, image.height)

        val rowBytes = (image.width + 7) / 8
        val row = ByteArray(rowBytes)
        for (y in 0 until image.height) {
            java.util.Arrays.fill(row, 0)
            for (x in 0 until image.width) {
                val p = image.pixel(x, y)
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                val luma = (r * 299 + g * 587 + b * 114) / 1000
                if (luma < 128) row[x / 8] = (row[x / 8].toInt() or (1 shl (7 - x % 8))).toByte()
            }
            out.write(row)
        }
        out.flush()
    }

    private fun writeVarint(out: OutputStream, value: Int) {
        if (value <= 0) {
            out.write(0)
            return
        }
        val groups = ArrayList<Int>(4)
        var v = value
        while (v > 0) {
            groups.add(v and 0x7F)
            v = v shr 7
        }
        for (i in groups.size - 1 downTo 1) {
            out.write(groups[i] or 0x80)
        }
        out.write(groups[0])
    }
}
