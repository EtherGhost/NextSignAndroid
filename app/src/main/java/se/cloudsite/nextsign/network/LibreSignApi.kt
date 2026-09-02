package se.cloudsite.nextsign.network

import com.google.gson.JsonElement
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

// Query params baked directly into the path rather than using @Query with default
// values - Retrofit's dynamic proxy doesn't reliably honor Kotlin default parameter
// values, and this exact query is fixed anyway (matches loadFiles() in the Ubuntu
// Touch app's LibreSignApiClient.qml).
//
// Response types are Call<OcsResponse<...>> (normal Gson-constructible data classes),
// never Call<ResponseBody> - the SSO bridge always deserializes its AIDL response via
// Gson regardless of the declared return type, and fails trying to build ResponseBody
// since it's abstract. Confirmed live during the step-1 connectivity test.
interface LibreSignApi {
    @GET("file/list?format=json&status[]=1&status[]=2&status[]=3&details=true")
    fun listFiles(): Call<OcsResponse<FileListData>>

    // Path uses the signer's own sign_request_uuid, NOT the file's own uuid - despite
    // LibreSign's own API docs describing this parameter identically to
    // file/validate/uuid's, sending the file uuid here fails as invalid. Confirmed live
    // in the Ubuntu Touch app.
    @Headers("Content-Type: application/json")
    @POST("sign/uuid/{signUuid}?format=json")
    fun signDocument(@Path("signUuid") signUuid: String, @Body body: SignRequestBody): Call<OcsResponse<JsonElement>>

    // Unlike sign/uuid/{signUuid}, this one really does take the file's own uuid -
    // confirmed live in the Ubuntu Touch app.
    @GET("file/validate/uuid/{uuid}?format=json&showMessages=true")
    fun validateFile(@Path("uuid") uuid: String): Call<OcsResponse<ValidationData>>

    @GET("signature/elements?format=json")
    fun getSignatureElements(): Call<OcsResponse<SignatureElementsData>>

    @Headers("Content-Type: application/json")
    @POST("signature/elements?format=json")
    fun createSignatureElement(@Body body: SignatureElementCreateRequest): Call<OcsResponse<SignatureElementsData>>

    // @PATCH isn't in the set of HTTP method annotations this SSO bridge's Retrofit
    // adapter actually understands (confirmed by reading NextcloudRetrofitServiceMethod's
    // 1.3.4 source - it only recognizes DELETE/GET/POST/PUT/HEAD/@HTTP) - using it
    // directly would throw UnsupportedOperationException at call time. @HTTP with an
    // explicit method name is the supported way to issue a PATCH.
    @Headers("Content-Type: application/json")
    @HTTP(method = "PATCH", path = "signature/elements/{nodeId}?format=json", hasBody = true)
    fun updateSignatureElement(
        @Path("nodeId") nodeId: Int,
        @Body body: SignatureElementUpdateRequest
    ): Call<OcsResponse<SignatureElementsData>>
}
