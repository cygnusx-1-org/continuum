package ml.docilealligator.infinityforreddit.apis

import retrofit2.Call
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface OhMyDlAPI {
    @FormUrlEncoded
    @POST("/api/download")
    fun getRedgifsData(
        @FieldMap params: Map<String, String>
    ): Call<String>
}