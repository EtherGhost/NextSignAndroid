package se.cloudsite.nextsign.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri

object PdfPreviewRenderer {
    data class RenderedPage(val bitmap: Bitmap, val pageIndex: Int, val pageCount: Int)

    // Returns null for any failure (corrupt file, password-protected PDF, empty
    // document, out-of-range pageIndex, I/O error) rather than letting
    // PdfRenderer's exceptions (IOException, SecurityException) propagate and
    // crash the app - the caller treats "finished loading, result still null"
    // as the signal to show an error. Reopens the document on every call rather
    // than keeping a renderer around across page navigation - documents prepared
    // for signing are typically a handful of pages, not large scanned books, so
    // the simplicity is worth the repeated open for this app's actual usage.
    fun renderPage(context: Context, uri: Uri, pageIndex: Int): RenderedPage? {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pageCount = renderer.pageCount
                    if (pageIndex !in 0 until pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        // PdfRenderer only paints non-white content - start from a white
                        // page or transparent areas of the PDF show through as black.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        RenderedPage(bitmap, pageIndex, pageCount)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
