package se.cloudsite.nextsign.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

// Ports the Ubuntu Touch app's native SignatureImageEncoder (a C++ class there): a
// signature is stamped small onto a PDF page, so a multi-megapixel camera photo is
// unnecessary and risks exceeding LibreSign's upload limit - downscale before encoding
// rather than sending the source image as-is, and always re-encode to PNG since
// LibreSign stores visible signature/initials elements as .png server-side regardless
// of upload format.
object SignatureImageEncoder {
    private const val MAX_DIMENSION_PX = 1200
    private const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024

    // Returns a "data:image/png;base64,..." URI, or null if the image couldn't be
    // read, decoded, or encoded within LibreSign's upload size limit.
    fun toBase64DataUri(context: Context, uri: Uri): String? {
        val bitmap = readBitmap(context, uri) ?: return null
        val scaled = downscaleIfNeeded(bitmap)

        val output = ByteArrayOutputStream()
        val encoded = scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        bitmap.recycle()
        if (!encoded) {
            return null
        }

        val bytes = output.toByteArray()
        if (bytes.size > MAX_UPLOAD_BYTES) {
            return null
        }

        return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun bitmapToBase64DataUri(bitmap: Bitmap): String? {
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            return null
        }
        val bytes = output.toByteArray()
        if (bytes.size > MAX_UPLOAD_BYTES) {
            return null
        }
        return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // For showing an immediate local preview of a just-picked/just-drawn image without
    // waiting on a round-trip to the server.
    fun decodeDataUri(dataUri: String): Bitmap? {
        val base64 = dataUri.substringAfter(",", "")
        if (base64.isEmpty()) {
            return null
        }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun readBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_DIMENSION_PX && bitmap.height <= MAX_DIMENSION_PX) {
            return bitmap
        }
        val scale = MAX_DIMENSION_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
