package com.arnit.imagex.encoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.PlatformRaster
import com.arnit.imagex.formats.ImageFormatId
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** TGA-2 writer: 32bpp, top-left origin, RLE compressed, lossless. */
class TgaEncoder : ImageWriter {
    override val format = ImageFormatId.TGA
    override val supportsAlpha = true
    override val losslessCapable = true

    override fun encode(image: RasterImage, params: EncodeParams, out: OutputStream, platform: PlatformRaster) {
        val header = ByteArrayOutputStream(18)
        header.write(0)          // no image id
        header.write(0)          // no colormap
        header.write(10)         // RLE truecolor
        ByteKit.putLe16(header, 0) // colormap first entry index
        ByteKit.putLe16(header, 0) // colormap length
        header.write(0)          // colormap entry size (no colormap)
        ByteKit.putLe16(header, 0) // x origin
        ByteKit.putLe16(header, 0) // y origin
        ByteKit.putLe16(header, image.width)
        ByteKit.putLe16(header, image.height)
        header.write(32)         // bpp
        header.write(0x20 or 0x08) // top-left origin, 8 alpha bits
        header.writeTo(out)

        val row = ByteArray(image.width * 4)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val p = image.pixel(x, y)
                val o = x * 4
                row[o] = (p and 0xFF).toByte()
                row[o + 1] = ((p shr 8) and 0xFF).toByte()
                row[o + 2] = ((p shr 16) and 0xFF).toByte()
                row[o + 3] = ((p ushr 24) and 0xFF).toByte()
            }
            writeRleRow(out, row)
        }

        val footer = "TRUEVISION-XFILE.\u0000"
        out.write(footer.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun writeRleRow(out: OutputStream, row: ByteArray) {
        val w = row.size / 4
        var x = 0
        while (x < w) {
            val start = x * 4
            var run = 1
            while (x + run < w && run < 128 &&
                row[(x + run) * 4] == row[start] &&
                row[(x + run) * 4 + 1] == row[start + 1] &&
                row[(x + run) * 4 + 2] == row[start + 2] &&
                row[(x + run) * 4 + 3] == row[start + 3]
            ) run++
            if (run >= 2) {
                out.write(0x80 or (run - 1))
                out.write(row, start, 4)
                x += run
            } else {
                // literal run until a repeat begins
                var lit = 1
                while (x + lit < w && lit < 128 && !rowEquals(row, x + lit, x + lit - 1)) lit++
                out.write(lit - 1)
                out.write(row, start, lit * 4)
                x += lit
            }
        }
    }

    private fun rowEquals(row: ByteArray, i: Int, j: Int): Boolean {
        val a = i * 4
        val b = j * 4
        return row[a] == row[b] && row[a + 1] == row[b + 1] && row[a + 2] == row[b + 2] && row[a + 3] == row[b + 3]
    }
}
