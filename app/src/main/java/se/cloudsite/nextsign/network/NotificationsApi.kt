package se.cloudsite.nextsign.network

import com.google.gson.JsonElement
import retrofit2.Call
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

data class VapidData(val vapid: String?)

// Nextcloud's core Notifications app WebPush API - not LibreSign-specific, shared by
// any Nextcloud client. Confirmed against the app's real PHP source
// (WebPushController.php) and Nextcloud Talk's real Android client, not guessed: the
// registration flow is a two-step handshake (register, then activate using a token
// the server pushes back through the new endpoint itself), not documented in casual
// API references. See push/PushServiceImpl.kt for how these are used.
interface NotificationsApi {
    @GET("webpush/vapid?format=json")
    fun getVapidKey(): Call<OcsResponse<VapidData>>

    // appTypes: comma-separated Nextcloud app ids to filter notifications by, or
    // "all" for everything - "all" is used for this proof of concept so any
    // notification confirms the pipeline works, not just LibreSign's.
    @FormUrlEncoded
    @POST("webpush?format=json")
    fun registerWebPush(
        @Field("endpoint") endpoint: String,
        @Field("uaPublicKey") uaPublicKey: String,
        @Field("auth") auth: String,
        @Field("appTypes") appTypes: String
    ): Call<OcsResponse<JsonElement>>

    @FormUrlEncoded
    @POST("webpush/activate?format=json")
    fun activateWebPush(@Field("activationToken") activationToken: String): Call<OcsResponse<JsonElement>>

    // Called both when the user turns push off in Settings, and (just for the
    // previously-active account) when switching accounts - otherwise a stale
    // subscription for an account you've switched away from keeps receiving pushes on
    // the same device, since registerWebPush() has no way to know a different account
    // was previously using the same UnifiedPush endpoint.
    @DELETE("webpush?format=json")
    fun unregisterWebPush(): Call<OcsResponse<JsonElement>>
}
