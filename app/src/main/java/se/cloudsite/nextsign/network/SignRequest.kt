package se.cloudsite.nextsign.network

// clickToSign only, by design - no password/code fallback, matching the Ubuntu Touch
// app. "elements" stays empty until the signature-setup feature (a later porting step)
// exists to supply a profileNodeId - sending a documentElementId with no matching
// profileNodeId makes the server reject the whole sign request outright, so it's safer
// to omit elements entirely for now (the signature just won't render visibly yet,
// same intermediate state the Ubuntu Touch app went through before it had this
// feature either).
data class SignRequestBody(
    val method: String = "clickToSign",
    val elements: List<SignElementBody> = emptyList()
)

data class SignElementBody(
    val documentElementId: Int,
    val profileNodeId: Int
)
