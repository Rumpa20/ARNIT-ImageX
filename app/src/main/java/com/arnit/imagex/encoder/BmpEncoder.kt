package com.arnit.imagex.encoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.PlatformRaster
import com.arnit.imagex.formats.ImageFormatId
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Lossless BMP writer: 32bpp BI_RGB with real alpha preserved in the file. */
class BmpEncoder : ImageWriter {
    override val format = ImageFormatId.BMP
    override val supportsAlpha = true
    override val losslessCapable = true

    override fun encode(image: RasterImage, params: EncodeParams, out: OutputStream, platform: PlatformRaster) {
        val header = ByteArrayOutputStream(54)
        val data = ByteArrayOutputStream(image.width * image.height * 4)

        val rowBytes = image.width * 4
        val paddedRow = (rowBytes + 3) / 4 * 4
        val pixelDataSize = paddedRow * image.height
        val fileSize = 14 + 40 + pixelDataSize

        // BITMAPFILEHEADER
        header.write('B'.code); header.write('M'.code)
        ByteKit.putLe32(header, fileSize)
        ByteKit.putLe32(header, 0)
        ByteKit.putLe32(header, 54)

        // BITMAPINFOHEADER
        ByteKit.putLe32(header, 40)
        ByteKit.putLe32(header, image.width)
        ByteKit.putLe32(header, image.height)
        ByteKit.putLe16(header, 1)
        ByteKit.putLe16(header, 32)
        ByteKit.putLe32(header, 0)          // BI_RGB
        ByteKit.putLe32(header, pixelDataSize)
        ByteKit.putLe32(header, 0)
        ByteKit.putLe32(header, 0)
        ByteKit.putLe32(header, 0)
        ByteKit.putLe32(header, 0)

        // Rows are stored bottom-up
        val row = ByteArray(paddedRow)
        for (y in image.height - 1 downTo 0) {
            java.util.Arrays.fill(row, 0)
            for (x in 0 until image.width) {
                val p = image.pixel(x, y)
                val o = x * 4
                row[o] = (p and 0xFF).toByte()
                row[o + 1] = ((p shr 8) and 0xFF).toByte()
                row[o + 2] = ((p shr 16) and 0xFF).toByte()
                row[o + 3] = ((p ushr 24) and 0xFF).toByte()
            }
            data.write(row, 0, paddedRow)
        }

        header.writeTo(out)
        data.writeTo(out)
        out.flush()
    }
}
