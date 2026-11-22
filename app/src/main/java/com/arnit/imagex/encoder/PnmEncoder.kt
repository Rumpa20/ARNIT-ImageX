package com.arnit.imagex.encoder

import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.PlatformRaster
import com.arnit.imagex.formats.ImageFormatId
import java.io.OutputStream

/**
 * Netpbm writer emitting binary PPM (P6) for color images and
 * binary PGM (P5) when the image is entirely gray. Alpha is composited
 * onto white because PNM has no alpha channel.
 */
class PnmEncoder : ImageWriter {
    override val format = ImageFormatId.PNM
    override val supportsAlpha = false
    override val losslessCapable = true

    override fun encode(image: RasterImage, params: EncodeParams, out: OutputStream, platform: PlatformRaster) {
        val source = if (image.hasAlpha) image.flattenedOn() else image

        var allGray = true
        for (p in source.pixels) {
            val r = p shr 16 and 0xFF
            val g = p shr 8 and 0xFF
            val b = p and 0xFF
            if (r != g || g != b) { allGray = false; break }
        }

        val header = if (allGray) "P5\n" else "P6\n"
        val pixelCount = source.width * source.height
        val bytesPerSample = if (allGray) pixelCount else pixelCount * 3

        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write("${source.width} ${source.height}\n255\n".toByteArray(Charsets.US_ASCII))

        val data = ByteArray(bytesPerSample)
        for (i in 0 until pixelCount) {
            val p = source.pixels[i]
            if (allGray) {
                val g = (p shr 8 and 0xFF).toByte()
                data[i] = g
            } else {
                data[i * 3] = ((p shr 16) and 0xFF).toByte()
                data[i * 3 + 1] = ((p shr 8) and 0xFF).toByte()
                data[i * 3 + 2] = (p and 0xFF).toByte()
            }
        }
        out.write(data)
        out.flush()
    }
}
