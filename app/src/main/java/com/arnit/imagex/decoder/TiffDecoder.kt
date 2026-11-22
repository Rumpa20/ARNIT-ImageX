package com.arnit.imagex.decoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.ImageFormatId

/**
 * Baseline TIFF decoder:
 *  - little and big endian, classic IFD layout
 *  - strips (tiles are rejected with a clear message)
 *  - compression: none, LZW, Deflate, Adobe Deflate, PackBits
 *  - photometric: WhiteIsZero, BlackIsZero, RGB, palette
 *  - 1/2/4/8/16-bit samples, extra samples as alpha, planar and chunky
 * Reads the first image (page 0). JPEG-in-TIFF and CCITT fail with a clear
 * message instead of producing garbage.
 */
class TiffDecoder : ImageReader {
    override val format = ImageFormatId.TIFF

    override fun canHandle(data: ByteArray, fileName: String?): Boolean {
        if (data.size < 4) return false
        val b0 = data[0].toInt() and 0xFF
        val b1 = data[1].toInt() and 0xFF
        val b2 = data[2].toInt() and 0xFF
        val b3 = data[3].toInt() and 0xFF
        val le = b0 == 0x49 && b1 == 0x49 && b2 == 0x2A && b3 == 0x00
        val be = b0 == 0x4D && b1 == 0x4D && b2 == 0x00 && b3 == 0x2A
        return le || be
    }

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage =
        try {
            parse(data, limits)
        } catch (e: ConversionException) {
            throw e
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The TIFF image could not be decoded.", e.message, e)
        }

    private fun parse(data: ByteArray, limits: DecodeLimits): RasterImage {
        if (data.size < 8) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TIFF file is too short.")
        val le = (data[0].toInt() and 0xFF) == 0x49
        if ((if (le) ByteKit.le16(data, 2) else ByteKit.be16(data, 2)) != 42) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "Not a valid TIFF file.")
        }

        var offset = if (le) ByteKit.le32(data, 4) else ByteKit.be32(data, 4)
        var tags: Tags? = null
        var guard = 0
        while (offset != 0 && offset + 2 <= data.size && guard++ < 256) {
            val read = readIfd(data, offset, le)
            if (read.hasImageData) {
                tags = read
                break
            }
            offset = read.nextIfd
        }
        val t = tags ?: throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "No image data found in the TIFF file.")

        val width = t.int(256) ?: throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TIFF width tag missing.")
        val height = t.int(257) ?: throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TIFF height tag missing.")
        if (width <= 0 || height <= 0) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TIFF has invalid dimensions.")
        checkSize(width, height, limits)

        val bits = t.ints(258) ?: intArrayOf(1)
        val compression = t.int(259) ?: 1
        val photometric = t.int(262) ?: -1
        val samples = t.int(277) ?: bits.size.coerceAtLeast(1)
        var rowsPerStrip = t.int(278) ?: height
        if (rowsPerStrip <= 0) rowsPerStrip = height
        val planar = t.int(284) ?: 1
        val predictor = t.int(317) ?: 1
        val sampleFormats = t.ints(339)
        val extraSamples = t.ints(338)
        var colormap = t.ints(320)
        if (t.has(322)) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Tiled TIFF layout is not supported by this build.")
        }

        if (sampleFormats != null && sampleFormats.any { it == 3 || it == 6 }) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Floating point TIFF pixels are not supported.")
        }
        if (photometric == 4) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Transparency-mask TIFF files are not supported.")
        }
        if (bits.any { it !in VALID_BIT_DEPTHS }) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported TIFF bit depth: ${bits.joinToString("/")}.")
        }
        if (bits.size > 1 && bits.distinct().size != 1) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Mixed per-channel bit depths are not supported.")
        }
        if (photometric !in VALID_PHOTOMETRIC) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported TIFF photometric interpretation $photometric.")
        }
        if (planar != 1 && planar != 2) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported TIFF planar configuration $planar.")
        }

        val offsets = t.ints(273)
        val counts = t.ints(279)
        if (offsets == null || counts == null || offsets.size != counts.size) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TIFF strip table is invalid.")
        }

        val bitsPerSample = bits[0]
        val channels = samples
        val stripsPerPlane = (height + rowsPerStrip - 1) / rowsPerStrip
        if (planar == 2 && offsets.size != stripsPerPlane * channels) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TIFF plane/strip count mismatch.")
        }
        colormap = colormap?.map { (it ushr 8).coerceIn(0, 255) }?.toIntArray()

        val strips = ArrayList<ByteArray>(offsets.size)
        for (i in offsets.indices) {
            if (offsets[i] < 0 || counts[i] < 0 || offsets[i] + counts[i] > data.size) {
                throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TIFF strip data is out of bounds.")
            }
            val raw = decompress(data, offsets[i], counts[i], compression)
            strips.add(applyPredictor(raw, predictor, width, rowsPerStrip, channels, bitsPerSample, planar))
        }

        val isPlanar = planar == 2
        val store = SampleStore(strips, isPlanar, channels, rowsPerStrip, width, height, bitsPerSample, le)

        val hasAlphaChannel =
            (photometric == 2 && channels >= 4) ||
                (photometric in GRAY_PHOTOMETRIC && channels >= 2)

        val pixels = IntArray(width * height)
        val paletteSize = colormap?.size ?: 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val out: Int = when (photometric) {
                    2, 0 -> {
                        var r = store.sample(x, y, 0)
                        var g = store.sample(x, y, 1)
                        var b = store.sample(x, y, 2)
                        if (photometric == 0) { r = 255 - r; g = 255 - g; b = 255 - b }
                        val a = if (hasAlphaChannel) store.sample(x, y, 3) else 255
                        (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    1 -> {
                        val v = store.sample(x, y, 0)
                        val a = if (hasAlphaChannel) store.sample(x, y, 1) else 255
                        (a shl 24) or (v shl 16) or (v shl 8) or v
                    }
                    else -> {
                        val idx = store.sample(x, y, 0)
                        if (paletteSize < 3 || (2 * paletteSize / 3) + idx >= paletteSize) {
                            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "TIFF palette is missing or too small.")
                        }
                        val third = paletteSize / 3
                        val r = colormap!![idx]
                        val g = colormap[third + idx]
                        val b = colormap[2 * third + idx]
                        0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                    }
                }
                pixels[y * width + x] = out
            }
        }
        return RasterImage(width, height, pixels)
    }

    private fun decompress(data: ByteArray, off: Int, count: Int, compression: Int): ByteArray {
        if (count <= 0) return ByteArray(0)
        return when (compression) {
            1 -> data.copyOfRange(off, off + count)
            5 -> tiffLzwDecode(data, off, count)
            8, 32946 -> ByteKit.inflate(data, off, count, count * 4)
            32773 -> ByteKit.unpackBits(data, off, off + count, count * 2)
            7 -> throw ConversionException(
                ErrorCode.DECODER_FAILED, "JPEG-encoded TIFF files are not supported by this build."
            )
            2, 3, 4 -> throw ConversionException(
                ErrorCode.DECODER_FAILED, "CCITT fax-encoded TIFF files are not supported by this build."
            )
            else -> throw ConversionException(ErrorCode.DECODER_FAILED, "Unsupported TIFF compression code $compression.")
        }
    }

    private fun applyPredictor(
        buf: ByteArray, predictor: Int, width: Int, rowsPerStrip: Int,
        channels: Int, bits: Int, planar: Int
    ): ByteArray {
        if (predictor != 2) return buf
        if (bits != 8) {
            throw ConversionException(
                ErrorCode.DECODER_FAILED, "Horizontal predictor with ${bits}-bit samples is not supported."
            )
        }
        val stride = if (planar == 2) width else width * channels
        val rows = buf.size / stride
        for (r in 0 until rows) {
            val base = r * stride
            for (i in 1 until stride) {
                buf[base + i] =
                    (((buf[base + i].toInt() and 0xFF) + (buf[base + i - 1].toInt() and 0xFF)) and 0xFF).toByte()
            }
        }
        return buf
    }

    /** Classic TIFF LZW with early code-width change. */
    private fun tiffLzwDecode(data: ByteArray, off: Int, count: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(maxOf(count, 2) * 2)
        val table = ArrayList<ByteArray>(4096)
        var prev: ByteArray? = null
        var nextCode = 258
        var codeWidth = 9
        var bitBuffer = 0L
        var buffered = 0
        var i = off
        val end = off + count

        fun resetTable() {
            table.clear()
            for (c in 0 until 256) table.add(byteArrayOf(c.toByte()))
            nextCode = 258
            codeWidth = 9
            prev = null
            bitBuffer = 0
            buffered = 0
        }
        resetTable()

        while (true) {
            if (out.size() > 512 * 1024 * 1024) {
                throw ConversionException(ErrorCode.IMAGE_TOO_LARGE, "TIFF decodes to more than 512 MB of pixel data.")
            }
            while (buffered < codeWidth) {
                if (i >= end) return out.toByteArray()
                bitBuffer = (bitBuffer shl 8) or (data[i].toLong() and 0xFF)
                i++
                buffered += 8
            }
            val code = (bitBuffer shr (buffered - codeWidth)).toInt() and ((1 shl codeWidth) - 1)
            buffered -= codeWidth
            when {
                code == 257 -> return out.toByteArray()
                code == 256 -> resetTable()
                else -> {
                    val entry: ByteArray = if (code < table.size) {
                        table[code]
                    } else {
                        val p = prev
                            ?: throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "Corrupt TIFF LZW stream.")
                        p.copyOf(p.size + 1).also { it[p.size] = p[0] }
                    }
                    out.write(entry)
                    val p = prev
                    if (p != null && nextCode < 4096) {
                        table.add(p.copyOf(p.size + 1).also { it[p.size] = entry[0] })
                        nextCode++
                        when (nextCode) {
                            511 -> codeWidth = 10
                            1023 -> codeWidth = 11
                            2047 -> codeWidth = 12
                        }
                    }
                    prev = entry
                }
            }
        }
    }

    private fun readIfd(data: ByteArray, offset: Int, le: Boolean): Tags {
        val tags = Tags(data, le)
        if (offset + 2 > data.size) return tags
        val count = bytesToInt(data, offset, 2, le)
        var o = offset + 2
        repeat(count) {
            if (o + 12 <= data.size) {
                val tag = bytesToInt(data, o, 2, le)
                val type = bytesToInt(data, o + 2, 2, le)
                val n = bytesToInt(data, o + 4, 4, le)
                val typeSize = when (type) {
                    1, 2, 6, 7 -> 1
                    3, 8 -> 2
                    4, 9, 11 -> 4
                    5, 10, 12 -> 8
                    else -> 0
                }
                if (typeSize > 0 && n > 0 && n.toLong() * typeSize <= data.size.toLong()) {
                    val valueOffset = if (n.toLong() * typeSize > 4) {
                        val ptr = bytesToInt(data, o + 8, 4, le)
                        if (ptr < 0 || ptr.toLong() + n.toLong() * typeSize > data.size.toLong()) -1 else ptr
                    } else o + 8
                    if (valueOffset >= 0) tags.add(tag, type, n, valueOffset)
                }
            }
            o += 12
        }
        val next = offset + 2 + count * 12
        tags.nextIfd = if (next + 4 <= data.size) bytesToInt(data, next, 4, le) else 0
        return tags
    }

    private fun bytesToInt(data: ByteArray, offset: Int, bytes: Int, le: Boolean): Int {
        if (offset + bytes > data.size) return 0
        var v = 0
        if (le) {
            for (i in 0 until bytes) v = v or ((data[offset + i].toInt() and 0xFF) shl (8 * i))
        } else {
            for (i in 0 until bytes) v = (v shl 8) or (data[offset + i].toInt() and 0xFF)
        }
        return v
    }

    private companion object {
        val VALID_BIT_DEPTHS = intArrayOf(1, 2, 4, 8, 16)
        val VALID_PHOTOMETRIC = intArrayOf(0, 1, 2, 3)
        val GRAY_PHOTOMETRIC = intArrayOf(0, 1)
    }
}

/** Storage for one TIFF tag: type/count/offset triple referencing the file bytes. */
private class Tags(private val data: ByteArray, private val le: Boolean) {
    var nextIfd = 0
    private val entries = HashMap<Int, Entry>()

    val hasImageData: Boolean
        get() = entries.containsKey(256) && (entries.containsKey(273) || entries.containsKey(324))

    fun has(tag: Int): Boolean = entries.containsKey(tag)

    fun add(tag: Int, type: Int, count: Int, offset: Int) {
        entries[tag] = Entry(type, count, offset)
    }

    fun int(tag: Int): Int? = ints(tag)?.firstOrNull()

    fun ints(tag: Int): IntArray? {
        val e = entries[tag] ?: return null
        val size = when (e.type) {
            1, 2, 6, 7 -> 1
            3, 8 -> 2
            4, 9, 11 -> 4
            5, 10, 12 -> 8
            else -> return null
        }
        val out = IntArray(e.count)
        for (i in 0 until e.count) {
            val base = e.offset + i * size
            if (base + size > data.size) return null
            out[i] = when (e.type) {
                1, 6, 7 -> data[base].toInt() and 0xFF
                3, 8 -> if (le) ByteKit.le16(data, base) else ByteKit.be16(data, base)
                4, 9, 11 -> if (le) ByteKit.le32(data, base) else ByteKit.be32(data, base)
                5, 10, 12 -> if (le) ByteKit.le32(data, base) else ByteKit.be32(data, base)
                else -> return null
            }
        }
        return out
    }

    private class Entry(val type: Int, val count: Int, val offset: Int)
}

/** Locates pixel samples across TIFF strips (chunky or planar, any supported depth). */
private class SampleStore(
    private val strips: List<ByteArray>,
    private val isPlanar: Boolean,
    private val channels: Int,
    private val rowsPerStrip: Int,
    private val width: Int,
    private val height: Int,
    private val bits: Int,
    private val le: Boolean
) {
    private val stripsPerPlane: Int = (height + rowsPerStrip - 1) / rowsPerStrip
    private val bytesPerSample: Int = (bits + 7) / 8
    private val rowSamples: Int = if (isPlanar) width else width * channels
    private val rowBytes: Int = (rowSamples * bits + 7) / 8

    fun sample(x: Int, y: Int, channel: Int): Int {
        val stripIndex = if (isPlanar) {
            channel * stripsPerPlane + y / rowsPerStrip
        } else {
            y / rowsPerStrip
        }
        if (stripIndex >= strips.size) return 0
        val buf = strips[stripIndex]
        val rowInStrip = y % rowsPerStrip
        val rowStart = rowInStrip * rowBytes
        return when (bits) {
            16 -> {
                val o = rowStart + (if (isPlanar) x else x * channels + channel) * 2
                if (o + 1 >= buf.size) 0
                else {
                    val v = if (le) ByteKit.le16(buf, o) else ByteKit.be16(buf, o)
                    (v shr 8) and 0xFF
                }
            }
            8 -> {
                val o = rowStart + (if (isPlanar) x else x * channels + channel)
                if (o < buf.size) buf[o].toInt() and 0xFF else 0
            }
            else -> {
                val bitPos = rowStart * 8 + (if (isPlanar) x else x * channels + channel) * bits
                val byteIndex = bitPos / 8
                val firstBitInByte = bitPos % 8
                if (byteIndex >= buf.size) return 0
                var v = 0
                for (i in 0 until bits) {
                    val bi = byteIndex * 8 + firstBitInByte + i
                    if (bi / 8 >= buf.size) break
                    val bit = (buf[bi / 8].toInt() shr (7 - (bi % 8))) and 1
                    v = (v shl 1) or bit
                }
                (v * 255) / ((1 shl bits) - 1)
            }
        }
    }
}
