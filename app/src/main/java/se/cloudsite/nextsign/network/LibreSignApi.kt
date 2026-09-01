package se.cloudsite.nextsign.network

import com.google.gson.JsonElement
import retrofit2.Call
import retrofit2.http.GET

// Query params baked directly into the path rather than using @Query with default
// values - Retrofit's dynamic proxy doesn't reliably honor Kotlin default parameter
// values, and this exact query is fixed anyway (matches loadFiles() in the Ubuntu
// Touch app's LibreSignApiClient.qml).
//
// Returns a raw JsonElement for now (step 1: prove an authenticated call works end to
// end) rather than a typed model - the typed file-list model comes with the document
// list screen in the next porting step. NOT Call<ResponseBody>: the SSO bridge always
// deserializes the AIDL response via Gson regardless of the declared return type - it
// doesn't support ResponseBody as a "give me the raw bytes" escape hatch the way plain
// Retrofit does, and fails trying to Gson-construct that abstract class. JsonElement is
// the raw-passthrough type Gson actually knows how to build.
interface LibreSignApi {
    @GET("file/list?format=json&status[]=1&status[]=2&status[]=3&details=true")
    fun listFiles(): Call<JsonElement>
}
