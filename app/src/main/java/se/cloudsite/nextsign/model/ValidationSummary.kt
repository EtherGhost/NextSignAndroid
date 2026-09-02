package se.cloudsite.nextsign.model

data class ValidationSummary(
    val statusText: String,
    val signers: List<SignerVerdict>
)

data class SignerVerdict(
    val displayName: String,
    val signed: String,
    val signatureLabel: String,
    val certificateLabel: String
)
