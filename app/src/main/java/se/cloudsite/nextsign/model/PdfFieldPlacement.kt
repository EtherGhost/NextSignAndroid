package se.cloudsite.nextsign.model

// Coordinates in PDF points, top-down (matches the top/left/width/height shape
// LibreSign's file-element endpoint accepts directly - confirmed live in the
// earlier spike test, no bottom-up llx/lly conversion needed on our side).
data class PdfFieldPlacement(
    val left: Float,
    val top: Float,
    val width: Float = 150f,
    val height: Float = 50f
)
