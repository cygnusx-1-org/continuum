package ml.docilealligator.infinityforreddit.markdown.commentface

import android.os.Handler
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.apis.RedditAPI
import org.json.JSONException
import org.json.JSONObject

/**
 * Subreddit stylesheets, parsed for comment faces, kept in memory and on disk.
 *
 * A stylesheet is fetched once a day at most: [peek] answers from memory without blocking, and
 * [request] fills memory from disk and then, when the copy there is a day old or missing, from
 * `/r/{subreddit}/about/stylesheet.json`. A stale copy keeps being served while the new one loads.
 * Listeners hear about a subreddit only when what it can draw has changed, so screens rebind the
 * rows with faces in them once and not on every refresh.
 *
 * A subreddit whose stylesheet has no faces is remembered as a zero-byte marker rather than its
 * stylesheet, which is most of them.
 *
 * Listeners are held weakly, so a screen that is gone stops hearing about stylesheets as soon as
 * nothing else holds it, even inside an activity that outlives it (the post pager's pages). The
 * caller keeps its listener alive; [CommentFaces] does that for the screens that use it.
 *
 * Main-thread confined, apart from the loading itself.
 */
class CommentFaceRepository(
    private val directory: File,
    private val api: RedditAPI,
    private val executor: Executor,
    private val mainHandler: Handler,
) {
    fun interface Listener {
        fun onCommentFacesLoaded(subreddit: String)
    }

    private class Entry {
        var stylesheet: CommentFaceStylesheet? = null
        var contentHash = 0
        var fetchedAt = 0L
        var failedAt = 0L
        var loading = false
    }

    private class Loaded(val stylesheet: CommentFaceStylesheet, val contentHash: Int, val fetchedAt: Long)

    private val entries = HashMap<String, Entry>()
    private val listeners: MutableSet<Listener> = Collections.newSetFromMap(WeakHashMap())

    /** The stylesheet for [subreddit] if it has been loaded, without loading it. */
    fun peek(subreddit: String): CommentFaceStylesheet? = entries[subreddit]?.stylesheet

    /** Loads [subreddit]'s stylesheet unless a fresh one is loaded or on its way. */
    fun request(subreddit: String) {
        val entry = entries.getOrPut(subreddit) { Entry() }
        if (entry.loading) {
            return
        }
        val now = System.currentTimeMillis()
        val current = entry.stylesheet
        if (current != null && now - entry.fetchedAt < FRESH_MS) {
            return
        }
        // A stale copy waits out a failure too, rather than refetching on every request.
        if (entry.failedAt != 0L && now - entry.failedAt < RETRY_MS) {
            return
        }
        entry.loading = true
        val readDisk = current == null
        executor.execute { load(subreddit, readDisk) }
    }

    /** Adds [listener], held weakly, until it is collected or [owner] is destroyed. */
    fun observe(owner: LifecycleOwner, listener: Listener) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        listeners.add(listener)
        // Weak here too: the owner's lifecycle would otherwise hold the listener, and whatever it
        // captured, for as long as the owner lives.
        val reference = WeakReference(listener)
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                reference.get()?.let { listeners.remove(it) }
            }
        })
    }

    private fun load(subreddit: String, readDisk: Boolean) {
        if (readDisk) {
            val cached = readCache(subreddit)
            if (cached != null) {
                val fresh = System.currentTimeMillis() - cached.fetchedAt < FRESH_MS
                mainHandler.post { deliver(subreddit, cached, finished = fresh) }
                if (fresh) {
                    return
                }
            }
        }
        val fetched = fetch(subreddit)
        mainHandler.post {
            if (fetched != null) {
                deliver(subreddit, fetched, finished = true)
            } else {
                val entry = entries.getOrPut(subreddit) { Entry() }
                entry.loading = false
                entry.failedAt = System.currentTimeMillis()
            }
        }
    }

    private fun deliver(subreddit: String, loaded: Loaded, finished: Boolean) {
        val entry = entries.getOrPut(subreddit) { Entry() }
        val previous = entry.stylesheet
        val changed = if (previous == null) !loaded.stylesheet.isEmpty else entry.contentHash != loaded.contentHash
        entry.stylesheet = loaded.stylesheet
        entry.contentHash = loaded.contentHash
        entry.fetchedAt = loaded.fetchedAt
        entry.failedAt = 0L
        if (finished) {
            entry.loading = false
        }
        if (changed) {
            for (listener in listeners.toList()) {
                listener.onCommentFacesLoaded(subreddit)
            }
        }
    }

    private fun fetch(subreddit: String): Loaded? {
        return try {
            val response = api.getSubredditStylesheet(subreddit).execute()
            val body = response.body()
            val now = System.currentTimeMillis()
            if (response.isSuccessful && body != null) {
                val data = JSONObject(body).getJSONObject("data")
                val css = data.optString("stylesheet")
                val images = HashMap<String, String>()
                val array = data.optJSONArray("images")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val image = array.optJSONObject(i) ?: continue
                        val name = image.optString("name")
                        val url = image.optString("url")
                        if (name.isNotEmpty() && url.isNotEmpty()) {
                            images[name] = url
                        }
                    }
                }
                val stylesheet = CommentFaceStylesheet.parse(css, images)
                if (stylesheet.isEmpty) {
                    writeCache(subreddit, null)
                    Loaded(CommentFaceStylesheet.EMPTY, 0, now)
                } else {
                    writeCache(subreddit, body)
                    Loaded(stylesheet, contentHash(css, images), now)
                }
            } else if (response.code() == 403 || response.code() == 404) {
                // No stylesheet, or one this app cannot see: either way, no faces.
                writeCache(subreddit, null)
                Loaded(CommentFaceStylesheet.EMPTY, 0, now)
            } else {
                null
            }
        } catch (e: IOException) {
            Log.w(TAG, "Fetching r/$subreddit's stylesheet failed", e)
            null
        } catch (e: JSONException) {
            Log.w(TAG, "r/$subreddit's stylesheet response did not parse", e)
            null
        }
    }

    private fun readCache(subreddit: String): Loaded? {
        val faces = File(directory, "$subreddit$FACES_SUFFIX")
        val none = File(directory, "$subreddit$NONE_SUFFIX")
        try {
            if (faces.isFile) {
                val data = JSONObject(faces.readText()).getJSONObject("data")
                val css = data.optString("stylesheet")
                val images = HashMap<String, String>()
                val array = data.optJSONArray("images")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val image = array.optJSONObject(i) ?: continue
                        images[image.optString("name")] = image.optString("url")
                    }
                }
                return Loaded(CommentFaceStylesheet.parse(css, images), contentHash(css, images), faces.lastModified())
            }
            if (none.isFile) {
                return Loaded(CommentFaceStylesheet.EMPTY, 0, none.lastModified())
            }
        } catch (e: IOException) {
            Log.w(TAG, "Reading r/$subreddit's cached stylesheet failed", e)
        } catch (e: JSONException) {
            Log.w(TAG, "r/$subreddit's cached stylesheet did not parse", e)
            faces.delete()
        }
        return null
    }

    /** Stores [response] as [subreddit]'s stylesheet, or a no-faces marker when it is null. */
    private fun writeCache(subreddit: String, response: String?) {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) {
                return
            }
            val faces = File(directory, "$subreddit$FACES_SUFFIX")
            val none = File(directory, "$subreddit$NONE_SUFFIX")
            if (response == null) {
                faces.delete()
                none.writeBytes(ByteArray(0))
            } else {
                val temporary = File(directory, "$subreddit$FACES_SUFFIX.tmp")
                temporary.writeText(response)
                if (!temporary.renameTo(faces)) {
                    temporary.delete()
                    return
                }
                none.delete()
            }
            trimCache()
        } catch (e: IOException) {
            Log.w(TAG, "Caching r/$subreddit's stylesheet failed", e)
        }
    }

    private fun trimCache() {
        val files = directory.listFiles() ?: return
        if (files.size <= MAX_CACHED_SUBREDDITS) {
            return
        }
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHED_SUBREDDITS)
            .forEach { it.delete() }
    }

    private fun contentHash(css: String, images: Map<String, String>): Int =
        31 * css.hashCode() + images.toSortedMap().hashCode()

    companion object {
        private const val TAG = "CommentFaceRepository"
        private const val FACES_SUFFIX = ".json"
        private const val NONE_SUFFIX = ".none"
        private const val FRESH_MS = 24L * 60 * 60 * 1000
        private const val RETRY_MS = 10L * 60 * 1000
        private const val MAX_CACHED_SUBREDDITS = 300

        private val SUBREDDIT_NAME = Regex("^[a-z0-9_]{2,30}$")

        /** [name] as a cache key — lowercased, without an `r/` prefix — or null if it is not a subreddit name. */
        @JvmStatic
        fun normalize(name: String?): String? {
            val trimmed = name?.trim()?.removePrefix("/")?.lowercase(Locale.ROOT) ?: return null
            val bare = trimmed.removePrefix("r/")
            return bare.takeIf { SUBREDDIT_NAME.matches(it) }
        }
    }
}
