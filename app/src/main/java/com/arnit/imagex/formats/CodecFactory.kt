package com.arnit.imagex.formats

import com.arnit.imagex.decoder.AndroidPlatformRaster
import com.arnit.imagex.decoder.DecoderRegistry
import com.arnit.imagex.decoder.DdsDecoder
import com.arnit.imagex.decoder.IcoDecoder
import com.arnit.imagex.decoder.IcnsDecoder
import com.arnit.imagex.decoder.PlatformImageReader
import com.arnit.imagex.decoder.PnmDecoder
import com.arnit.imagex.decoder.PsdDecoder
import com.arnit.imagex.decoder.SvgDecoder
import com.arnit.imagex.decoder.TgaDecoder
import com.arnit.imagex.decoder.TiffDecoder
import com.arnit.imagex.decoder.WbmpDecoder
import com.arnit.imagex.encoder.AndroidBitmapEncoder
import com.arnit.imagex.encoder.BmpEncoder
import com.arnit.imagex.encoder.EncoderRegistry
import com.arnit.imagex.encoder.IcoEncoder
import com.arnit.imagex.encoder.PnmEncoder
import com.arnit.imagex.encoder.TgaEncoder
import com.arnit.imagex.encoder.TiffEncoder
import com.arnit.imagex.encoder.WbmpEncoder

/**
 * Single place where codecs are assembled. Registering an additional codec
 * never requires touching the engine or UI.
 */
object CodecFactory {

    fun createDecoders(platform: AndroidPlatformRaster): DecoderRegistry {
        val registry = DecoderRegistry(platform)
        registry.register(PlatformImageReader(ImageFormatId.JPEG, platform))
        registry.register(PlatformImageReader(ImageFormatId.PNG, platform))
        registry.register(PlatformImageReader(ImageFormatId.WEBP, platform))
        registry.register(PlatformImageReader(ImageFormatId.BMP, platform))
        registry.register(PlatformImageReader(ImageFormatId.GIF, platform))
        if (PlatformFormats.canDecodeHeic()) {
            registry.register(PlatformImageReader(ImageFormatId.HEIC, platform))
        }
        if (PlatformFormats.canDecodeAvif()) {
            registry.register(PlatformImageReader(ImageFormatId.AVIF, platform))
        }
        registry.register(IcoDecoder())
        registry.register(IcnsDecoder())
        registry.register(TgaDecoder())
        registry.register(TiffDecoder())
        registry.register(WbmpDecoder())
        registry.register(PnmDecoder())
        registry.register(DdsDecoder())
        registry.register(PsdDecoder())
        registry.register(SvgDecoder())
        return registry
    }

    fun createEncoders(platform: AndroidPlatformRaster): EncoderRegistry {
        val registry = EncoderRegistry()
        registry.register(AndroidBitmapEncoder(ImageFormatId.JPEG, platform))
        registry.register(AndroidBitmapEncoder(ImageFormatId.PNG, platform))
        registry.register(AndroidBitmapEncoder(ImageFormatId.WEBP, platform))
        registry.register(BmpEncoder())
        registry.register(TgaEncoder())
        registry.register(TiffEncoder())
        registry.register(IcoEncoder())
        registry.register(WbmpEncoder())
        registry.register(PnmEncoder())
        return registry
    }
}
