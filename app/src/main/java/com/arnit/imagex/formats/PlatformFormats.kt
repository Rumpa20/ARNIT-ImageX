package com.arnit.imagex.formats

import android.os.Build

/** Honest OS capabilities for the formats the platform itself provides. */
object PlatformFormats {

    /** HEIF/HEIC decoding is available from Android 9 on supporting devices. */
    fun canDecodeHeic(): Boolean = Build.VERSION.SDK_INT >= 28

    /** AVIF decoding is available from Android 12. */
    fun canDecodeAvif(): Boolean = Build.VERSION.SDK_INT >= 31

    /** WebP lossless encoding needed Android 11. */
    fun canEncodeWebpLossless(): Boolean = Build.VERSION.SDK_INT >= 30

    fun hintFor(format: ImageFormatId): String? = when (format) {
        ImageFormatId.AVIF -> if (!canDecodeAvif()) "AVIF decode needs Android 12+ on this device" else "AVIF is decode-only: the public Android SDK has no AVIF encoder"
        ImageFormatId.HEIC -> if (!canDecodeHeic()) "HEIC decode needs Android 9+ on this device" else "HEIC is decode-only: the public Android SDK has no HEIF encoder"
        ImageFormatId.JPEG2000 -> "No JPEG 2000 decoder is bundled with this build"
        ImageFormatId.DNG, ImageFormatId.CR2, ImageFormatId.CR3, ImageFormatId.NEF, ImageFormatId.ARW ->
            "Camera RAW support requires a per-sensor decoder that is not bundled with this build"
        else -> null
    }
}
