package com.arnit.imagex.encoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.PlatformRaster
import com.arnit.imagex.formats.ImageFormatId
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * ICO writer. Emits the classic multi-size icon: 256, 128, 64, 48, 32 and 16px
 * entries (capped at the source size), each stored as an embedded PNG.
 */
class IcoEncoder : ImageWriter {
    override val format = ImageFormatId.ICO
    override val supportsAlpha = true
    override val losslessCapable = true

    override fun encode(image: RasterImage, params: EncodeParams, out: OutputStream, platform: PlatformRaster) {
        val sizes = ENTRY_SIZES.filter { it <= maxOf(image.width, image.height) }.ifEmpty { listOf(256) }
        val pngBlobs = sizes.map { size ->
            val scaled = if (image.width == size && image.height == size) image
            else {
                val ratio = size.toDouble() / maxOf(image.width, image.height)
                val w = (image.width * ratio).toInt().coerceAtLeast(1)
                val h = (image.height * ratio).toInt().coerceAtLeast(1)
                image.resizedTo(w, h)
            }
            val buffer = ByteArrayOutputStream()
            platform.encodePng(scaled, buffer)
            size to buffer.toByteArray()
        }

        val header = ByteArrayOutputStream(6 + pngBlobs.size * 16)
        ByteKit.putLe16(header, 0)
        ByteKit.putLe16(header, 1)
        ByteKit.putLe16(header, pngBlobs.size)

        val directorySize = 6 + pngBlobs.size * 16
        var offset = directorySize
        for ((size, blob) in pngBlobs) {
            header.write(if (size >= 256) 0 else size)
            header.write(if (size >= 256) 0 else size)
            header.write(0)
            header.write(0)
            ByteKit.putLe16(header, 1)
            ByteKit.putLe16(header, 32)
            ByteKit.putLe32(header, blob.size)
            ByteKit.putLe32(header, offset)
            offset += blob.size
        }
        header.writeTo(out)
        for ((_, blob) in pngBlobs) out.write(blob)
        out.flush()
    }

    private companion object {
        val ENTRY_SIZES = intArrayOf(256, 128, 64, 48, 32, 16)
    }
}
