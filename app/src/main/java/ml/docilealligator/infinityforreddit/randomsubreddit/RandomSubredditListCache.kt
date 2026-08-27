package ml.docilealligator.infinityforreddit.randomsubreddit

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom
import ml.docilealligator.infinityforreddit.apis.DownloadFile
import retrofit2.Retrofit

/**
 * The on-disk half of the feature: one cached copy of each [RandomSubredditList], the conditional
 * refresh that keeps it current, and the sampling that draws candidates out of it.
 *
 * A copy of each list ships in the APK as an asset so the first pick never waits on a download,
 * and every refresh writes to [CACHE_DIRECTORY], which takes precedence from then on -- the shipped
 * copy is a starting point, not a fallback the updates have to work around.
 *
 * A list is never held in memory. The SFW file is 328,366 names, and keeping that as Java strings
 * would cost around 20 MB for a button; instead [sampleCandidates] takes one streaming pass and
 * reservoir-samples, which allocates the sample and nothing else.
 *
 * Every method here blocks and must be called off the main thread.
 */
class RandomSubredditListCache(
    private val context: Context,
    private val downloadRetrofit: Retrofit,
    private val statePreferences: SharedPreferences
) {

    private companion object {
        const val TAG = "RandomSubredditList"
        const val CACHE_DIRECTORY = "random_subreddits"
        const val ASSET_DIRECTORY = "random_subreddits"
        const val ETAG_HEADER = "ETag"
        const val NOT_MODIFIED = 304
    }

    /**
     * [count] names drawn uniformly at random, without holding the file. Reads the downloaded copy
     * if a refresh has landed one, otherwise the copy shipped in the APK. Returns fewer than
     * [count] only if the list itself is shorter, and empty only if neither copy can be read --
     * which the caller must answer by fetching one, or by treating the pick as failed.
     */
    fun sampleCandidates(list: RandomSubredditList, count: Int): List<String> {
        val reservoir = ArrayList<String>(count)
        val random = ThreadLocalRandom.current()
        var seen = 0

        try {
            openList(list)?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).forEachLine { line ->
                    val name = line.trim()
                    if (name.isNotEmpty()) {
                        if (seen < count) {
                            reservoir.add(name)
                        } else {
                            // Algorithm R: the (seen+1)-th name replaces a held one with
                            // probability count/(seen+1), which leaves every name equally likely.
                            val index = random.nextInt(seen + 1)
                            if (index < count) {
                                reservoir[index] = name
                            }
                        }
                        seen++
                    }
                }
            }
        } catch (e: IOException) {
            // A half-read file still yields a usable sample of what was read.
            Log.w(TAG, "Could not read ${list.fileName}", e)
        }

        return reservoir
    }

    /**
     * Fetches the remote copy if the cadence says it is time, replacing the cache on a 200 and
     * doing nothing at all on a 304. Failures are silent by design: a stale list still picks, so
     * there is nothing here worth interrupting anyone over.
     */
    fun refreshIfDue(list: RandomSubredditList) {
        val due = try {
            RandomSubredditListRefreshPolicy.isCheckDue(
                statePreferences.getLong(list.lastSuccessKey, 0L),
                statePreferences.getLong(list.lastAttemptKey, 0L),
                System.currentTimeMillis()
            )
        } catch (e: ClassCastException) {
            // Nothing here can produce this: only putLong ever touches these keys. Reaching it
            // means the file was corrupted or edited from outside, and left alone it would fail
            // this list's check on every foreground for good, silently, while the other list
            // carried on. So the record is thrown away and rebuilt rather than merely survived.
            Log.w(TAG, "Unreadable check state for ${list.fileName}; starting it over", e)
            clearState(list)
            // Cleared reads as never checked, which is due -- so one refresh repairs it now
            // instead of leaving the list a day behind.
            true
        }

        if (due) {
            fetch(list)
        }
    }

    /**
     * Fetches regardless of the cadence, for a pick that found no list to draw on at all -- which
     * means the shipped copy is missing too, as it is in a build made without the sibling
     * `subreddit-lists` checkout the assets are symlinked from. The alternative there is not a
     * stale pick but no pick, and the cadence's hourly retry is too long to wait through.
     */
    fun fetchNow(list: RandomSubredditList) {
        fetch(list)
    }

    private fun fetch(list: RandomSubredditList) {
        val now = System.currentTimeMillis()
        // A conditional request is only honest while the bytes the tag describes are still on
        // disk: without that, a 304 would leave the cache empty and the check marked current.
        val etag = if (isCached(list)) readEtag(list) else null
        try {
            val response = downloadRetrofit.create(DownloadFile::class.java)
                .downloadFile(list.remoteUrl, if (etag.isNullOrEmpty()) null else etag)
                .execute()

            if (response.code() == NOT_MODIFIED) {
                response.errorBody()?.close()
                // Nothing was replaced, so the stored tag still describes what is on disk.
                recordCheckCompleted(list, now)
                return
            }

            val body = response.body()
            if (!response.isSuccessful || body == null) {
                // Any 4xx counts as a plain failure too: a 404 can be transient mid-push.
                Log.w(TAG, "Refresh of ${list.fileName} returned HTTP ${response.code()}")
                response.errorBody()?.close()
                recordAttempt(list, now)
                return
            }

            body.use { writeCache(list, it.byteStream()) }
            // Written before the timestamps, so a process death between the two leaves the check
            // still due rather than leaving a tag that describes bytes the cache no longer holds.
            storeEtag(list, response.headers()[ETAG_HEADER])
            recordCheckCompleted(list, now)
        } catch (e: IOException) {
            Log.w(TAG, "Refresh of ${list.fileName} failed", e)
            recordAttempt(list, now)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Refresh of ${list.fileName} failed", e)
            recordAttempt(list, now)
        }
    }

    /** The downloaded copy if a refresh has landed one, otherwise the copy shipped in the APK. */
    private fun openList(list: RandomSubredditList): InputStream? {
        if (isCached(list)) {
            try {
                return FileInputStream(cacheFile(list))
            } catch (e: IOException) {
                Log.w(TAG, "Cached ${list.fileName} could not be opened", e)
            }
        }
        return try {
            context.assets.open("$ASSET_DIRECTORY/${list.fileName}")
        } catch (e: IOException) {
            Log.w(TAG, "Shipped ${list.fileName} could not be opened", e)
            null
        }
    }

    /**
     * Whether a completed **download** is on disk for [list]. Deliberately blind to the copy
     * shipped in the APK: this is what decides whether an `If-None-Match` is honest, and the
     * stored tag describes downloaded bytes, never the shipped ones.
     */
    fun isCached(list: RandomSubredditList): Boolean {
        val cached = cacheFile(list)
        return cached.isFile && cached.length() > 0L
    }

    private fun cacheFile(list: RandomSubredditList) =
        File(File(context.filesDir, CACHE_DIRECTORY), list.fileName)

    /**
     * Writes through a temporary file and renames, so a download cut off part-way never becomes
     * the cache -- a truncated list would silently shrink the pool every pick draws from.
     */
    private fun writeCache(list: RandomSubredditList, source: InputStream) {
        val directory = File(context.filesDir, CACHE_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create $directory")
        }

        val destination = cacheFile(list)
        val temporary = File(directory, list.fileName + ".tmp")
        try {
            FileOutputStream(temporary).use { source.copyTo(it) }
            if (!temporary.renameTo(destination)) {
                destination.delete()
                if (!temporary.renameTo(destination)) {
                    throw IOException("Could not move $temporary onto $destination")
                }
            }
        } finally {
            temporary.delete()
        }
    }

    /**
     * The stored tag, or null after dropping one that could not be read as a string. Losing it
     * costs a single unconditional refetch; keeping an unreadable record would cost the check.
     */
    private fun readEtag(list: RandomSubredditList): String? {
        return try {
            statePreferences.getString(list.etagKey, null)
        } catch (e: ClassCastException) {
            Log.w(TAG, "Unreadable ETag for ${list.fileName}; refetching in full", e)
            statePreferences.edit().remove(list.etagKey).apply()
            null
        }
    }

    /**
     * Wipes a list's whole check record, not just the key that would not read. A record that
     * cannot be parsed is one nothing here can reason about, and the ETag least of all: it would
     * otherwise go on describing bytes whose provenance is no longer known.
     */
    private fun clearState(list: RandomSubredditList) {
        statePreferences.edit()
            .remove(list.lastSuccessKey)
            .remove(list.lastAttemptKey)
            .remove(list.etagKey)
            .apply()
    }

    /**
     * The tag that now describes the cached bytes. A 200 that carried no ETag **clears** it rather
     * than keeping the old one: the old tag describes content this cache no longer holds, and
     * replaying it could earn a 304 that marks the wrong bytes current.
     */
    private fun storeEtag(list: RandomSubredditList, etag: String?) {
        val editor = statePreferences.edit()
        if (etag.isNullOrEmpty()) {
            editor.remove(list.etagKey)
        } else {
            editor.putString(list.etagKey, etag)
        }
        editor.apply()
    }

    /** A check that completed, 304 or 200 alike, which is what puts the 24h cadence back. */
    private fun recordCheckCompleted(list: RandomSubredditList, nowUtcMillis: Long) {
        statePreferences.edit()
            .putLong(list.lastSuccessKey, nowUtcMillis)
            .putLong(list.lastAttemptKey, nowUtcMillis)
            .apply()
    }

    private fun recordAttempt(list: RandomSubredditList, nowUtcMillis: Long) {
        statePreferences.edit().putLong(list.lastAttemptKey, nowUtcMillis).apply()
    }
}
