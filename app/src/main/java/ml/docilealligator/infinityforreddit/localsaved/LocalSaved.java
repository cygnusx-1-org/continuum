package ml.docilealligator.infinityforreddit.localsaved;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * Local Saved: keeps a local record of the fullnames the user saves on Reddit so items that
 * Reddit silently drops from the /saved listing (e.g. mod-removed) remain recoverable.
 * <p>
 * Faithful port of Slide's LocalSaved. Every in-app save writes a {@code PENDING} marker and
 * kicks {@link #reconcile}, which walks the live /saved listing: a pending fullname that is
 * absent from /saved was dropped by Reddit and is {@code PROMOTED} (shown in the Local Saved
 * tabs); a pending fullname still present is pruned; a promoted fullname that reappears is
 * un-promoted. Applies to logged-in accounts only (anonymous saves are handled by ReadPost).
 */
public class LocalSaved {
    // Single-flight guard: overlapping triggers collapse into one reconcile run.
    private static final AtomicBoolean RECONCILING = new AtomicBoolean(false);
    // Safety cap so a huge /saved history can't spin forever.
    private static final int MAX_RECONCILE_PAGES = 10;
    // Used by the Context-based convenience overloads (call sites without an injected Executor).
    private static final Executor LOCAL_EXECUTOR = Executors.newSingleThreadExecutor();

    public static boolean isEnabledForAccount(@Nullable String accountName) {
        return accountName != null && !accountName.isEmpty()
                && !accountName.equals(Account.ANONYMOUS_ACCOUNT);
    }

    private static RedditDataRoomDatabase getDatabase(Context context) {
        return ((Infinity) context.getApplicationContext()).mRedditDataRoomDatabase;
    }

    /** Convenience overload for call sites (e.g. comment adapters) that lack an injected DB/Executor. */
    public static void onSaved(Context context, Retrofit oauthRetrofit, String accessToken,
                               String accountName, String fullName) {
        onSaved(getDatabase(context), LOCAL_EXECUTOR, oauthRetrofit, accessToken, accountName, fullName);
    }

    /** Convenience overload for call sites that lack an injected DB/Executor. */
    public static void onUnsaved(Context context, String accountName, String fullName) {
        onUnsaved(getDatabase(context), LOCAL_EXECUTOR, accountName, fullName);
    }

    /** Convenience overload for call sites that lack an injected DB. */
    public static void reconcile(Context context, Retrofit oauthRetrofit, String accessToken,
                                 String accountName) {
        reconcile(getDatabase(context), oauthRetrofit, accessToken, accountName);
    }

    /** Convenience overload for call sites that have a DB but no Executor (e.g. a ViewModel). */
    public static void onSaved(RedditDataRoomDatabase db, Retrofit oauthRetrofit, String accessToken,
                               String accountName, String fullName) {
        onSaved(db, LOCAL_EXECUTOR, oauthRetrofit, accessToken, accountName, fullName);
    }

    /** Convenience overload for call sites that have a DB but no Executor. */
    public static void onUnsaved(RedditDataRoomDatabase db, String accountName, String fullName) {
        onUnsaved(db, LOCAL_EXECUTOR, accountName, fullName);
    }

    /**
     * Record a save locally (as PENDING) and trigger reconciliation. Call after the server-side
     * save succeeds. No-op for anonymous accounts.
     */
    public static void onSaved(RedditDataRoomDatabase db, Executor executor, Retrofit oauthRetrofit,
                               String accessToken, String accountName, String fullName) {
        if (!isEnabledForAccount(accountName) || fullName == null || fullName.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            db.localSavedThingDao().insert(new LocalSavedThing(accountName, fullName,
                    LocalSavedState.PENDING, System.currentTimeMillis()));
            reconcile(db, oauthRetrofit, accessToken, accountName);
        });
    }

    /**
     * Remove a local marker (both pending and promoted). Call after the server-side unsave
     * succeeds. No-op for anonymous accounts.
     */
    public static void onUnsaved(RedditDataRoomDatabase db, Executor executor, String accountName,
                                 String fullName) {
        if (!isEnabledForAccount(accountName) || fullName == null || fullName.isEmpty()) {
            return;
        }
        executor.execute(() -> db.localSavedThingDao().delete(accountName, fullName));
    }

    /**
     * Reconcile pending/promoted markers against the live /saved listing. Single-flight. The
     * blocking network walk always runs on the dedicated {@link #LOCAL_EXECUTOR} so it never ties
     * up the app's shared executor. Any network/parse failure aborts WITHOUT mutating state, so a
     * failed fetch can never false-promote a save.
     */
    public static void reconcile(RedditDataRoomDatabase db, Retrofit oauthRetrofit,
                                 String accessToken, String accountName) {
        if (!isEnabledForAccount(accountName)) {
            return;
        }
        if (!RECONCILING.compareAndSet(false, true)) {
            return;
        }
        LOCAL_EXECUTOR.execute(() -> {
            try {
                reconcileInternal(db, oauthRetrofit, accessToken, accountName);
            } catch (Exception ignored) {
                // Swallow: never mutate state on failure.
            } finally {
                RECONCILING.set(false);
            }
        });
    }

    private static void reconcileInternal(RedditDataRoomDatabase db, Retrofit oauthRetrofit,
                                          String accessToken, String accountName) throws Exception {
        LocalSavedThingDao dao = db.localSavedThingDao();
        List<LocalSavedThing> pending = dao.getByState(accountName, LocalSavedState.PENDING);
        List<LocalSavedThing> promoted = dao.getByState(accountName, LocalSavedState.PROMOTED);
        if (pending.isEmpty() && promoted.isEmpty()) {
            return;
        }

        // Fullnames we still need to locate in /saved; once all are seen we can stop paginating.
        Set<String> wanted = new HashSet<>();
        for (LocalSavedThing t : pending) {
            wanted.add(t.getFullName());
        }
        for (LocalSavedThing t : promoted) {
            wanted.add(t.getFullName());
        }

        Set<String> present = new HashSet<>();
        RedditAPI api = oauthRetrofit.create(RedditAPI.class);
        String after = null;
        // Whether we walked enough of /saved to conclude a missing pending item was truly dropped:
        // either we exhausted the listing (after == null) or we located every marker we cared about.
        boolean walkComplete = false;
        for (int page = 0; page < MAX_RECONCILE_PAGES; page++) {
            Call<String> call = api.getSavedThingsForReconcileOauth(
                    APIUtils.getOAuthHeader(accessToken), accountName, "saved", after);
            Response<String> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                // Fetch failed -> abort without mutating any state (never false-promote).
                return;
            }
            after = collectFullNames(response.body(), present);
            if (present.containsAll(wanted) || after == null) {
                walkComplete = true;
                break;
            }
        }

        // Apply transitions.
        for (LocalSavedThing t : pending) {
            if (present.contains(t.getFullName())) {
                // Still on the server -> just a normal save, drop the pending marker.
                dao.delete(accountName, t.getFullName());
            } else if (walkComplete) {
                // Conclusively absent from /saved -> Reddit dropped it -> surface it.
                dao.setState(accountName, t.getFullName(), LocalSavedState.PROMOTED);
            }
            // else: the walk was truncated at the page cap, so absence is inconclusive.
            // Leave the marker PENDING for a later reconcile rather than risk a false promote.
        }
        for (LocalSavedThing t : promoted) {
            if (present.contains(t.getFullName())) {
                // Reappeared on the server -> no longer "lost".
                dao.delete(accountName, t.getFullName());
            }
        }
    }

    /**
     * Adds every {@code data.children[].data.name} in the listing to {@code present} and returns
     * the {@code data.after} paging token (or null when there are no more pages).
     */
    @Nullable
    private static String collectFullNames(@NonNull String listingJson, Set<String> present) throws Exception {
        JSONObject data = new JSONObject(listingJson).getJSONObject("data");
        JSONArray children = data.getJSONArray("children");
        for (int i = 0; i < children.length(); i++) {
            JSONObject childData = children.getJSONObject(i).optJSONObject("data");
            if (childData != null) {
                String name = childData.optString("name", null);
                if (name != null && !name.isEmpty()) {
                    present.add(name);
                }
            }
        }
        if (data.isNull("after")) {
            return null;
        }
        String after = data.optString("after", null);
        // Treat an empty token as "no more pages" so we don't re-fetch page 1 in a loop.
        return (after != null && after.isEmpty()) ? null : after;
    }
}
