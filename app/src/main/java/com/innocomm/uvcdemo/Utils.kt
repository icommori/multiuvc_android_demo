package com.innocomm.uvcdemo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

fun yuvToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap? {
    try {
        buffer.rewind()
        val remaining = buffer.remaining()
        val expectedSize = width * height * 2

        if (remaining < expectedSize) {
            return null
        }

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        var i = 0
        var p = 0
        while (i < expectedSize) {
            val y1 = buffer.get().toInt() and 0xFF
            val u = buffer.get().toInt() and 0xFF
            val y2 = buffer.get().toInt() and 0xFF
            val v = buffer.get().toInt() and 0xFF

            pixels[p++] = yuvToRgb(y1, u, v)
            pixels[p++] = yuvToRgb(y2, u, v)
            i += 4
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    } catch (e: Exception) {
        Log.e("UvcHelper", "Conversion error: ${e.message}")
        return null
    }
}

private fun yuvToRgb(y: Int, u: Int, v: Int): Int {
    val r = (y + 1.370705 * (v - 128)).toInt().coerceIn(0, 255)
    val g = (y - 0.337633 * (u - 128) - 0.698001 * (v - 128)).toInt().coerceIn(0, 255)
    val b = (y + 1.732446 * (u - 128)).toInt().coerceIn(0, 255)
    return -0x1000000 or (r shl 16) or (g shl 8) or b
}