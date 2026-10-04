package se.cloudsite.nextsign.network

// Raw wire shape for the request-signature response - verified live against the
// real test server with two signers. The returned signers[] order does NOT match
// submission order, so each one must be matched back to our own signer by its own
// identifyMethods[0].value, not by array position (see LibreSignRepository).
data class RawRequestSignatureResponse(
    val id: Int?,
    val uuid: String?,
    val signers: List<RawRequestSignatureSigner>?
)

data class RawRequestSignatureSigner(
    val signRequestId: Int?,
    val identifyMethods: List<RawIdentifyMethodEcho>?
)

data class RawIdentifyMethodEcho(
    val method: String?,
    val value: String?
)
