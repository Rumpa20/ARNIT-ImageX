package com.arnit.imagex.formats

/**
 * Every format the app knows about, including formats that are currently *not*
 * decodable/encodable on-device. Availability is always resolved at runtime
 * (see FormatSupport) so the UI can honestly show what works on this build.
 */
enum class ImageFormatId(
    val displayName: String,
    val mime: String,
    val extensions: List<String>,
    val supportsAlpha: Boolean,
    val inherentlyLossy: Boolean,
    val animatedPossible: Boolean = false,
    val family: Family = Family.RASTER,
    val description: String = ""
) {
    JPEG(
        "JPG / JPEG", "image/jpeg", listOf("jpg", "jpeg"),
        supportsAlpha = false, inherentlyLossy = true, description = "Universal photo format; no transparency support."
    ),
    JPEG2000(
        "JPEG 2000", "image/jp2", listOf("jp2", "j2k", "jpx"),
        supportsAlpha = true, inherentlyLossy = false, family = Family.SPECIAL,
        description = "Requires a JPEG 2000 decoder that is not bundled with this build."
    ),
    PNG(
        "PNG", "image/png", listOf("png"),
        supportsAlpha = true, inherentlyLossy = false, description = "Lossless; recommended for graphics and screenshots."
    ),
    WEBP(
        "WebP", "image/webp", listOf("webp"),
        supportsAlpha = true, inherentlyLossy = false, animatedPossible = true,
        description = "Modern format; lossless and lossy modes."
    ),
    AVIF(
        "AVIF", "image/avif", listOf("avif"),
        supportsAlpha = true, inherentlyLossy = false, animatedPossible = true,
        description = "Modern high-compression format; decode needs Android 12+, encode needs Android 14+."
    ),
    HEIC(
        "HEIC / HEIF", "image/heif", listOf("heic", "heif", "hif"),
        supportsAlpha = true, inherentlyLossy = false, animatedPossible = true,
        description = "Apple/phone photo format; encode depends on device hardware."
    ),
    BMP(
        "BMP", "image/bmp", listOf("bmp", "dib"),
        supportsAlpha = true, inherentlyLossy = false, description = "Windows bitmap; large but lossless."
    ),
    GIF(
        "GIF", "image/gif", listOf("gif"),
        supportsAlpha = true, inherentlyLossy = false, animatedPossible = true,
        description = "Animated images are converted from their first frame on this build."
    ),
    TIFF(
        "TIFF / TIF", "image/tiff", listOf("tif", "tiff"),
        supportsAlpha = true, inherentlyLossy = false, family = Family.PRO,
        description = "Baseline TIFF; handle with care on 16-bit or exotic variants."
    ),
    SVG(
        "SVG", "image/svg+xml", listOf("svg"),
        supportsAlpha = true, inherentlyLossy = false, family = Family.VECTOR,
        description = "Vector format; conversion rasterizes the vector content."
    ),
    ICO(
        "ICO", "image/vnd.microsoft.icon", listOf("ico", "cur"),
        supportsAlpha = true, inherentlyLossy = false, description = "Windows icon format."
    ),
    ICNS(
        "ICNS", "image/x-icns", listOf("icns"),
        supportsAlpha = true, inherentlyLossy = false, family = Family.PRO,
        description = "macOS icon format; PNG-representation entries are supported."
    ),
    TGA(
        "TGA", "image/x-tga", listOf("tga", "icb", "vda", "vst"),
        supportsAlpha = true, inherentlyLossy = false, family = Family.PRO,
        description = "Targa, common in graphics pipelines."
    ),
    DDS(
        "DDS", "image/vnd-ms.dds", listOf("dds"),
        supportsAlpha = true, inherentlyLossy = true, family = Family.PRO,
        description = "DirectDraw surface; block-compressed texture formats (DXT1/3/5) and raw RGBA."
    ),
    WBMP(
        "WBMP", "image/vnd.wap.wbmp", listOf("wbmp"),
        supportsAlpha = false, inherentlyLossy = false, description = "Wireless bitmap; monochrome 1-bit."
    ),
    PNM(
        "PNM (PPM/PGM/PBM)", "image/x-portable-anymap", listOf("ppm", "pgm", "pbm", "pnm"),
        supportsAlpha = false, inherentlyLossy = false, family = Family.PRO,
        description = "Portable anymap family (PPM, PGM, PBM)."
    ),
    PSD(
        "PSD", "image/vnd.adobe.photoshop", listOf("psd"),
        supportsAlpha = true, inherentlyLossy = false, family = Family.PRO,
        description = "Photoshop composite (flattened) image; layers are not merged on export."
    ),
    DNG(
        "DNG", "image/x-adobe-dng", listOf("dng"),
        supportsAlpha = false, inherentlyLossy = false, family = Family.CAMERA_RAW,
        description = "Camera RAW requires a per-sensor decoder not bundled with this build."
    ),
    CR2(
        "CR2", "image/x-canon-cr2", listOf("cr2"),
        supportsAlpha = false, inherentlyLossy = false, family = Family.CAMERA_RAW,
        description = "Camera RAW requires a per-sensor decoder not bundled with this build."
    ),
    CR3(
        "CR3", "image/x-canon-cr3", listOf("cr3"),
        supportsAlpha = false, inherentlyLossy = false, family = Family.CAMERA_RAW,
        description = "Camera RAW requires a per-sensor decoder not bundled with this build."
    ),
    NEF(
        "NEF", "image/x-nikon-nef", listOf("nef"),
        supportsAlpha = false, inherentlyLossy = false, family = Family.CAMERA_RAW,
        description = "Camera RAW requires a per-sensor decoder not bundled with this build."
    ),
    ARW(
        "ARW", "image/x-sony-arw", listOf("arw"),
        supportsAlpha = false, inherentlyLossy = false, family = Family.CAMERA_RAW,
        description = "Camera RAW requires a per-sensor decoder not bundled with this build."
    );

    /** Grouped label for camera raw formats shown in the format list. */
    val isCameraRawGroup: Boolean get() = family == Family.CAMERA_RAW

    enum class Family {
        RASTER, VECTOR, PRO, SPECIAL, CAMERA_RAW
    }

    companion object {
        fun fromExtension(ext: String): ImageFormatId? {
            val e = ext.lowercase().trim('.')
            return entries.firstOrNull { e in it.extensions }
        }

        fun fromMime(mime: String?): ImageFormatId? {
            if (mime == null) return null
            val m = mime.substringBefore(';').trim().lowercase()
            return entries.firstOrNull { it.mime == m }
        }

        /** Output formats that can be encoded by this build's bundled encoders. */
        fun defaultOutputOrder(): List<ImageFormatId> = listOf(
            PNG, JPEG, WEBP, AVIF, HEIC, BMP, TIFF, ICO, TGA, WBMP, PNM
        )
    }
}

/** Honest runtime availability for every catalog format. */
data class FormatSupport(
    val format: ImageFormatId,
    val canDecode: Boolean,
    val canEncode: Boolean,
    val encodeLosslessPossible: Boolean,
    val premiumOnlyDecode: Boolean = false,
    val premiumOnlyEncode: Boolean = false,
    val note: String? = null
) {
    val available: Boolean get() = canDecode || canEncode

    /** Mark used by the Formats screen. */
    val statusLabel: String
        get() = when {
            canDecode && canEncode -> if (encodeLosslessPossible) "Decode + Encode (lossless)" else "Decode + Encode"
            canDecode -> "Decode only"
            canEncode -> "Encode only"
            else -> "Not available on this build"
        }
}
