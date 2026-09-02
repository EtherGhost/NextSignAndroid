package se.cloudsite.nextsign.network

// Raw wire shape for GET file/validate/uuid/{uuid} - matches what the Ubuntu Touch
// app's LibreSignApiCore.js parseValidation() already reverse-engineered against a
// real server. Unlike sign/uuid/{signUuid}, this endpoint takes the file's own uuid,
// not the signer's sign_request_uuid - confirmed live in the Ubuntu Touch app, despite
// LibreSign's own API docs describing both endpoints' uuid parameter identically.
data class ValidationData(
    val statusText: String?,
    val signers: List<RawValidationSigner>?
)

data class RawValidationSigner(
    val displayName: String?,
    val signed: String?,
    val signature_validation: RawVerdict?,
    val certificate_validation: RawVerdict?
)

data class RawVerdict(
    val label: String?
)
