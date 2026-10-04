package se.cloudsite.nextsign.network

// Raw wire shape for GET identify-account/search?search=... - verified live
// against the real test server (ocs.data is directly a JSON array, no extra
// wrapper object). Field names already match Kotlin camelCase 1:1.
data class RawIdentifyAccount(
    val identify: String?,
    val isNoUser: Boolean?,
    val displayName: String?,
    val subname: String?,
    val method: String?
)
