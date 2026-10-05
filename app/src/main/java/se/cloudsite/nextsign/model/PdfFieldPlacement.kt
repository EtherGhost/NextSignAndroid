package se.cloudsite.nextsign.model

// Coordinates in PDF points, top-down (matches the top/left/width/height shape
// LibreSign's file-element endpoint accepts directly - confirmed live in the
// earlier spike test, no bottom-up llx/lly conversion needed on our side).
// No default width/height - every call site passes them explicitly (see
// MainActivity.FIELD_BASE_WIDTH/HEIGHT), so a default here would just be dead
// code that could silently drift out of sync with the real base size.
// page is 0-indexed (matches PdfRenderer/PdfPreviewRenderer page indices) -
// LibreSignRepository converts to LibreSign's own 1-indexed "page" at the API
// boundary, the one place that distinction actually matters.
//
// A list of these now, not one per signer identify - a signer can need more
// than one field (e.g. initials on several pages plus a signature on the last
// one), confirmed as the actual real-world requirement after the earlier
// one-field-per-signer design kept producing "the box just moves instead of
// adding a new one" reports that turned out to be the model itself being
// wrong, not a bug in it. id is this app's own locally-generated identifier
// (not anything LibreSign knows about) - just enough to address one specific
// field among possibly several for the same signer, for move/resize/remove.
data class PdfFieldPlacement(
    val id: String,
    val identify: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val page: Int
)
