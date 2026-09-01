package se.cloudsite.nextsign.network

import retrofit2.Call
import retrofit2.http.GET

// Query params baked directly into the path rather than using @Query with default
// values - Retrofit's dynamic proxy doesn't reliably honor Kotlin default parameter
// values, and this exact query is fixed anyway (matches loadFiles() in the Ubuntu
// Touch app's LibreSignApiClient.qml).
//
// Call<OcsResponse<FileListData>> (a normal Gson-constructible data class), not
// Call<ResponseBody> - the SSO bridge always deserializes its AIDL response via Gson
// regardless of the declared return type, and fails trying to build ResponseBody since
// it's abstract. Confirmed live during the step-1 connectivity test.
interface LibreSignApi {
    @GET("file/list?format=json&status[]=1&status[]=2&status[]=3&details=true")
    fun listFiles(): Call<OcsResponse<FileListData>>
}
