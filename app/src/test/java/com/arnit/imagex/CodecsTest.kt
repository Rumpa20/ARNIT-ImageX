package com.arnit.imagex

import com.arnit.imagex.core.ExifReader
import com.arnit.imagex.core.QualityMode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.decoder.DecodeLimits
import com.arnit.imagex.decoder.IcoDecoder
import com.arnit.imagex.decoder.PnmDecoder
import com.arnit.imagex.decoder.TgaDecoder
import com.arnit.imagex.decoder.TiffDecoder
import com.arnit.imagex.decoder.WbmpDecoder
import com.arnit.imagex.encoder.BmpEncoder
import com.arnit.imagex.encoder.EncodeParams
import com.arnit.imagex.encoder.IcoEncoder
import com.arnit.imagex.encoder.PnmEncoder
import com.arnit.imagex.encoder.TgaEncoder
import com.arnit.imagex.encoder.TiffEncoder
import com.arnit.imagex.encoder.WbmpEncoder
import com.arnit.imagex.formats.FormatDetector
import com.arnit.imagex.formats.ImageFormatId
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecsTest {

    private val platform = ImageIoPlatformRaster()
    private val limits = DecodeLimits(maxDimension = 8192, applyExifOrientation = false)

    /** Opaque gradient image with deterministic pixel values. */
    private fun gradient(width: Int, height: Int): RasterImage {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255) / (width - 1).coerceAtLeast(1)
                val g = (y * 255) / (height - 1).coerceAtLeast(1)
                val b = ((x + y) * 255) / (width + height - 2).coerceAtLeast(1)
                pixels[y * width + x] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            }
        }
        return RasterImage(width, height, pixels)
    }

    private fun gradientWithAlpha(width: Int, height: Int): RasterImage {
        val image = gradient(width, height)
        for (x in 0 until width) {
            val alpha = if (x % 2 == 0) 0x40 else 0xC0
            image.setPixel(x, 0, alpha shl 24 or ((x * 7) and 0xFFFFFF))
        }
        return image
    }

    private fun encode(image: RasterImage, format: ImageFormatId): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = when (format) {
            ImageFormatId.BMP -> BmpEncoder()
            ImageFormatId.TGA -> TgaEncoder()
            ImageFormatId.TIFF -> TiffEncoder()
            ImageFormatId.ICO -> IcoEncoder()
            ImageFormatId.WBMP -> WbmpEncoder()
            ImageFormatId.PNM -> PnmEncoder()
            else -> throw IllegalArgumentException("No pure encoder in test for $format")
        }
        encoder.encode(
            image,
            EncodeParams.from(QualityMode.LOSSLESS, 100, format, encoder.losslessCapable),
            out,
            platform
        )
        return out.toByteArray()
    }

    @Test
    fun tgaRoundTripPreservesPixels() {
        val image = gradientWithAlpha(48, 32)
        val data = encode(image, ImageFormatId.TGA)
        val decoded = TgaDecoder().decode(data, limits, platform)
        assertEquals(image.width, decoded.width)
        assertEquals(image.height, decoded.height)
        assertTrue(image.pixels.contentEquals(decoded.pixels))
    }

    @Test
    fun tiffDecoderReadsJdkWrittenTiff() {
        val image = gradientWithAlpha(48, 32)
        val buffered = java.awt.image.BufferedImage(
            image.width, image.height, java.awt.image.BufferedImage.TYPE_INT_ARGB
        )
        buffered.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        val out = ByteArrayOutputStream()
        assertTrue(javax.imageio.ImageIO.write(buffered, "tiff", out))
        val decoded = TiffDecoder().decode(out.toByteArray(), limits, platform)
        assertEquals(image.width, decoded.width)
        assertEquals(image.height, decoded.height)
        assertTrue(image.pixels.contentEquals(decoded.pixels))
    }

    @Test
    fun tiffEncoderOutputIsReadByJdk() {
        val image = gradient(48, 32)
        val data = encode(image, ImageFormatId.TIFF)
        val decoded = platform.decode(data, 8192, enforceLimit = false)
        assertEquals(image.width, decoded.width)
        assertEquals(image.height, decoded.height)
        val expectedRgb = IntArray(image.pixels.size) { image.pixels[it] and 0xFFFFFF }
        val actualRgb = IntArray(decoded.pixels.size) { decoded.pixels[it] and 0xFFFFFF }
        assertTrue(expectedRgb.contentEquals(actualRgb))
    }

    @Test
    fun tiffRoundTripPreservesPixels() {
        val image = gradientWithAlpha(48, 32)
        val data = encode(image, ImageFormatId.TIFF)
        val decoded = TiffDecoder().decode(data, limits, platform)
        assertEquals(image.width, decoded.width)
        assertEquals(image.height, decoded.height)
        assertTrue(image.pixels.contentEquals(decoded.pixels))
    }

    @Test
    fun bmpRoundTripIsReadableAndLossless() {
        val image = gradientWithAlpha(48, 32)
        val data = encode(image, ImageFormatId.BMP)
        val decoded = platform.decode(data, 8192, enforceLimit = false)
        assertEquals(image.width, decoded.width)
        assertEquals(image.height, decoded.height)
        val expectedRgb = IntArray(image.pixels.size) { image.pixels[it] and 0xFFFFFF }
        val actualRgb = IntArray(decoded.pixels.size) { decoded.pixels[it] and 0xFFFFFF }
        assertTrue(expectedRgb.contentEquals(actualRgb))
    }

    @Test
    fun pnmRoundTripPreservesOpaquePixels() {
        val image = gradient(40, 24)
        val data = encode(image, ImageFormatId.PNM)
        assertEquals('P'.code.toByte(), data[0])
        assertEquals('6'.code.toByte(), data[1])
        val decoded = PnmDecoder().decode(data, limits, platform)
        assertEquals(image.width, decoded.width)
        assertEquals(image.height, decoded.height)
        assertTrue(image.pixels.contentEquals(decoded.pixels))
    }

    @Test
    fun pnmEncodesGrayAsP5() {
        val gray = IntArray(9) { 0xFF808080.toInt() }
        val data = encode(RasterImage(3, 3, gray), ImageFormatId.PNM)
        assertEquals('P'.code.toByte(), data[0])
        assertEquals('5'.code.toByte(), data[1])
    }

    @Test
    fun wbmpRoundTripThresholdsCheckerboard() {
        val width = 40
        val height = 24
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            if ((x / 4 + i / width / 4) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val image = RasterImage(width, height, pixels)
        val data = encode(image, ImageFormatId.WBMP)
        assertEquals(0, data[0].toInt() and 0xFF)
        assertEquals(0, data[1].toInt() and 0xFF)
        val decoded = WbmpDecoder().decode(data, limits, platform)
        assertEquals(width, decoded.width)
        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        for (i in pixels.indices) {
            val expectedBlack = (image.pixels[i] shr 8 and 0xFF) < 128
            val actualBlack = (decoded.pixels[i] shr 8 and 0xFF) < 128
            assertEquals("Pixel $i blackness", expectedBlack, actualBlack)
        }
    }

    @Test
    fun icoRoundTripReturnsLargestEntryUnchanged() {
        val image = gradientWithAlpha(64, 40)
        val data = encode(image, ImageFormatId.ICO)
        assertEquals(0x00, data[0].toInt() and 0xFF)
        assertEquals(0x00, data[1].toInt() and 0xFF)
        assertEquals(0x01, data[2].toInt() and 0xFF)
        val decoded = IcoDecoder().decode(data, limits, platform)
        assertEquals(64, decoded.width)
        assertEquals(40, decoded.height)
        assertTrue(image.pixels.contentEquals(decoded.pixels))
    }

    @Test
    fun formatDetectorRecognizesCommonMagics() {
        assertDetected(
            ImageFormatId.PNG,
            byteArrayOf(0x89.toByte()) + "PNG\r\n\u001A\n".toByteArray(Charsets.ISO_8859_1)
        )
        assertDetected(
            ImageFormatId.JPEG,
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        )
        assertDetected(ImageFormatId.GIF, "GIF89a".toByteArray(Charsets.US_ASCII))
        assertDetected(ImageFormatId.BMP, "BM".toByteArray(Charsets.US_ASCII) + ByteArray(12))
        assertDetected(ImageFormatId.TIFF, byteArrayOf(0x49.toByte(), 0x49.toByte(), 0x2A.toByte(), 0x00.toByte()))
        assertDetected(ImageFormatId.TIFF, byteArrayOf(0x4D.toByte(), 0x4D.toByte(), 0x00.toByte(), 0x2A.toByte()))
        assertDetected(ImageFormatId.ICNS, "icns".toByteArray(Charsets.US_ASCII) + ByteArray(4))
        assertDetected(ImageFormatId.PSD, "8BPS".toByteArray(Charsets.US_ASCII) + ByteArray(4))
        assertNull(FormatDetector.sniffAll("definitely not an image at all".toByteArray(), "junk.bin", null))
    }

    private fun assertDetected(expected: ImageFormatId, data: ByteArray) {
        assertEquals(expected, FormatDetector.sniffAll(data, null, null)?.format)
        assertEquals(
            expected,
            FormatDetector.sniffAll(data, "file.${expected.extensions.first()}", null)?.format
        )
    }

    @Test
    fun formatDetectorMatchesExtensionOnlyForAmbiguousData() {
        val sniff = FormatDetector.sniffAll(ByteArray(64) { (it % 251).toByte() }, "picture.tga", null)
        assertEquals(ImageFormatId.TGA, sniff?.format)
    }

    @Test
    fun exifReaderReadsMinimalJpegApp1Orientation() {
        assertEquals(6, ExifReader.orientationOf(minimalJpegWithOrientation(6)))
        assertEquals(
            1,
            ExifReader.orientationOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        )
    }

    @Test
    fun encodeParamsResolveQualityByMode() {
        val lossless = EncodeParams.from(QualityMode.LOSSLESS, 40, ImageFormatId.WEBP, losslessCapable = true)
        assertEquals(100, lossless.quality)
        assertTrue(lossless.forceLossless)

        val original = EncodeParams.from(QualityMode.ORIGINAL, 40, ImageFormatId.WEBP, losslessCapable = true)
        assertEquals(100, original.quality)
        assertTrue(original.forceLossless)

        assertEquals(92, EncodeParams.from(QualityMode.HIGH, 40, ImageFormatId.JPEG, false).quality)
        assertEquals(60, EncodeParams.from(QualityMode.SMALLER, 40, ImageFormatId.JPEG, false).quality)
        assertEquals(80, EncodeParams.from(QualityMode.BALANCED, 40, ImageFormatId.JPEG, false).quality)
        assertEquals(73, EncodeParams.from(QualityMode.CUSTOM, 73, ImageFormatId.JPEG, false).quality)

        val png = EncodeParams.from(QualityMode.HIGH, 40, ImageFormatId.PNG, losslessCapable = true)
        assertEquals(100, png.quality)
        assertEquals(false, png.forceLossless)
    }

    @Test
    fun areaDownscaleKeepsPureBlackWhiteHalves() {
        val width = 32
        val height = 8
        val pixels = IntArray(width * height) { i ->
            if ((i % width) < width / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val resized = RasterImage(width, height, pixels).resizedTo(16, 4)
        assertEquals(16, resized.width)
        assertEquals(4, resized.height)
        for (y in 0 until 4) {
            assertEquals(0xFF000000.toInt(), resized.pixel(0, y))
            assertEquals(0xFFFFFFFF.toInt(), resized.pixel(15, y))
        }
    }

    @Test
    fun flattenRemovesAlphaButKeepsColor() {
        val image = RasterImage(1, 1, intArrayOf(0x8040C080.toInt()))
        val flat = image.flattenedOn()
        assertTrue(!flat.hasAlpha)
        assertEquals(0xFF, flat.pixel(0, 0) ushr 24)
        assertEquals((0x40 * 0x80 + 0xFF * 0x7F) / 0xFF, flat.pixel(0, 0) shr 16 and 0xFF)
    }

    /** Builds a tiny JPEG: SOI + APP1(Exif with a single orientation tag) + EOI. */
    private fun minimalJpegWithOrientation(orientation: Int): ByteArray {
        val magick = "MM".toByteArray(Charsets.US_ASCII)
        val tiff = ByteArrayOutputStream()
        tiff.write(magick[0].toInt())
        tiff.write(magick[1].toInt())
        tiff.write(0); tiff.write(42)
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(8)
        tiff.write(0); tiff.write(1)
        tiff.write(0x01); tiff.write(0x12)
        tiff.write(0); tiff.write(3)
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(1)
        tiff.write(0); tiff.write(orientation)
        tiff.write(0); tiff.write(0)
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(0)

        val exif = "Exif".toByteArray(Charsets.US_ASCII)
        val payload = ByteArrayOutputStream()
        for (byte in exif) payload.write(byte.toInt())
        payload.write(0); payload.write(0)
        payload.write(tiff.toByteArray())
        val payloadBytes = payload.toByteArray()

        val jpeg = ByteArrayOutputStream()
        jpeg.write(0xFF); jpeg.write(0xD8)
        jpeg.write(0xFF); jpeg.write(0xE1)
        jpeg.write((payloadBytes.size + 2) shr 8 and 0xFF)
        jpeg.write((payloadBytes.size + 2) and 0xFF)
        jpeg.write(payloadBytes)
        jpeg.write(0xFF); jpeg.write(0xD9)
        return jpeg.toByteArray()
    }
}
