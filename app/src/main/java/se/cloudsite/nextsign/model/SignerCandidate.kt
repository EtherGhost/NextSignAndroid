package se.cloudsite.nextsign.model

data class SignerCandidate(
    val identify: String,
    val displayName: String,
    val subname: String,
    val isNoUser: Boolean,
    val method: String
)
