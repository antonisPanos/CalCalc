package com.example.calcalc.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Photos go to Gemini as base64 inside the JSON request, so a full-resolution capture would
 * mean multi-megabyte uploads for no accuracy gain. 1024px on the long edge is plenty for
 * identifying food.
 */
object ImageUtils {

    private const val MAX_EDGE = 1024
    private const val JPEG_QUALITY = 80

    suspend fun fromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null
        fromJpegBytes(bytes)
    }

    suspend fun fromJpegBytes(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val oriented = applyExifRotation(bytes, decoded)
        val scaled = downscale(oriented)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_EDGE) return bitmap
        val scale = MAX_EDGE.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    }

    /** Gallery photos routinely carry rotation only in EXIF; without this, food arrives sideways. */
    private fun applyExifRotation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            val exif = ExifInterface(bytes.inputStream())
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Decodes a stored base64 JPEG back to a bitmap for the chat thumbnail. */
    fun decodeBase64(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
