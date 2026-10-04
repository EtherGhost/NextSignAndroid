package se.cloudsite.nextsign.model

// Coordinates in PDF points, top-down (matches the top/left/width/height shape
// LibreSign's file-element endpoint accepts directly - confirmed live in the
// earlier spike test, no bottom-up llx/lly conversion needed on our side).
// No default width/height - every call site passes them explicitly (see
// MainActivity.FIELD_BASE_WIDTH/HEIGHT), so a default here would just be dead
// code that could silently drift out of sync with the real base size.
data class PdfFieldPlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)
