package se.cloudsite.nextsign.network

// POST file-element/{fileUuid} - {fileUuid} is the FILE's own uuid (from
// request-signature's response), not a per-signer secret - confirmed live in the
// earlier spike test. "elementId" is deliberately omitted (not just left null) -
// including it at all fails with a server-side SQL error on create, per the same spike.
data class CreateFileElementBody(
    val signRequestId: Int,
    val fileId: Int,
    val type: String = "signature",
    val coordinates: FileElementCoordinates
)

// top/left/width/height (screen-like, top-down) rather than llx/lly/urx/ury - both
// are accepted by the server, and this one needs no bottom-up conversion on our side
// since it matches our own tap-to-place math directly (see PdfFieldPlacement). Must
// be Int, not Float - confirmed live: the server rejects e.g. "150.0" with "Koordinat
// height måste vara ett heltal" even though it's a whole number, because Gson still
// serializes a Kotlin Float as a JSON number with a decimal point.
data class FileElementCoordinates(
    val page: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

data class RawFileElementResult(
    val fileElementId: Int?
)
