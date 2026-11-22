package com.arnit.imagex.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Device-independent RGBA raster. Pixels are ARGB (0xAARRGGBB) ints in row-major order.
 * Codecs (decoders/encoders) work on this model; the Android layer converts to/from
 * android.graphics.Bitmap. Keeping this pure lets it be unit tested without a device.
 */
class RasterImage(val width: Int, val height: Int, val pixels: IntArray) {

    init {
        require(width > 0 && height > 0) { "Invalid raster dimensions ${width}x$height" }
        if (pixels.size != width * height) {
            throw IllegalArgumentException(
                "Pixel buffer size ${pixels.size} does not match ${width}x$height"
            )
        }
    }

    private val hasAlphaCache: Boolean by lazy {
        for (p in pixels) if (p ushr 24 != 0xFF) return@lazy true
        false
    }

    /** True when at least one pixel is not fully opaque. */
    val hasAlpha: Boolean get() = hasAlphaCache

    fun pixel(x: Int, y: Int): Int = pixels[y * width + x]

    fun setPixel(x: Int, y: Int, value: Int) {
        pixels[y * width + x] = value
    }

    /** Returns a copy with all pixels composited over [background] (fully opaque result). */
    fun flattenedOn(background: Int = 0xFFFFFFFF.toInt()): RasterImage {
        val out = IntArray(pixels.size)
        val bgR = background shr 16 and 0xFF
        val bgG = background shr 8 and 0xFF
        val bgB = background and 0xFF
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            out[i] = when {
                a == 0xFF -> 0xFF000000.toInt() or (p and 0xFFFFFF)
                a == 0 -> background
                else -> {
                    val r = p shr 16 and 0xFF
                    val g = p shr 8 and 0xFF
                    val b = p and 0xFF
                    val outR = (r * a + bgR * (0xFF - a)) / 0xFF
                    val outG = (g * a + bgG * (0xFF - a)) / 0xFF
                    val outB = (b * a + bgB * (0xFF - a)) / 0xFF
                    0xFF000000.toInt() or (outR shl 16) or (outG shl 8) or outB
                }
            }
        }
        return RasterImage(width, height, out)
    }

    fun rotatedBy90Clockwise(): RasterImage {
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[x * height + (height - 1 - y)] = pixels[y * width + x]
            }
        }
        return RasterImage(height, width, out)
    }

    fun flippedHorizontally(): RasterImage {
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[y * width + (width - 1 - x)] = pixels[y * width + x]
            }
        }
        return RasterImage(width, height, out)
    }

    fun rotated180(): RasterImage {
        val out = IntArray(pixels.size)
        for (i in pixels.indices) out[pixels.size - 1 - i] = pixels[i]
        return RasterImage(width, height, out)
    }

    /**
     * Applies EXIF orientation (1-8). Returns a raster already oriented the way it should
     * be displayed; the orientation tag is consumed at this point.
     */
    fun applyExifOrientation(orientation: Int): RasterImage = when (orientation) {
        2 -> flippedHorizontally()
        3 -> rotated180()
        4 -> rotated180().flippedHorizontally()
        5 -> rotatedBy90Clockwise().flippedHorizontally()
        6 -> rotatedBy90Clockwise()
        7 -> rotatedBy90Clockwise().rotated180().flippedHorizontally()
        8 -> rotatedBy90Clockwise().rotated180()
        else -> this
    }

    /**
     * Alpha-aware resize. Downscale uses an area filter (high quality), upscale uses
     * bilinear interpolation. Never called automatically; only on an explicit user choice.
     */
    fun resizedTo(newWidth: Int, newHeight: Int): RasterImage =
        if (newWidth < width || newHeight < height) {
            areaDownscale(newWidth, newHeight)
        } else {
            bilinear(newWidth, newHeight)
        }

    private fun areaDownscale(dw: Int, dh: Int): RasterImage {
        val out = IntArray(dw * dh)
        val xRatio = width.toDouble() / dw
        val yRatio = height.toDouble() / dh
        for (dy in 0 until dh) {
            val y0d = dy * yRatio
            val y1d = (dy + 1) * yRatio
            val y0 = y0d.toInt()
            val y1 = min(height, ceil(y1d).toInt().coerceAtLeast(y0 + 1))
            for (dx in 0 until dw) {
                val x0d = dx * xRatio
                val x1d = (dx + 1) * xRatio
                val x0 = x0d.toInt()
                val x1 = min(width, ceil(x1d).toInt().coerceAtLeast(x0 + 1))
                var r = 0.0; var g = 0.0; var b = 0.0
                var alphaSum = 0.0; var areaSum = 0.0
                for (yy in y0 until y1) {
                    val yTop = max(y0d, yy.toDouble())
                    val yBot = min(y1d, (yy + 1).toDouble())
                    if (yBot <= yTop) continue
                    val yw = yBot - yTop
                    for (xx in x0 until x1) {
                        val xLeft = max(x0d, xx.toDouble())
                        val xRight = min(x1d, (xx + 1).toDouble())
                        if (xRight <= xLeft) continue
                        val xw = xRight - xLeft
                        val area = xw * yw
                        val p = pixels[yy * width + xx]
                        val pa = (p ushr 24) / 255.0
                        r += (p shr 16 and 0xFF) * pa * area
                        g += (p shr 8 and 0xFF) * pa * area
                        b += (p and 0xFF) * pa * area
                        alphaSum += pa * area
                        areaSum += area
                    }
                }
                out[dy * dw + dx] = if (alphaSum <= 0.0) 0 else {
                    val outR = (r / alphaSum).roundToInt().coerceIn(0, 255)
                    val outG = (g / alphaSum).roundToInt().coerceIn(0, 255)
                    val outB = (b / alphaSum).roundToInt().coerceIn(0, 255)
                    val outA = (alphaSum / areaSum * 255.0).roundToInt().coerceIn(0, 255)
                    (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }
        }
        return RasterImage(dw, dh, out)
    }

    private fun bilinear(dw: Int, dh: Int): RasterImage {
        val out = IntArray(dw * dh)
        val xRatio = width.toDouble() / dw
        val yRatio = height.toDouble() / dh
        for (dy in 0 until dh) {
            val fy = (dy + 0.5) * yRatio - 0.5
            val y0 = fy.toInt().coerceIn(0, height - 1)
            val y1 = min(y0 + 1, height - 1)
            val wy = (fy - y0).coerceIn(0.0, 1.0)
            for (dx in 0 until dw) {
                val fx = (dx + 0.5) * xRatio - 0.5
                val x0 = fx.toInt().coerceIn(0, width - 1)
                val x1 = min(x0 + 1, width - 1)
                val wx = (fx - x0).coerceIn(0.0, 1.0)
                out[dy * dw + dx] = blend(
                    pixel(x0, y0), pixel(x1, y0), pixel(x0, y1), pixel(x1, y1),
                    wx, wy
                )
            }
        }
        return RasterImage(dw, dh, out)
    }

    private fun blend(
        p00: Int, p10: Int, p01: Int, p11: Int, wx: Double, wy: Double
    ): Int {
        fun ch(shift: Int): Int {
            val top = lerp(p00 shr shift and 0xFF, p10 shr shift and 0xFF, wx)
            val bottom = lerp(p01 shr shift and 0xFF, p11 shr shift and 0xFF, wx)
            return lerp(top, bottom, wy).roundToInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun lerp(v0: Int, v1: Int, t: Double): Double = v0 + (v1 - v0) * t

    private fun lerp(v0: Double, v1: Double, t: Double): Double = v0 + (v1 - v0) * t
}
