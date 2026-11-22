package com.arnit.imagex

import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.PlatformRaster
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * JVM-only [PlatformRaster] used to unit test the pure codecs without an
 * Android device. Mirrors the contract of AndroidPlatformRaster.
 */
class ImageIoPlatformRaster : PlatformRaster {

    override fun decode(data: ByteArray, maxDimension: Int, enforceLimit: Boolean): RasterImage {
        val image = ImageIO.read(ByteArrayInputStream(data))
            ?: throw IllegalArgumentException("The data is not readable by ImageIO")
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getRGB(0, 0, w, h, pixels, 0, w)
        return RasterImage(w, h, pixels)
    }

    override fun encodePng(image: RasterImage, out: OutputStream) {
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        buffered.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        ImageIO.write(buffered, "png", out)
    }
}
