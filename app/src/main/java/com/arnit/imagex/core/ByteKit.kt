package com.arnit.imagex.core

import java.io.ByteArrayOutputStream

/** Small unsigned byte/int helpers shared by the hand-written codecs. */
object ByteKit {

    fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    fun le32(b: ByteArray, o: Int): Int = le16(b, o) or (le16(b, o + 2) shl 16)
    fun be16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    fun be32(b: ByteArray, o: Int): Int = be16(b, o) shl 16 or be16(b, o + 2)

    fun putLe16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    fun putLe32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }

    /** Reads bytes but never beyond [max]; returns the head of the file. */
    fun head(b: ByteArray, max: Int = 512): ByteArray =
        if (b.size <= max) b else b.copyOf(max)

    fun startsWith(b: ByteArray, offset: Int, bytes: ByteArray): Boolean {
        if (b.size < offset + bytes.size) return false
        for (i in bytes.indices) if (b[offset + i] != bytes[i]) return false
        return true
    }

    /** Standard decode of TIFF/PSD PackBits RLE. */
    fun unpackBits(src: ByteArray, from: Int, until: Int, expected: Int): ByteArray {
        val out = ByteArrayOutputStream(expected.coerceAtLeast(0))
        var i = from
        while (i < until && out.size() < expected) {
            val n = src[i].toInt()
            i++
            when {
                n >= 0 -> { // literal run of n+1
                    val count = n + 1
                    out.write(src, i, minOf(count, until - i))
                    i += count
                }
                n != -128 -> { // repeat run of 1 - n
                    val count = 1 - n
                    val value = src[i]
                    i++
                    repeat(count) { out.write(value.toInt()) }
                }
            }
        }
        return out.toByteArray()
    }

    /** Inflate zlib/deflate data thrown by TIFF/PSD. */
    fun inflate(src: ByteArray, from: Int, count: Int, expected: Int): ByteArray {
        val out = ByteArrayOutputStream(expected.coerceAtLeast(1024))
        val inflater = java.util.zip.Inflater()
        inflater.setInput(src, from, count)
        val buffer = ByteArray(65536)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buffer, 0, n)
                }
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    /** RGB565 -> 8-bit with bit replication (keeps full-range gradients). */
    fun expand5(v: Int): Int = (v shl 3) or (v shr 2)

    fun rgb565(value: Int): Int {
        val r = expand5((value shr 11) and 0x1F)
        val g = expand5((value shr 5) and 0x3F)
        val b = expand5(value and 0x1F)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }
}
