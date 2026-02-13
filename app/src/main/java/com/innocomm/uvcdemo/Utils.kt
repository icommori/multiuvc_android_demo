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
        val remaining = buffer.remaining()
        val expectedSize = width * height * 2

        if (remaining < expectedSize) {
            return null
        }

        val bytes = ByteArray(remaining)
        buffer.get(bytes)

        val yuvImage = YuvImage(bytes, ImageFormat.YUY2, width, height, null)
        val out = ByteArrayOutputStream()
        if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)) {
            return null
        }
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
        Log.e("UvcHelper", "Conversion error: ${e.message}")
        return null
    }
}