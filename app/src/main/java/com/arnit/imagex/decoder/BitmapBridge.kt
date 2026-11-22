package com.arnit.imagex.decoder

import android.graphics.Bitmap
import com.arnit.imagex.core.RasterImage

/** Conversion helpers between android.graphics.Bitmap and the pure RasterImage model. */
object BitmapBridge {

    fun toRasterImage(bitmap: Bitmap): RasterImage {
        val argb = bitmap.config == Bitmap.Config.ARGB_8888
        val image = if (argb) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        }
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getPixels(pixels, 0, w, 0, 0, w, h)
        if (image !== bitmap) image.recycle()
        return RasterImage(w, h, pixels)
    }

    fun toBitmap(image: RasterImage): Bitmap {
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(image.pixels, 0, image.width, 0, 0, image.width, image.height)
        return bitmap
    }
}
