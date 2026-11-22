package com.arnit.imagex.formats

/**
 * Pure content sniffing: magic byte matching with light-header parsing.
 * Runs on the first bytes of a file so file extensions can never lie about
 * the real format (invalid/misleading extensions are detected and reported).
 */
object FormatDetector {

    data class Sniff(
        val format: ImageFormatId,
        val confidence: Int, // 3 = magic match, 2 = extension only, 1 = guess
    )

    fun sniff(
        head: ByteArray,
        fileName: String? = null,
        declaredMime: String? = null
    ): Sniff? {
        val byMagic = byMagic(head)
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() }
        val byExt = ImageFormatId.fromExtension(ext ?: "")
        val byMime = ImageFormatId.fromMime(declaredMime)

        val magicConfidence = byMagic?.let { if (it in setOf(ImageFormatId.WBMP)) 1 else 3 } ?: 0
        val candidates = listOfNotNull(
            byMagic?.let { it to magicConfidence },
            byExt?.let { it to 2 },
            byMime?.let { it to 2 }
        )
        val best = candidates.maxByOrNull { it.second } ?: return null
        return Sniff(best.first, best.second)
    }

    fun sniffAll(data: ByteArray, fileName: String?, declaredMime: String?): Sniff? =
        sniff(data.copyOf(minOf(data.size, 512)), fileName, declaredMime)

    private fun byMagic(head: ByteArray): ImageFormatId? {
        if (head.size < 4) return null
        fun u(i: Int) = head[i].toInt() and 0xFF

        if (u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF) return ImageFormatId.JPEG
        if (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) return ImageFormatId.PNG
        if (head.size >= 6 &&
            head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() &&
            head[2] == 'F'.code.toByte() && head[3] == '8'.code.toByte()
        ) return ImageFormatId.GIF
        if (u(0) == 0x42 && u(1) == 0x4D) return ImageFormatId.BMP
        if (u(0) == 0x49 && u(1) == 0x49 && u(2) == 0x2A && u(3) == 0x00) return ImageFormatId.TIFF
        if (u(0) == 0x4D && u(1) == 0x4D && u(2) == 0x00 && u(3) == 0x2A) return ImageFormatId.TIFF
        if (u(0) == 0x00 && u(1) == 0x00 && u(2) == 0x01 && u(3) == 0x00) return ImageFormatId.ICO
        if (u(0) == 0x00 && u(1) == 0x00 && u(2) == 0x02 && u(3) == 0x00) return ImageFormatId.ICO
        if (head.size >= 12 && head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() &&
            head[2] == 'F'.code.toByte() && head[3] == 'F'.code.toByte() &&
            head[8] == 'W'.code.toByte() && head[9] == 'E'.code.toByte() &&
            head[10] == 'B'.code.toByte() && head[11] == 'P'.code.toByte()
        ) return ImageFormatId.WEBP
        if (head[0] == 'i'.code.toByte() && head[1] == 'c'.code.toByte() &&
            head[2] == 'n'.code.toByte() && head[3] == 's'.code.toByte()
        ) return ImageFormatId.ICNS
        if (u(0) == 0x44 && u(1) == 0x44 && u(2) == 0x53 && u(3) == 0x20) return ImageFormatId.DDS
        if (u(0) == 0x38 && u(1) == 0x42 && u(2) == 0x50 && u(3) == 0x53) return ImageFormatId.PSD

        // ISO BMFF base: HEIC/HEIF/AVIF/CR3 all carry "ftyp" at offset 4
        if (head.size >= 12) {
            if (head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
                head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte()
            ) {
                val brand = String(head, 8, 4, Charsets.US_ASCII)
                return when {
                    brand.startsWith("avi") -> ImageFormatId.AVIF
                    brand.startsWith("hei") || brand.startsWith("mif") ||
                        brand.startsWith("msf") || brand.startsWith("mif1") ||
                        brand.startsWith("hevc") || brand.startsWith("mfx") -> ImageFormatId.HEIC
                    brand.startsWith("crx") -> null // Canon CR3: camera raw, not decodable
                    else -> null
                }
            }
        }

        // JPEG 2000 signature box
        if (head.size >= 12 && u(0) == 0x00 && u(1) == 0x00 && u(2) == 0x00 && u(3) == 0x0C &&
            head[4] == 'j'.code.toByte() && head[5] == 'P'.code.toByte() &&
            head[6] == ' '.code.toByte() && head[7] == ' '.code.toByte()
        ) return ImageFormatId.JPEG2000
        if (u(0) == 0xFF && u(1) == 0x4F && u(2) == 0xFF && u(3) == 0x51) return ImageFormatId.JPEG2000

        // PNM: "P1".."P7" followed by whitespace
        if (u(0) == 'P'.code && u(1) in 0x31..0x36 &&
            (u(2) == 0x20 || u(2) == 0x0A || u(2) == 0x0D || u(2) == 0x09)
        ) return ImageFormatId.PNM

        // WBMP: type 0 header (0x00, fix header 0x00) - weak evidence only
        if (u(0) == 0x00 && u(1) == 0x00) return ImageFormatId.WBMP

        // SVG detection needs bytes beyond head start; checked in the SVG reader
        return null
    }

    fun looksLikeSvg(head: ByteArray): Boolean {
        val text = String(head, Charsets.US_ASCII).lowercase()
        val idx = text.indexOf("<svg")
        if (idx >= 0) return true
        if (text.contains("<?xml") || text.startsWith("</")) {
            // handled by caller potentially reading more bytes
        }
        return false
    }
}
