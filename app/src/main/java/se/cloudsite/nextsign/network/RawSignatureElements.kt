package se.cloudsite.nextsign.network

// Raw wire shapes for GET/POST/PATCH signature/elements - matches what the Ubuntu
// Touch app's LibreSignApiCore.js parseSignatureElements() already reverse-engineered
// against a real server. Each element's file.nodeId is the "profileNodeId" a sign
// request needs alongside a documentElementId to actually render a visible mark,
// rather than just recording the signature cryptographically.
data class SignatureElementsData(
    val elements: List<RawSignatureElement>?
)

data class RawSignatureElement(
    val type: String?,
    val file: RawSignatureElementFile?,
    val starred: Boolean?
)

data class RawSignatureElementFile(
    val nodeId: Int?
)
