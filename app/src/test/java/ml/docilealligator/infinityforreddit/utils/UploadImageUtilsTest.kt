package ml.docilealligator.infinityforreddit.utils

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * What [UploadImageUtils.uploadImage] declares to Reddit about the image it uploads.
 *
 * These tests read the lease form and the S3 multipart off the wire. The lease must name the
 * mimetype and no filepath: with a filepath Reddit records the upload as an asset its listing JSON
 * then reports as failed, so a gallery made from it showed as a text post everywhere but reddit.com
 * (see `UploadImageUtils.imageLeaseParams`). The mimetype and the uploaded part's name must agree
 * with the bytes, and a provider with no type must not leave the mimetype null, which Retrofit
 * rejects before sending anything.
 *
 * Native graphics, because the format is read from the image header with BitmapFactory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UploadImageUtilsTest {

    private val uri: Uri = Uri.parse("content://media/pick/1")
    private val requests = mutableListOf<Request>()

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = if (request.url.encodedPath.endsWith("asset.json")) LEASE_REPLY else S3_REPLY
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        }
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()

    @Before
    fun registerImageExtensions() {
        val mimeTypeMap = shadowOf(MimeTypeMap.getSingleton())
        mimeTypeMap.addExtensionMimeTypeMapping("jpg", "image/jpeg")
        mimeTypeMap.addExtensionMimeTypeMapping("png", "image/png")
    }

    private fun upload(providerMimeType: String?, image: ByteArray, getImageKey: Boolean = true): String {
        val contentResolver = mock<ContentResolver>()
        whenever(contentResolver.getType(uri)).thenReturn(providerMimeType)
        whenever(contentResolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(image))
        return UploadImageUtils.uploadImage(
            retrofit("https://oauth.reddit.com/"),
            retrofit("https://reddit-uploaded-media.s3-accelerate.amazonaws.com/"),
            contentResolver, "token", uri, getImageKey
        )
    }

    private fun leaseField(name: String): String? {
        val form = requests.single { it.url.encodedPath.endsWith("asset.json") }.body as FormBody
        return (0 until form.size).firstOrNull { form.name(it) == name }?.let { form.value(it) }
    }

    private fun leaseFieldNames(): List<String> {
        val form = requests.single { it.url.encodedPath.endsWith("asset.json") }.body as FormBody
        return (0 until form.size).map { form.name(it) }
    }

    private fun uploadedFileDisposition(): String? {
        val multipart = requests.single { !it.url.encodedPath.endsWith("asset.json") }.body as MultipartBody
        return multipart.parts
            .mapNotNull { it.headers?.get("Content-Disposition") }
            .single { it.contains("name=\"file\"") }
    }

    private fun encode(format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(format, 100, it) }.toByteArray()
    }

    @Test
    fun `a PNG is leased and uploaded as a PNG`() {
        upload("image/png", encode(Bitmap.CompressFormat.PNG))

        assertEquals("image/png", leaseField("mimetype"))
        assertEquals("form-data; name=\"file\"; filename=\"post_image.png\"", uploadedFileDisposition())
    }

    @Test
    fun `a JPEG is leased and uploaded as a JPEG`() {
        upload("image/jpeg", encode(Bitmap.CompressFormat.JPEG))

        assertEquals("image/jpeg", leaseField("mimetype"))
        assertEquals("form-data; name=\"file\"; filename=\"post_image.jpg\"", uploadedFileDisposition())
    }

    @Test
    fun `a provider that mislabels the image does not decide its format`() {
        upload("image/jpeg", encode(Bitmap.CompressFormat.PNG))

        assertEquals("image/png", leaseField("mimetype"))
        assertEquals("form-data; name=\"file\"; filename=\"post_image.png\"", uploadedFileDisposition())
    }

    @Test
    fun `an image whose provider reports no type is still leased`() {
        // A null mimetype used to go straight into the lease's field map, which Retrofit rejects.
        upload(null, encode(Bitmap.CompressFormat.PNG))

        assertEquals("image/png", leaseField("mimetype"))
    }

    @Test
    fun `the lease names no filepath`() {
        upload("image/png", encode(Bitmap.CompressFormat.PNG))

        assertEquals("with a filepath the listing reports the upload as failed", null, leaseField("filepath"))
        assertEquals(listOf("mimetype"), leaseFieldNames())
    }

    @Test
    fun `a gallery item or inline image gets the S3 key, an image post the URL`() {
        assertEquals("abc123", upload("image/png", encode(Bitmap.CompressFormat.PNG), getImageKey = true))
        requests.clear()
        assertEquals(
            "https://reddit-uploaded-media.s3-accelerate.amazonaws.com/abc123",
            upload("image/png", encode(Bitmap.CompressFormat.PNG), getImageKey = false)
        )
    }

    @Test
    fun `the upload form is read from a bare lease and from an args-wrapped one`() {
        val bare = UploadImageUtils.parseJSONResponseFromAWS(LEASE_REPLY)
        val wrapped = UploadImageUtils.parseJSONResponseFromAWS(
            """{"args":{"action":"//reddit-uploaded-video.s3-accelerate.amazonaws.com",""" +
                """"fields":[{"name":"key","value":"vid456"}]},"asset":{"asset_id":"vid456"}}"""
        )

        assertEquals(setOf("key"), bare.keys)
        assertEquals(setOf("key"), wrapped.keys)
    }

    @Test
    fun `bytes BitmapFactory cannot read fall back to the provider type, then to JPEG`() {
        val unreadable = byteArrayOf(1, 2, 3, 4)

        assertEquals("image/webp", UploadImageUtils.resolveImageMimeType("image/webp", unreadable))
        assertEquals("image/jpeg", UploadImageUtils.resolveImageMimeType(null, unreadable))
    }

    private companion object {
        /** What `asset.json` answers to a lease with no filepath: the bare upload form. */
        const val LEASE_REPLY =
            """{"action":"//reddit-uploaded-media.s3-accelerate.amazonaws.com",""" +
                """"fields":[{"name":"key","value":"abc123"}]}"""
        const val S3_REPLY =
            "<PostResponse><Location>https://reddit-uploaded-media.s3-accelerate.amazonaws.com/abc123" +
                "</Location><Bucket>reddit-uploaded-media</Bucket><Key>abc123</Key></PostResponse>"
    }
}
