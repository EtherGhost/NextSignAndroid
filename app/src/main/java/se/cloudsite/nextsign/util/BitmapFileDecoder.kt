package se.cloudsite.nextsign.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

// Downloaded avatar/signature preview files are only ever shown small (avatar:
// a 64px file requested from Nextcloud's own endpoint; signature preview: a
// ~180dp box) - decoding the source file at full resolution first, then only
// ever displaying it small, wastes memory for no benefit. inSampleSize
// downsamples during decode itself instead of after.
object BitmapFileDecoder {
    fun decodeSampled(path: String, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= maxDimension && height / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
