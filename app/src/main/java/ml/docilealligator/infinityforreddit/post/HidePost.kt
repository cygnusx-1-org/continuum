package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.apis.RedditAPIKt
import ml.docilealligator.infinityforreddit.utils.APIUtils
import retrofit2.Retrofit

suspend fun hidePost(
    oauthRetrofit: Retrofit,
    accessToken: String,
    fullname: String
): Boolean {
    val params: MutableMap<String, String> = mutableMapOf<String, String>().apply {
        put(APIUtils.ID_KEY, fullname)
    }
    try {
        val response = oauthRetrofit.create(RedditAPIKt::class.java)
            .hide(APIUtils.getOAuthHeader(accessToken), params)

        return response.isSuccessful
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}

suspend fun unhidePost(
    oauthRetrofit: Retrofit,
    accessToken: String,
    fullname: String
): Boolean {
    val params: MutableMap<String, String> = mutableMapOf<String, String>().apply {
        put(APIUtils.ID_KEY, fullname)
    }
    try {
        val response = oauthRetrofit.create(RedditAPIKt::class.java)
            .unhide(APIUtils.getOAuthHeader(accessToken), params)

        return response.isSuccessful
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}