package ml.docilealligator.infinityforreddit.utils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

public class UploadImageUtils {
    public static String uploadVideoPosterImage(Retrofit oauthRetrofit, Retrofit uploadMediaRetrofit,
                                                @Nullable String accessToken, Bitmap image) throws IOException, JSONException, XmlPullParserException {
        RedditAPI api = oauthRetrofit.create(RedditAPI.class);

        Call<String> uploadImageCall = api.uploadImage(APIUtils.getOAuthHeader(accessToken), imageLeaseParams("image/jpeg"));
        Response<String> uploadImageResponse = uploadImageCall.execute();
        if (uploadImageResponse.isSuccessful()) {
            Map<String, RequestBody> nameValuePairsMap = parseJSONResponseFromAWS(uploadImageResponse.body());

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] byteArray = stream.toByteArray();

            RequestBody fileBody = RequestBody.create(byteArray, MediaType.parse("application/octet-stream"));
            MultipartBody.Part fileToUpload = MultipartBody.Part.createFormData("file", "post_image.jpg", fileBody);

            RedditAPI uploadMediaToAWSApi = uploadMediaRetrofit.create(RedditAPI.class);
            Call<String> uploadMediaToAWS = uploadMediaToAWSApi.uploadMediaToAWS(nameValuePairsMap, fileToUpload);
            Response<String> uploadMediaToAWSResponse = uploadMediaToAWS.execute();
            if (!uploadMediaToAWSResponse.isSuccessful()) {
                throw uploadFailed(uploadMediaToAWSResponse);
            }
            return requireParsed(parseImageFromXMLResponseFromAWS(uploadMediaToAWSResponse.body(), false));
        } else {
            throw uploadFailed(uploadImageResponse);
        }
    }

    public static String uploadImage(Retrofit oauthRetrofit, Retrofit uploadMediaRetrofit, ContentResolver contentResolver,
                                     @Nullable String accessToken, Uri imageUri) throws IOException, JSONException, XmlPullParserException {
        return uploadImage(oauthRetrofit, uploadMediaRetrofit, contentResolver, accessToken, imageUri, false);
    }

    /**
     * Uploads an image to Reddit's media bucket.
     *
     * @param getImageKey return the uploaded object's S3 key rather than its URL. The key is the
     *                    media id a gallery item and an inline richtext image name; the URL is what
     *                    an image post submits.
     */
    public static String uploadImage(Retrofit oauthRetrofit, Retrofit uploadMediaRetrofit, ContentResolver contentResolver,
                                     @Nullable String accessToken, Uri imageUri, boolean getImageKey) throws IOException, JSONException, XmlPullParserException {
        byte[] buf;
        try (InputStream inputStream = contentResolver.openInputStream(imageUri)) {
            if (inputStream == null) {
                // Every other failure here throws; returning a sentinel would be handed on as
                // the uploaded image's address and submitted as the post's URL.
                throw new MediaUploadException("Cannot open the image to upload");
            }
            buf = IOUtils.toByteArray(inputStream);
        }

        // The lease's mimetype, the uploaded part's name and the bytes all describe the same format.
        String mimeType = resolveImageMimeType(contentResolver.getType(imageUri), buf);
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        String fileName = "post_image." + (extension == null ? "jpg" : extension);

        RedditAPI api = oauthRetrofit.create(RedditAPI.class);

        Call<String> uploadImageCall = api.uploadImage(APIUtils.getOAuthHeader(accessToken), imageLeaseParams(mimeType));
        Response<String> uploadImageResponse = uploadImageCall.execute();
        if (!uploadImageResponse.isSuccessful()) {
            throw uploadFailed(uploadImageResponse);
        }
        Map<String, RequestBody> nameValuePairsMap = parseJSONResponseFromAWS(uploadImageResponse.body());
        RequestBody fileBody = RequestBody.create(buf, MediaType.parse("application/octet-stream"));
        MultipartBody.Part fileToUpload = MultipartBody.Part.createFormData("file", fileName, fileBody);

        RedditAPI uploadMediaToAWSApi = uploadMediaRetrofit.create(RedditAPI.class);
        Call<String> uploadMediaToAWS = uploadMediaToAWSApi.uploadMediaToAWS(nameValuePairsMap, fileToUpload);
        Response<String> uploadMediaToAWSResponse = uploadMediaToAWS.execute();
        if (!uploadMediaToAWSResponse.isSuccessful()) {
            throw uploadFailed(uploadMediaToAWSResponse);
        }
        return requireParsed(parseImageFromXMLResponseFromAWS(uploadMediaToAWSResponse.body(), getImageKey));
    }

    /**
     * The form for {@code /api/media/asset.json}: the mimetype, and deliberately no filepath.
     *
     * <p>With a filepath, Reddit answers with an {@code args}/{@code asset} lease and records the
     * upload as an asset that its listing JSON then reports as {@code "status": "failed"} once it
     * is in a gallery or a body -- the gallery shows as a text post and the body says "Processing
     * img" on old.reddit and in every API client, though reddit.com shows the images. Without one,
     * Reddit answers with a bare {@code action}/{@code fields} upload form whose S3 key is a media
     * id that processes normally: galleries, inline images and image posts all come out valid.
     */
    private static Map<String, String> imageLeaseParams(String mimeType) {
        Map<String, String> params = new HashMap<>();
        params.put(APIUtils.MIMETYPE_KEY, mimeType);
        return params;
    }

    /**
     * The image's real format, read from its header, so a provider that mislabels the file (or, like
     * a cropping app's cache provider, reports no type at all) cannot lease it under the wrong one.
     * Falls back to the provider's type for anything BitmapFactory cannot identify, and to JPEG when
     * there is neither -- a null mimetype would make Retrofit reject the lease request outright.
     */
    static String resolveImageMimeType(@Nullable String providerMimeType, byte[] image) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, options);
        if (options.outMimeType != null) {
            return options.outMimeType;
        }
        return providerMimeType == null ? "image/jpeg" : providerMimeType;
    }

    /**
     * The upload succeeded but its XML reply held no Location (or Key) element, so there is no URL to
     * post. Nothing downstream can do anything with that but fail.
     */
    private static String requireParsed(@Nullable String parsed) throws MediaUploadException {
        if (parsed == null) {
            throw new MediaUploadException("Could not read the uploaded media's address from Reddit's reply");
        }
        return parsed;
    }

    /** Turns a failed upload response into the one failure signal, keeping the status for the user. */
    private static MediaUploadException uploadFailed(Response<String> response) {
        return MediaUploadException.refused(response.code(), response.message());
    }

    @Nullable
    public static String parseImageFromXMLResponseFromAWS(@Nullable String response) throws XmlPullParserException, IOException {
        //Get Image URL
        return parseImageFromXMLResponseFromAWS(response, false);
    }

    @Nullable
    public static String parseImageFromXMLResponseFromAWS(@Nullable String response, boolean getImageKey) throws XmlPullParserException, IOException {
        if (response == null) {
            return null;
        }
        XmlPullParser xmlPullParser = XmlPullParserFactory.newInstance().newPullParser();
        xmlPullParser.setInput(new StringReader(response));

        boolean isKeyTag = false;
        int eventType = xmlPullParser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if ((xmlPullParser.getName().equals("Key") && getImageKey) || (xmlPullParser.getName().equals("Location") && !getImageKey)) {
                    isKeyTag = true;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                if (isKeyTag) {
                    return xmlPullParser.getText();
                }
            }
            eventType = xmlPullParser.next();
        }

        return null;
    }

    public static Map<String, RequestBody> parseJSONResponseFromAWS(@Nullable String response) throws JSONException {
        if (response == null) {
            // This parses Reddit's /api/media/asset.json reply (the presigned S3 upload form),
            // not the S3 upload itself despite the method name.
            throw new JSONException("Empty response from Reddit's media asset request");
        }
        JSONObject responseObject = new JSONObject(response);
        // The upload form arrives wrapped in `args` (a lease that names a filepath, as a video
        // upload's does) or bare (an image lease; see imageLeaseParams).
        JSONObject uploadForm = responseObject.has(JSONUtils.ARGS_KEY)
                ? responseObject.getJSONObject(JSONUtils.ARGS_KEY) : responseObject;
        JSONArray nameValuePairs = uploadForm.getJSONArray(JSONUtils.FIELDS_KEY);

        Map<String, RequestBody> nameValuePairsMap = new HashMap<>();
        for (int i = 0; i < nameValuePairs.length(); i++) {
            nameValuePairsMap.put(nameValuePairs.getJSONObject(i).getString(JSONUtils.NAME_KEY),
                    APIUtils.getRequestBody(nameValuePairs.getJSONObject(i).getString(JSONUtils.VALUE_KEY)));
        }

        return nameValuePairsMap;
    }
}
