package se.cloudsite.nextsign.network

data class SignatureElementFileBody(
    val base64: String
)

// POST signature/elements - creates a new element. Only used when the account has no
// element of this type registered yet; otherwise SignatureElementUpdateRequest (PATCH)
// replaces the existing one in place, matching the Ubuntu Touch app's own fix for
// picking/drawing repeatedly piling up duplicate server-side elements.
data class SignatureElementCreateRequest(
    val elements: List<SignatureElementEntry>
)

data class SignatureElementEntry(
    val type: String,
    val file: SignatureElementFileBody
)

// PATCH signature/elements/{nodeId} - "type" must be included even though the
// endpoint's own docs list it as optional: the server validates the element's type
// before it resolves elementId from the URL's nodeId, so omitting it fails with
// "Element needs a type" regardless. Confirmed live in the Ubuntu Touch app.
data class SignatureElementUpdateRequest(
    val type: String,
    val file: SignatureElementFileBody
)
