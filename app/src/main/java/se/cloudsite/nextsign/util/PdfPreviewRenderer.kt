package se.cloudsite.nextsign.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri

object PdfPreviewRenderer {
    fun renderFirstPage(context: Context, uri: Uri): Bitmap? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    // PdfRenderer only paints non-white content - start from a white
                    // page or transparent areas of the PDF show through as black.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
}
