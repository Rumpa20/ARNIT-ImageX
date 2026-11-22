package com.arnit.imagex.formats

import com.arnit.imagex.decoder.DecoderRegistry
import com.arnit.imagex.encoder.EncoderRegistry

/**
 * Builds the honest support table shown on the Formats screen by combining
 * what is registered at runtime (decoders/encoders) with
 * free/premium availability rules.
 */
object FormatCatalog {

    const val FREE_MAX_DIMENSION = 4096
    const val PREMIUM_MAX_DIMENSION = 8192
    const val FREE_BATCH_LIMIT = 1
    const val PREMIUM_BATCH_LIMIT = 50

    /** Formats the free tier may use as input or output. */
    val FREE_INPUT = setOf(
        ImageFormatId.JPEG, ImageFormatId.PNG, ImageFormatId.WEBP, ImageFormatId.BMP, ImageFormatId.GIF
    )

    val FREE_OUTPUT = setOf(
        ImageFormatId.JPEG, ImageFormatId.PNG, ImageFormatId.WEBP, ImageFormatId.BMP
    )

    private val losslessEncodeFormats = setOf(
        ImageFormatId.PNG, ImageFormatId.BMP, ImageFormatId.TIFF, ImageFormatId.TGA,
        ImageFormatId.ICO, ImageFormatId.PNM, ImageFormatId.WEBP, ImageFormatId.PSD
    )

    fun supports(
        decoders: DecoderRegistry,
        encoders: EncoderRegistry,
        platformNotes: (ImageFormatId) -> String? = { PlatformFormats.hintFor(it) }
    ): List<FormatSupport> = ImageFormatId.entries.map { format ->
        val canDecode = decoders.decodableFormatIds().contains(format)
        val canEncode = encoders.encodableFormatIds().contains(format)
        FormatSupport(
            format = format,
            canDecode = canDecode,
            canEncode = canEncode,
            encodeLosslessPossible = canEncode && losslessEncodeFormats.contains(format) &&
                !format.inherentlyLossy,
            premiumOnlyDecode = canDecode && format !in FREE_INPUT,
            premiumOnlyEncode = canEncode && format !in FREE_OUTPUT,
            note = if (canDecode || canEncode) null else platformNotes(format)
                ?: format.description.ifBlank { null }
        )
    }

    /** Formats offered as conversion targets, ordered for the UI. */
    fun outputOrder(available: Set<ImageFormatId>): List<ImageFormatId> =
        ImageFormatId.defaultOutputOrder().filter { it in available } +
            (available - ImageFormatId.defaultOutputOrder().toSet())
}
