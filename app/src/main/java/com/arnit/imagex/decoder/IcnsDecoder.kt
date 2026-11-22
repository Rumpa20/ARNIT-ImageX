package com.arnit.imagex.decoder

import com.arnit.imagex.core.ByteKit
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import com.arnit.imagex.core.RasterImage
import com.arnit.imagex.formats.ImageFormatId

/**
 * Apple ICNS decoder. Modern ICNS files embed PNG data in ic07..ic14/icp*
 * entries; those are decoded via the platform raster decoder. Legacy
 * Apple-drawing entries are rejected with a clear message.
 */
class IcnsDecoder : ImageReader {
    override val format = ImageFormatId.ICNS

    override fun canHandle(data: ByteArray, fileName: String?): Boolean =
        data.size >= 8 &&
            data[0] == 'i'.code.toByte() && data[1] == 'c'.code.toByte() &&
            data[2] == 'n'.code.toByte() && data[3] == 's'.code.toByte()

    override fun decode(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage =
        try {
            parse(data, limits, platform)
        } catch (e: ConversionException) {
            throw e
        } catch (e: Exception) {
            throw ConversionException(ErrorCode.DECODER_FAILED, "The ICNS file could not be decoded.", e.message, e)
        }

    private fun parse(data: ByteArray, limits: DecodeLimits, platform: PlatformRaster): RasterImage {
        if (data.size < 8) throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "ICNS file is too short.")
        val totalSize = ByteKit.be32(data, 4)
        if (totalSize <= 0 || totalSize > data.size) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "ICNS header size is invalid.")
        }

        data class Entry(val type: String, val offset: Int, val length: Int)

        val entries = ArrayList<Entry>()
        var p = 8
        while (p + 8 <= data.size) {
            val type = String(data, p, 4, Charsets.US_ASCII)
            val len = ByteKit.be32(data, p + 4)
            if (len < 8 || p + len > data.size) break
            entries.add(Entry(type, p + 8, len - 8))
            p += len
        }
        if (entries.isEmpty()) {
            throw ConversionException(ErrorCode.CORRUPTED_IMAGE, "ICNS file contains no entries.")
        }

        // Prefer the largest PNG-representation entry this build can decode
        val pngTypes = entries
            .filter { it.type in PNG_ENTRY_TYPES }
            .sortedByDescending { PNG_ENTRY_TYPES[it.type] ?: 0 }

        var lastError: ConversionException? = null
        for (entry in pngTypes) {
            val bytes = data.copyOfRange(entry.offset, entry.offset + entry.length)
            if (!ByteKit.startsWith(bytes, 0, PNG_SIGNATURE)) {
                lastError = lastError ?: ConversionException(
                    ErrorCode.DECODER_FAILED,
                    "ICNS entry ${entry.type} uses JPEG 2000 content, which this build cannot decode."
                )
                continue
            }
            val decoded = runCatching { platform.decode(bytes, Int.MAX_VALUE, false) }.getOrNull()
            if (decoded != null) {
                checkSize(decoded.width, decoded.height, limits)
                return decoded
            }
        }
        throw lastError ?: ConversionException(
            ErrorCode.DECODER_FAILED,
            "No decodeable PNG-representation entries found in the ICNS file."
        )
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        /** entry type -> nominal pixel width, used to pick the largest match */
        val PNG_ENTRY_TYPES = mapOf(
            "icp4" to 16,
            "icp5" to 32,
            "icp6" to 64,
            "ic07" to 128,
            "ic08" to 256,
            "ic09" to 512,
            "ic10" to 1024,
            "ic11" to 32,
            "ic12" to 64,
            "ic13" to 256,
            "ic14" to 512
        )
    }
}
