package ml.docilealligator.infinityforreddit.markdown.commentface

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.apis.RedditAPI
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Call
import retrofit2.Response

/**
 * Loading, caching and announcing subreddit stylesheets (issue #432): one network fetch a day at
 * most, the disk copy first, a zero-byte marker for a subreddit with no faces, and a listener call
 * only when there is something new to draw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class CommentFaceRepositoryTest {

    private val directory: File = Files.createTempDirectory("comment_faces").toFile()
    private val api: RedditAPI = mock()
    private val loaded = ArrayList<String>()
    private val listener = CommentFaceRepository.Listener { loaded.add(it) }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private val owner = Owner()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** Runs the load on the calling thread; deliveries still go through the main looper. */
    private fun repository() = CommentFaceRepository(directory, api, { it.run() }, Handler(Looper.getMainLooper()))
        .also { it.observe(owner, listener) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun stylesheetJson(css: String): String = JSONObject().put("kind", "stylesheet").put(
        "data", JSONObject()
            .put("stylesheet", css)
            .put("images", JSONArray().put(JSONObject()
                .put("name", "sprite")
                .put("url", "https://b.thumbs.redditmedia.com/sprite.png")
                .put("link", "url(%%sprite%%)")))
    ).toString()

    private val faceCss = ".md a[href=\"#bonk\"] { display: inline-block; width: 50px; height: 60px; background: url(%%sprite%%); }"

    private fun respond(subreddit: String, response: Response<String>) {
        val call: Call<String> = mock()
        whenever(call.execute()).thenReturn(response)
        whenever(api.getSubredditStylesheet(subreddit)).thenReturn(call)
    }

    @Test
    fun `a stylesheet with faces is fetched, cached, and announced once`() {
        respond("anime", Response.success(stylesheetJson(faceCss)))
        val repository = repository()
        assertNull(repository.peek("anime"))

        repository.request("anime")
        idle()

        assertEquals(listOf("anime"), loaded)
        assertNotNull(repository.peek("anime")?.resolve("#bonk", null, CommentFaceContext.COMMENT))
        assertTrue(File(directory, "anime.json").isFile)

        // Fresh, so a second request is a no-op.
        repository.request("anime")
        idle()
        verify(api, times(1)).getSubredditStylesheet("anime")
        assertEquals(listOf("anime"), loaded)
    }

    @Test
    fun `a new process reads the stylesheet from disk instead of the network`() {
        respond("anime", Response.success(stylesheetJson(faceCss)))
        repository().request("anime")
        idle()
        loaded.clear()

        val api2: RedditAPI = mock()
        val second = CommentFaceRepository(directory, api2, { it.run() }, Handler(Looper.getMainLooper()))
        second.observe(owner, listener)
        second.request("anime")
        idle()

        verify(api2, never()).getSubredditStylesheet(any())
        assertEquals(listOf("anime"), loaded)
        assertNotNull(second.peek("anime")?.resolve("#bonk", null, CommentFaceContext.COMMENT))
    }

    @Test
    fun `a stylesheet without faces is remembered as a marker and not announced`() {
        respond("askreddit", Response.success(stylesheetJson(".side { width: 300px; }")))
        val repository = repository()
        repository.request("askreddit")
        idle()

        assertTrue(loaded.isEmpty())
        assertTrue(checkNotNull(repository.peek("askreddit")).isEmpty)
        assertTrue(File(directory, "askreddit.none").isFile)
        assertFalse(File(directory, "askreddit.json").exists())
    }

    @Test
    fun `a subreddit with no stylesheet gets the marker too`() {
        respond("nocss", Response.error(404, "{}".toResponseBody(null)))
        val repository = repository()
        repository.request("nocss")
        idle()

        assertTrue(checkNotNull(repository.peek("nocss")).isEmpty)
        assertTrue(File(directory, "nocss.none").isFile)
    }

    @Test
    fun `a failed fetch caches nothing and is not retried at once`() {
        val call: Call<String> = mock()
        whenever(call.execute()).thenThrow(IOException("offline"))
        whenever(api.getSubredditStylesheet("anime")).thenReturn(call)
        val repository = repository()

        repository.request("anime")
        idle()
        repository.request("anime")
        idle()

        assertNull(repository.peek("anime"))
        assertTrue(loaded.isEmpty())
        verify(api, times(1)).getSubredditStylesheet("anime")
        assertFalse(File(directory, "anime.json").exists())
    }

    @Test
    fun `a destroyed owner hears nothing more`() {
        respond("anime", Response.success(stylesheetJson(faceCss)))
        val repository = repository()
        owner.registry.currentState = Lifecycle.State.DESTROYED

        repository.request("anime")
        idle()

        assertTrue(loaded.isEmpty())
    }
}
