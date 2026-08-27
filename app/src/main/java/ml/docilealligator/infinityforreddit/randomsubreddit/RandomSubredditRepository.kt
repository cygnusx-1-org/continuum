package ml.docilealligator.infinityforreddit.randomsubreddit

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.IOException
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.concurrent.Executor
import java.util.concurrent.ThreadLocalRandom
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.apis.RedditAPI
import ml.docilealligator.infinityforreddit.thing.SortType
import retrofit2.Retrofit

/**
 * Picks a random subreddit that is verified to exist at the moment it is picked.
 *
 * A name straight out of a [RandomSubredditList] is only a plausible candidate, so a pick is never
 * served from the file alone. Two things are checked, and they cost differently:
 *
 *  - **Does it exist?** 100 names are confirmed at once in a **single** `/api/info?sr_name=`
 *    request and kept as a pool, so the next ~99 picks pay nothing for this. Checking one name at
 *    a time would cost a hundred times the requests.
 *  - **Does it have anything to show?** Asked per pick, of the one candidate about to be handed
 *    out, because no batch endpoint reports post counts -- the subreddit object has 97 fields and
 *    none of them is one. That is one small request (~1 KB gzipped) on every pick, pooled or not.
 *
 * So a pooled pick is cheap, not free. Both checks happen before the caller hears anything, which
 * is what makes a pick a single answer rather than a subreddit that gets swapped for a replacement
 * once someone is already looking at it.
 *
 * [pickSubscribed] skips the first check only: the account's own subscriptions are already in Room
 * and are reachable by construction, so there is nothing for `/api/info` to learn about them.
 */
@Singleton
class RandomSubredditRepository @Inject constructor(
    context: Context,
    @Named("download_media") downloadRetrofit: Retrofit,
    @Named("random_subreddits") statePreferences: SharedPreferences,
    private val redditDataRoomDatabase: RedditDataRoomDatabase,
    private val executor: Executor
) {

    companion object {
        private const val TAG = "RandomSubreddit"

        /**
         * Names confirmed per request. One round trip covers roughly ninety-nine picks' worth of
         * existence checks -- not the whole pick, which still checks the drawn name has posts.
         */
        private const val BATCH_SIZE = 100

        /**
         * Posts a subreddit must have to be worth opening. Landing on an empty one is the worst
         * outcome the pick can produce, and about one name in seventy from the SFW list is empty.
         */
        private const val MINIMUM_POSTS = 1

        /**
         * How many candidates one pick will try before giving up. Empties are rare, so this is
         * never reached in practice; it exists so a pathological run cannot spend the whole list.
         */
        private const val MAX_CANDIDATES_PER_PICK = 5

        /**
         * The draw itself, split from Room so it can be tested outright.
         *
         * Unusable names are dropped **before** the draw, never after: filtering afterwards would
         * let the draw land on a blank and report a failed pick while perfectly good candidates
         * sat in the same list. A name that is null or blank must never reach
         * `ViewSubredditDetailActivity`, which would open `r/null` or `r/`.
         *
         * @return the chosen name, or null when nothing usable was supplied -- which for this
         *   flavour means an account with no subscriptions, a legitimate outcome on a healthy
         *   device rather than an error.
         */
        @VisibleForTesting
        fun selectRandomName(names: List<String?>): String? {
            val usable = names.mapNotNull { name -> name?.trim()?.takeIf { it.isNotEmpty() } }
            if (usable.isEmpty()) {
                return null
            }
            return usable[ThreadLocalRandom.current().nextInt(usable.size)]
        }
    }

    private val cache = RandomSubredditListCache(context, downloadRetrofit, statePreferences)

    /** Names already confirmed live, spent one per pick. Guarded by its own monitor. */
    private val validatedPools = EnumMap<RandomSubredditList, ArrayDeque<String>>(RandomSubredditList::class.java)

    interface PickListener {
        fun onRandomSubredditPicked(subredditName: String)

        fun onRandomSubredditPickFailed()
    }

    /**
     * Brings both cached lists up to date if their cadence says it is time. Safe to call on every
     * app open: the due check is what throttles it, and a failure is never surfaced.
     */
    fun refreshListsIfDue() {
        executor.execute {
            for (list in RandomSubredditList.entries) {
                // Per list, and total. `Executor.execute` hands an escaping exception to the
                // thread's uncaught handler, which on Android kills the process -- and this body
                // runs on every app foreground, so an escape here is a crash loop at launch rather
                // than a feature that misbehaves when used. Catching per iteration also keeps one
                // list's failure from costing the other its check: the cadence reads that sit
                // ahead of the guarded fetch would otherwise take both lists down together.
                try {
                    cache.refreshIfDue(list)
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Refresh check for ${list.fileName} failed", e)
                }
            }
        }
    }

    /**
     * @param retrofit the account-appropriate instance -- `no_oauth` for anonymous, which
     *   authenticates itself, `oauth` with [headers] from `APIUtils.getOAuthHeader` otherwise.
     */
    fun pick(
        list: RandomSubredditList,
        retrofit: Retrofit,
        headers: Map<String, String>,
        handler: Handler,
        listener: PickListener
    ) {
        executor.execute {
            val picked = pickNonEmpty(list, retrofit, headers)
            handler.post {
                if (picked == null) {
                    listener.onRandomSubredditPickFailed()
                } else {
                    listener.onRandomSubredditPicked(picked)
                }
            }
        }
    }

    /**
     * Draws candidates until one has posts. Skipping happens here, before the caller hears
     * anything, so a pick is a single answer -- never a subreddit that is then swapped for a
     * replacement once someone is already looking at it.
     */
    private fun pickNonEmpty(
        list: RandomSubredditList,
        retrofit: Retrofit,
        headers: Map<String, String>
    ): String? {
        repeat(MAX_CANDIDATES_PER_PICK) {
            val candidate = takeFromPool(list) ?: refillPool(list, retrofit, headers) ?: return null
            if (hasPosts(candidate, retrofit, headers)) {
                return candidate
            }
            Log.w(TAG, "Skipping $candidate: no posts")
        }
        return null
    }

    /** Confirms a fresh batch of names and pools them, returning one to try. */
    private fun refillPool(
        list: RandomSubredditList,
        retrofit: Retrofit,
        headers: Map<String, String>
    ): String? {
        val survivors = validateBatch(list, retrofit, headers)
        if (survivors.isEmpty()) {
            return null
        }
        addToPool(list, survivors.shuffled())
        return takeFromPool(list)
    }

    /**
     * Picks one of [accountName]'s own subscriptions. No list, no download, no cache and no
     * `/api/info` round trip: the names are already in Room, and every one of them is a subreddit
     * that account can reach, so there is nothing for that call to learn.
     *
     * It does still check the drawn name has posts, which is a request -- an empty subreddit is a
     * bad landing however you got there. That check fails open, so this path keeps working with no
     * network at all, exactly as it did before the check existed.
     *
     * Anonymous is not a special case -- its subscriptions live in the same table under
     * `Account.ANONYMOUS_ACCOUNT`, and the name is passed straight through.
     *
     * Deliberately poolless. The pools exist to amortise the `/api/info` batch, which this path
     * never makes, and re-reading an indexed local table per tap costs less than the bookkeeping --
     * with the side benefit that a subreddit subscribed to a moment ago is eligible on the next tap.
     */
    fun pickSubscribed(
        accountName: String,
        retrofit: Retrofit,
        headers: Map<String, String>,
        handler: Handler,
        listener: PickListener
    ) {
        executor.execute {
            val picked = try {
                pickNonEmptySubscribed(accountName, retrofit, headers)
            } catch (e: RuntimeException) {
                // Room throwing -- a corrupt or closed database -- must still answer the listener.
                // `Executor.execute` hands an escaping exception to the thread's uncaught handler,
                // which on Android kills the process, so an unguarded read here is an app crash
                // rather than the failed pick the caller is built to show.
                Log.w(TAG, "Reading $accountName's subscriptions failed", e)
                null
            }
            handler.post {
                if (picked == null) {
                    listener.onRandomSubredditPickFailed()
                } else {
                    listener.onRandomSubredditPicked(picked)
                }
            }
        }
    }

    /**
     * Picks for whichever flavour [randomSubredditName] names. The one place that mapping lives, so
     * a caller only has to pass on the name it was handed -- from a tab, an intent extra or a typed
     * subreddit -- without knowing which of the two pick paths it implies.
     *
     * [retrofit] and [headers] are ignored for `myrandom`, which reads Room; pass the
     * account-appropriate pair regardless. A name that is not one of the three fails the pick,
     * which is the same answer the caller would give it anyway.
     */
    fun pickForName(
        randomSubredditName: String?,
        accountName: String,
        retrofit: Retrofit,
        headers: Map<String, String>,
        handler: Handler,
        listener: PickListener
    ) {
        when (RandomSubredditNames.canonicalise(randomSubredditName)) {
            RandomSubredditNames.MYRANDOM -> pickSubscribed(accountName, retrofit, headers, handler, listener)
            RandomSubredditNames.RANDNSFW -> pick(RandomSubredditList.NSFW, retrofit, headers, handler, listener)
            RandomSubredditNames.RANDOM -> pick(RandomSubredditList.SFW, retrofit, headers, handler, listener)
            else -> handler.post { listener.onRandomSubredditPickFailed() }
        }
    }

    private fun validateBatch(
        list: RandomSubredditList,
        retrofit: Retrofit,
        headers: Map<String, String>
    ): List<String> {
        // Sampling is inside the try, not before it: this runs on the shared executor, and an
        // exception that escapes `Executor.execute` goes to the thread's uncaught handler, which
        // on Android kills the process instead of reaching the caller as a failed pick.
        return try {
            var candidates = cache.sampleCandidates(list, BATCH_SIZE)
            if (candidates.isEmpty()) {
                // Neither the downloaded copy nor the one shipped in the APK could be read, which
                // in practice means a build made without the sibling `subreddit-lists` checkout
                // the assets are symlinked from. This is the one place a pick waits on the
                // download -- which is what the progress screen is there for.
                cache.fetchNow(list)
                candidates = cache.sampleCandidates(list, BATCH_SIZE)
            }
            if (candidates.isEmpty()) {
                return emptyList()
            }

            val response = retrofit.create(RedditAPI::class.java)
                .getSubredditsInfo(headers, candidates.joinToString(","))
                .execute()
            if (response.isSuccessful) {
                RandomSubredditInfoParser.parseLiveSubredditNames(
                    response.body().orEmpty(),
                    list == RandomSubredditList.NSFW
                )
            } else {
                Log.w(TAG, "Validating ${list.fileName} candidates returned HTTP ${response.code()}")
                emptyList()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Validating ${list.fileName} candidates failed", e)
            emptyList()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Validating ${list.fileName} candidates failed", e)
            emptyList()
        }
    }

    /**
     * Draws from [accountName]'s subscriptions until one has posts, dropping each empty one from
     * the running list so a second draw cannot land on it again.
     *
     * Separate from the executor body on purpose: `repeat` has no break, and `return@repeat` is a
     * continue, so the exit has to be a real return from a real function.
     */
    private fun pickNonEmptySubscribed(
        accountName: String,
        retrofit: Retrofit,
        headers: Map<String, String>
    ): String? {
        val subscribed = redditDataRoomDatabase.subscribedSubredditDao()
            .getAllSubscribedSubredditsList(accountName)
        // Typed nullable deliberately. The name column carries no NOT NULL, and declaring the
        // element non-null here would turn a null row into an NPE on the way to the filter that
        // exists to drop it.
        val remaining: MutableList<String?> = subscribed.map { it.name }.toMutableList()
        repeat(MAX_CANDIDATES_PER_PICK) {
            val candidate = selectRandomName(remaining) ?: return null
            if (hasPosts(candidate, retrofit, headers)) {
                return candidate
            }
            Log.w(TAG, "Skipping subscribed $candidate: no posts")
            remaining.removeAll { it?.trim() == candidate }
        }
        return null
    }

    /**
     * Whether [subredditName] has anything to show. Asks for a single post, which is the smallest
     * request that answers the question -- around 1 KB gzipped.
     *
     * **Fails open.** Only a response that came back and reported zero posts counts as empty; a
     * network error, a non-2xx or an unparseable body all return true. That keeps the check from
     * turning an offline device into a pick that never succeeds, and it is why `myrandom` still
     * works with no network at all.
     */
    private fun hasPosts(
        subredditName: String,
        retrofit: Retrofit,
        headers: Map<String, String>
    ): Boolean {
        return try {
            val response = retrofit.create(RedditAPI::class.java)
                .getSubredditBestPostsOauth(subredditName, SortType.Type.HOT, null, null, MINIMUM_POSTS, headers)
                .execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Post check for $subredditName returned HTTP ${response.code()}")
                return true
            }
            val posts = RandomSubredditInfoParser.countPosts(response.body().orEmpty())
            posts == RandomSubredditInfoParser.UNKNOWN_POST_COUNT || posts >= MINIMUM_POSTS
        } catch (e: IOException) {
            Log.w(TAG, "Post check for $subredditName failed", e)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Post check for $subredditName failed", e)
            true
        }
    }

    private fun takeFromPool(list: RandomSubredditList): String? = synchronized(validatedPools) {
        validatedPools[list]?.poll()
    }

    private fun addToPool(list: RandomSubredditList, names: List<String>) = synchronized(validatedPools) {
        validatedPools.getOrPut(list) { ArrayDeque() }.addAll(names)
    }
}
