package se.cloudsite.nextsign.network

// The OCS envelope every LibreSign API response is wrapped in - mirrors what
// LibreSignApiCore.js's parseFileList/parseValidation/parseSignatureElements unwrap
// in the Ubuntu Touch app. Not handled by the SSO library itself, so this app owns it.
data class OcsResponse<T>(
    val ocs: OcsWrapper<T>
)

data class OcsWrapper<T>(
    val meta: OcsMeta,
    val data: T
)

data class OcsMeta(
    val status: String,
    val statuscode: Int,
    val message: String
)
