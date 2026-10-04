package se.cloudsite.nextsign.network

// POST request-signature - creates the sign request and uploads the file in one
// call. "name" is required at the top level, not just inside "file" - confirmed
// live in the earlier spike test (fails with "File name is required" otherwise).
data class RequestSignatureBody(
    val file: RequestSignatureFile,
    val name: String,
    val signers: List<RequestSignatureSigner>
)

data class RequestSignatureFile(
    val base64: String,
    val name: String
)

data class RequestSignatureSigner(
    val identifyMethods: List<RequestSignatureIdentifyMethod>,
    val displayName: String
)

data class RequestSignatureIdentifyMethod(
    val method: String,
    val value: String,
    val requirement: String = "required"
)
