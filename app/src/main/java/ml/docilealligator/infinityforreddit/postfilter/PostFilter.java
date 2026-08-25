package ml.docilealligator.infinityforreddit.postfilter;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import ml.docilealligator.infinityforreddit.post.Post;

@Entity(tableName = "post_filter")
public class PostFilter implements Parcelable {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "name")
    // Empty, not a placeholder: a filter has to be named by whoever makes it, and a default name
    // that looks filled in is one the user saves without reading. The Customize screen refuses to
    // write an unnamed filter, and its name field shows its hint instead.
    public String name = "";
    @ColumnInfo(name = "max_vote")
    public int maxVote = -1;
    @ColumnInfo(name = "min_vote")
    public int minVote = -1;
    @ColumnInfo(name = "max_comments")
    public int maxComments = -1;
    @ColumnInfo(name = "min_comments")
    public int minComments = -1;
    @ColumnInfo(name = "max_awards")
    public int maxAwards = -1;
    @ColumnInfo(name = "min_awards")
    public int minAwards = -1;
    @Ignore
    public boolean allowNSFW;
    @ColumnInfo(name = "only_nsfw")
    public boolean onlyNSFW;
    @ColumnInfo(name = "only_spoiler")
    public boolean onlySpoiler;
    @Nullable
    @ColumnInfo(name = "post_title_excludes_regex")
    public String postTitleExcludesRegex;
    @Nullable
    @ColumnInfo(name = "post_title_contains_regex")
    public String postTitleContainsRegex;
    @Nullable
    @ColumnInfo(name = "post_title_excludes_strings")
    public String postTitleExcludesStrings;
    @Nullable
    @ColumnInfo(name = "post_title_contains_strings")
    public String postTitleContainsStrings;
    @Nullable
    @ColumnInfo(name = "exclude_subreddits")
    public String excludeSubreddits;
    @Nullable
    @ColumnInfo(name = "contain_subreddits")
    public String containSubreddits;
    @Nullable
    @ColumnInfo(name = "exclude_users")
    public String excludeUsers;
    @Nullable
    @ColumnInfo(name = "contain_users")
    public String containUsers;
    @Nullable
    @ColumnInfo(name = "contain_flairs")
    public String containFlairs;
    @Nullable
    @ColumnInfo(name = "exclude_flairs")
    public String excludeFlairs;
    @Nullable
    @ColumnInfo(name =  "exclude_domains")
    public String excludeDomains;
    @Nullable
    @ColumnInfo(name =  "contain_domains")
    public String containDomains;
    @ColumnInfo(name = "contain_text_type")
    public boolean containTextType = true;
    @ColumnInfo(name = "contain_link_type")
    public boolean containLinkType = true;
    @ColumnInfo(name = "contain_image_type")
    public boolean containImageType = true;
    @ColumnInfo(name = "contain_gif_type")
    public boolean containGifType = true;
    @ColumnInfo(name = "contain_video_type")
    public boolean containVideoType = true;
    @ColumnInfo(name = "contain_gallery_type")
    public boolean containGalleryType = true;
    @Ignore
    public ArrayList<String> postTitleExcludesRegexes = new ArrayList<>();
    @Ignore
    public ArrayList<String> postTitleContainsRegexes = new ArrayList<>();

    /**
     * Lower-cased subreddit names that wildcard matching must never hide: the current account's
     * subscribed subreddits plus the {@code u_username} profile subreddits of the users it follows.
     * Refreshed from the database whenever a feed loads its post filter (so an "Exclude subreddits"
     * entry like {@code *story*} cannot accidentally hide r/history when you are subscribed to it).
     * Exact-name entries are still honoured, since those are a deliberate block of one named
     * subreddit. Reassigned wholesale, never mutated in place, so readers on the paging thread
     * always see a complete set.
     */
    public static volatile Set<String> neverHideSubredditsLowerCase = Collections.emptySet();

    /**
     * Per-rule exceptions, as {@code filterName\u0000term\u0000subredditName} keys, all lower-cased.
     * A user who sees r/EarthPorn in the blocked list of a {@code *porn*} rule and marks it an
     * exception lands here, so that one subreddit comes back while the rule keeps hiding the rest.
     * Refreshed alongside {@link #neverHideSubredditsLowerCase} and reassigned wholesale for the
     * same reason.
     */
    public static volatile Set<String> wildcardExceptionKeys = Collections.emptySet();

    /**
     * Whether this filter's wildcard "Exclude subreddits" terms are live.
     *
     * <p>Set when the filter is resolved for a feed (see {@code
     * FetchPostFilterAndConcatenatedSubredditNames}), true only for
     * {@link ml.docilealligator.infinityforreddit.Constants#CONTINUUM_ALL_SUBREDDIT}. Everywhere
     * else a wildcard term is inert and only exact names filter, which is what keeps a broad term
     * from silently emptying Home, Search or a subreddit page.
     *
     * <p>{@code transient} so it stays out of the Gson backup written to {@code post_filters.json};
     * it is a property of the feed a filter was fetched for, not of the stored filter.
     */
    @Ignore
    public transient boolean wildcardSubredditMatchingEnabled = false;

    /**
     * Lower-cased "Exclude subreddits" term to the name of the filter that contributed it, so a
     * blocked subreddit can be attributed back to the rule the user actually wrote.
     *
     * <p>{@link #mergePostFilter} concatenates every applicable filter's terms into one object named
     * "Merged", which would otherwise lose that provenance entirely. Populated by the merge (and by
     * its single-filter fast path) rather than derived later, because by match time the only thing
     * left is the concatenated column.
     */
    @Ignore
    public transient Map<String, String> subredditTermOwners = Collections.emptyMap();

    public PostFilter() {

    }

    protected PostFilter(Parcel in) {
        name = Objects.requireNonNull(in.readString());
        maxVote = in.readInt();
        minVote = in.readInt();
        maxComments = in.readInt();
        minComments = in.readInt();
        maxAwards = in.readInt();
        minAwards = in.readInt();
        allowNSFW = in.readByte() != 0;
        onlyNSFW = in.readByte() != 0;
        onlySpoiler = in.readByte() != 0;
        postTitleExcludesRegex = in.readString();
        postTitleContainsRegex = in.readString();
        postTitleExcludesStrings = in.readString();
        postTitleContainsStrings = in.readString();
        excludeSubreddits = in.readString();
        containSubreddits = in.readString();
        excludeUsers = in.readString();
        containUsers = in.readString();
        containFlairs = in.readString();
        excludeFlairs = in.readString();
        excludeDomains = in.readString();
        containDomains = in.readString();
        containTextType = in.readByte() != 0;
        containLinkType = in.readByte() != 0;
        containImageType = in.readByte() != 0;
        containGifType = in.readByte() != 0;
        containVideoType = in.readByte() != 0;
        containGalleryType = in.readByte() != 0;
        postTitleExcludesRegexes = new ArrayList<>();
        in.readStringList(postTitleExcludesRegexes);
        postTitleContainsRegexes = new ArrayList<>();
        in.readStringList(postTitleContainsRegexes);
        // Both of these describe the feed this filter was resolved for, not the stored filter, but
        // they still have to survive a Parcel: PostFragment keeps its resolved filter in saved
        // instance state and reuses it verbatim on restore instead of refetching. Dropping them
        // there would leave every wildcard term silently inert after the process is recreated.
        wildcardSubredditMatchingEnabled = in.readByte() != 0;
        int termOwnerCount = in.readInt();
        if (termOwnerCount > 0) {
            Map<String, String> owners = new HashMap<>(termOwnerCount);
            for (int i = 0; i < termOwnerCount; i++) {
                String term = in.readString();
                String owner = in.readString();
                if (term != null && owner != null) {
                    owners.put(term, owner);
                }
            }
            subredditTermOwners = owners;
        }
    }

    public static final Creator<PostFilter> CREATOR = new Creator<PostFilter>() {
        @Override
        public PostFilter createFromParcel(Parcel in) {
            return new PostFilter(in);
        }

        @Override
        public PostFilter[] newArray(int size) {
            return new PostFilter[size];
        }
    };

    public static boolean isPostAllowed(@Nullable Post post, @Nullable PostFilter postFilter) {
        if (postFilter == null || post == null) {
            return true;
        }
        if (post.isNSFW() && !postFilter.allowNSFW) {
            return false;
        }
        if(post.isStickied()){
            return true;
        }
        if (postFilter.maxVote > 0 && post.getVoteType() + post.getScore() > postFilter.maxVote) {
            return false;
        }
        if (postFilter.minVote > 0 && post.getVoteType() + post.getScore() < postFilter.minVote) {
            return false;
        }
        if (postFilter.maxComments > 0 && post.getNComments() > postFilter.maxComments) {
            return false;
        }
        if (postFilter.minComments > 0 && post.getNComments() < postFilter.minComments) {
            return false;
        }
        if (postFilter.onlyNSFW && !post.isNSFW()) {
            if (postFilter.onlySpoiler) {
                return post.isSpoiler();
            }
            return false;
        }
        if (postFilter.onlySpoiler && !post.isSpoiler()) {
            if (postFilter.onlyNSFW) {
                return post.isNSFW();
            }
            return false;
        }
        if (!postFilter.containTextType && post.getPostType() == Post.TEXT_TYPE) {
            return false;
        }
        if (!postFilter.containLinkType && (post.getPostType() == Post.LINK_TYPE || post.getPostType() == Post.NO_PREVIEW_LINK_TYPE)) {
            return false;
        }
        if (!postFilter.containImageType && post.getPostType() == Post.IMAGE_TYPE) {
            return false;
        }
        if (!postFilter.containGifType && post.getPostType() == Post.GIF_TYPE) {
            return false;
        }
        if (!postFilter.containVideoType && post.getPostType() == Post.VIDEO_TYPE) {
            return false;
        }
        if (!postFilter.containGalleryType && post.getPostType() == Post.GALLERY_TYPE) {
            return false;
        }
        if (postFilter.postTitleExcludesRegexes.isEmpty()
                && postFilter.postTitleExcludesRegex != null
                && !postFilter.postTitleExcludesRegex.isEmpty()
        ) {
            postFilter.postTitleExcludesRegexes.add(postFilter.postTitleExcludesRegex);
        }
        if (!postFilter.postTitleExcludesRegexes.isEmpty()) {
            for (String regex : postFilter.postTitleExcludesRegexes) {
                try {
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(post.getTitle());
                    if (matcher.find()) {
                        return false;
                    }
                } catch (PatternSyntaxException e) {
                    Log.e("PostFilter", "isPostAllowed failed", e);
                }
            }
        }
        if (postFilter.postTitleContainsRegexes.isEmpty()
                && postFilter.postTitleContainsRegex != null
                && !postFilter.postTitleContainsRegex.isEmpty()
        ) {
            postFilter.postTitleContainsRegexes.add(postFilter.postTitleContainsRegex);
        }
        if (!postFilter.postTitleContainsRegexes.isEmpty()) {
            boolean matched = false;
            for (String regex : postFilter.postTitleContainsRegexes) {
                try {
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(post.getTitle());
                    if (matcher.find()) {
                        matched = true;
                        break;
                    }
                } catch (PatternSyntaxException e) {
                    Log.e("PostFilter", "isPostAllowed failed", e);
                }
            }
            if (!matched) {
                return false;
            }
        }
        if (postFilter.postTitleExcludesStrings != null && !postFilter.postTitleExcludesStrings.equals("")) {
            String[] titles = postFilter.postTitleExcludesStrings.split(",", 0);
            for (String t : titles) {
                // Locale.ROOT, like the subreddit/domain matching below: on a Turkish/Azeri device
                // the device locale lower-cases "I" to dotless "i", so a keyword would stop matching
                // a title that differs from it only in the case of an I.
                if (!t.trim().equals("") && post.getTitle().toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT).trim())) {
                    return false;
                }
            }
        }
        if (postFilter.postTitleContainsStrings != null && !postFilter.postTitleContainsStrings.equals("")) {
            String[] titles = postFilter.postTitleContainsStrings.split(",", 0);
            boolean hasRequiredString = false;
            for (String t : titles) {
                if (post.getTitle().toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT).trim())) {
                    hasRequiredString = true;
                    break;
                }
            }
            if (!hasRequiredString) {
                return false;
            }
        }
        if (postFilter.excludeSubreddits != null && !postFilter.excludeSubreddits.equals("")) {
            String[] subreddits = postFilter.excludeSubreddits.split(",", 0);
            String subredditName = post.getSubredditName();
            for (String s : subreddits) {
                String filter = s.trim();
                if (filter.isEmpty()) {
                    continue;
                }
                boolean wildcard = SubredditMatcher.isWildcard(filter);
                // A wildcard term is scoped to one feed; everywhere else only exact names filter,
                // so a broad term cannot quietly empty Home, Search or a subreddit page.
                if (wildcard && !postFilter.wildcardSubredditMatchingEnabled) {
                    continue;
                }
                if (!SubredditMatcher.matches(subredditName, filter)) {
                    continue;
                }
                if (wildcard) {
                    // Lower-cased only once a wildcard term has actually matched: a list of plain
                    // names is the common case and must not allocate a copy of every post's
                    // subreddit name to answer it.
                    if (neverHideSubredditsLowerCase.contains(subredditName.toLowerCase(Locale.ENGLISH))) {
                        continue;
                    }
                    String owner = postFilter.subredditTermOwners.get(filter.toLowerCase(Locale.ENGLISH));
                    if (owner == null) {
                        owner = postFilter.name;
                    }
                    if (wildcardExceptionKeys.contains(exceptionKey(owner, filter, subredditName))) {
                        continue;
                    }
                    PostFilterBlockRecorder.record(owner, filter, subredditName);
                }
                return false;
            }
        }
        if (postFilter.containSubreddits != null && !postFilter.containSubreddits.equals("")) {
            String[] subreddits = postFilter.containSubreddits.split(",", 0);
            boolean hasRequiredSubreddit = false;
            // An "only show these subreddits" term hides every post that does not match it, so a
            // term that cannot match on this feed must not count as a requirement at all -- treating
            // an inert wildcard as unsatisfied would empty the feed instead of ignoring the term.
            boolean hasUsableTerm = false;
            String subreddit = post.getSubredditName();
            for (String s : subreddits) {
                String filter = s.trim();
                if (filter.isEmpty()) {
                    continue;
                }
                if (SubredditMatcher.isWildcard(filter) && !postFilter.wildcardSubredditMatchingEnabled) {
                    continue;
                }
                hasUsableTerm = true;
                if (SubredditMatcher.matches(subreddit, filter)) {
                    hasRequiredSubreddit = true;
                    break;
                }
            }
            if (hasUsableTerm && !hasRequiredSubreddit) {
                return false;
            }
        }
        if (postFilter.excludeUsers != null && !postFilter.excludeUsers.equals("")) {
            String[] users = postFilter.excludeUsers.split(",", 0);
            for (String u : users) {
                if (!u.trim().equals("") && post.getAuthor().equalsIgnoreCase(u.trim())) {
                    return false;
                }
            }
        }
        if (postFilter.containUsers != null && !postFilter.containUsers.equals("")) {
            String[] users = postFilter.containUsers.split(",", 0);
            boolean hasRequiredUser = false;
            String user = post.getAuthor();
            for (String s : users) {
                if (!s.trim().equals("") && user.equalsIgnoreCase(s.trim())) {
                    hasRequiredUser = true;
                    break;
                }
            }
            if (!hasRequiredUser) {
                return false;
            }
        }
        if (postFilter.excludeFlairs != null && !postFilter.excludeFlairs.equals("")) {
            String[] flairs = postFilter.excludeFlairs.split(",", 0);
            for (String f : flairs) {
                if (!f.trim().equals("") && post.getFlair().trim().equalsIgnoreCase(f.trim())) {
                    return false;
                }
            }
        }
        if (post.getUrl() != null && postFilter.excludeDomains != null && !postFilter.excludeDomains.equals("")) {
            String[] domains = postFilter.excludeDomains.split(",", 0);
            String url = post.getUrl().toLowerCase(Locale.US);
            for (String f : domains) {
                if (!f.trim().equals("") && url.contains(f.trim().toLowerCase(Locale.US))) {
                    return false;
                }
            }
        }
        if (post.getUrl() != null && postFilter.containDomains != null && !postFilter.containDomains.equals("")) {
            String[] domains = postFilter.containDomains.split(",", 0);
            String url = post.getUrl().toLowerCase(Locale.US);
            boolean hasRequiredDomain = false;
            for (String f : domains) {
                if (url.contains(f.trim().toLowerCase(Locale.US))) {
                    hasRequiredDomain = true;
                    break;
                }
            }
            if (!hasRequiredDomain) {
                return false;
            }
        }
        if (postFilter.containFlairs != null && !postFilter.containFlairs.equals("")) {
            String[] flairs = postFilter.containFlairs.split(",", 0);
            if (flairs.length > 0) {
                boolean match = false;
                for (int i = 0; i < flairs.length; i++) {
                    String flair = flairs[i].trim();
                    if (flair.equals("") && i == flairs.length - 1) {
                        return false;
                    }
                    if (!flair.equals("") && post.getFlair().trim().equalsIgnoreCase(flair)) {
                        match = true;
                        break;
                    }
                }

                return match;
            }
        }

        return true;
    }

    public static PostFilter mergePostFilter(List<PostFilter> postFilterList) {
        if (postFilterList.size() == 1) {
            PostFilter only = postFilterList.get(0);
            only.subredditTermOwners = subredditTermOwnersOf(only);
            return only;
        }
        PostFilter postFilter = new PostFilter();
        StringBuilder stringBuilder;
        Map<String, String> termOwners = new HashMap<>();
        postFilter.name = "Merged";
        for (PostFilter p : postFilterList) {
            // The two directions are not symmetric, which is what made the old `Math.min` on the
            // maxima look right. The seed above is a fresh PostFilter, whose bounds are the -1 "no
            // bound" sentinel that isPostAllowed tests with `> 0`. Math.max against -1 does
            // accumulate the strictest minimum, but Math.min against -1 can never leave -1, so every
            // maximum the user set was dropped by any merge of two or more filters.
            postFilter.maxVote = strictestMaximum(postFilter.maxVote, p.maxVote);
            postFilter.minVote = Math.max(p.minVote, postFilter.minVote);
            postFilter.maxComments = strictestMaximum(postFilter.maxComments, p.maxComments);
            postFilter.minComments = Math.max(p.minComments, postFilter.minComments);
            postFilter.maxAwards = strictestMaximum(postFilter.maxAwards, p.maxAwards);
            postFilter.minAwards = Math.max(p.minAwards, postFilter.minAwards);

            postFilter.onlyNSFW = p.onlyNSFW ? p.onlyNSFW : postFilter.onlyNSFW;
            postFilter.onlySpoiler = p.onlySpoiler ? p.onlySpoiler : postFilter.onlySpoiler;

            if (p.postTitleExcludesRegex != null && !p.postTitleExcludesRegex.isEmpty()) {
                postFilter.postTitleExcludesRegexes.add(p.postTitleExcludesRegex);
                postFilter.postTitleExcludesRegex = p.postTitleExcludesRegex;
            }

            if (p.postTitleContainsRegex != null && !p.postTitleContainsRegex.isEmpty()) {
                postFilter.postTitleContainsRegexes.add(p.postTitleContainsRegex);
                postFilter.postTitleContainsRegex = p.postTitleContainsRegex;
            }

            if (p.postTitleExcludesStrings != null && !p.postTitleExcludesStrings.equals("")) {
                stringBuilder = new StringBuilder(postFilter.postTitleExcludesStrings == null ? "" : postFilter.postTitleExcludesStrings);
                stringBuilder.append(",").append(p.postTitleExcludesStrings);
                postFilter.postTitleExcludesStrings = stringBuilder.toString();
            }

            if (p.postTitleContainsStrings != null && !p.postTitleContainsStrings.equals("")) {
                stringBuilder = new StringBuilder(postFilter.postTitleContainsStrings == null ? "" : postFilter.postTitleContainsStrings);
                stringBuilder.append(",").append(p.postTitleContainsStrings);
                postFilter.postTitleContainsStrings = stringBuilder.toString();
            }

            if (p.excludeSubreddits != null && !p.excludeSubreddits.equals("")) {
                stringBuilder = new StringBuilder(postFilter.excludeSubreddits == null ? "" : postFilter.excludeSubreddits);
                stringBuilder.append(",").append(p.excludeSubreddits);
                postFilter.excludeSubreddits = stringBuilder.toString();
                // Remember which filter each term came from before the concatenation makes it
                // impossible to tell. First writer wins, so a term two filters share is attributed
                // to the one the user sees first in the list -- putAll would hand it to the last.
                for (Map.Entry<String, String> owned : subredditTermOwnersOf(p).entrySet()) {
                    termOwners.putIfAbsent(owned.getKey(), owned.getValue());
                }
            }

            if (p.containSubreddits != null && !p.containSubreddits.equals("")) {
                stringBuilder = new StringBuilder(postFilter.containSubreddits == null ? "" : postFilter.containSubreddits);
                stringBuilder.append(",").append(p.containSubreddits);
                postFilter.containSubreddits = stringBuilder.toString();
            }

            if (p.excludeUsers != null && !p.excludeUsers.equals("")) {
                stringBuilder = new StringBuilder(postFilter.excludeUsers == null ? "" : postFilter.excludeUsers);
                stringBuilder.append(",").append(p.excludeUsers);
                postFilter.excludeUsers = stringBuilder.toString();
            }

            if (p.containUsers != null && !p.containUsers.equals("")) {
                stringBuilder = new StringBuilder(postFilter.containUsers == null ? "" : postFilter.containUsers);
                stringBuilder.append(",").append(p.containUsers);
                postFilter.containUsers = stringBuilder.toString();
            }

            if (p.containFlairs != null && !p.containFlairs.equals("")) {
                stringBuilder = new StringBuilder(postFilter.containFlairs == null ? "" : postFilter.containFlairs);
                stringBuilder.append(",").append(p.containFlairs);
                postFilter.containFlairs = stringBuilder.toString();
            }

            if (p.excludeFlairs != null && !p.excludeFlairs.equals("")) {
                stringBuilder = new StringBuilder(postFilter.excludeFlairs == null ? "" : postFilter.excludeFlairs);
                stringBuilder.append(",").append(p.excludeFlairs);
                postFilter.excludeFlairs = stringBuilder.toString();
            }

            if (p.excludeDomains != null && !p.excludeDomains.equals("")) {
                stringBuilder = new StringBuilder(postFilter.excludeDomains == null ? "" : postFilter.excludeDomains);
                stringBuilder.append(",").append(p.excludeDomains);
                postFilter.excludeDomains = stringBuilder.toString();
            }

            if (p.containDomains != null && !p.containDomains.equals("")) {
                stringBuilder = new StringBuilder(postFilter.containDomains == null ? "" : postFilter.containDomains);
                stringBuilder.append(",").append(p.containDomains);
                postFilter.containDomains = stringBuilder.toString();
            }

            postFilter.containTextType = p.containTextType && postFilter.containTextType;
            postFilter.containLinkType = p.containLinkType && postFilter.containLinkType;
            postFilter.containImageType = p.containImageType && postFilter.containImageType;
            postFilter.containGifType = p.containGifType && postFilter.containGifType;
            postFilter.containVideoType = p.containVideoType && postFilter.containVideoType;
            postFilter.containGalleryType = p.containGalleryType && postFilter.containGalleryType;
        }

        postFilter.subredditTermOwners = termOwners;
        return postFilter;
    }

    /**
     * Folds one filter's maximum bound into a running merge, strictest-wins. Only a positive bound
     * is in force -- {@link #isPostAllowed} tests every maximum with {@code > 0}, and both -1 (the
     * unset sentinel) and 0 mean "no maximum" -- so the strictest of two is the smaller of the ones
     * actually set, and a filter that sets none leaves the running value alone.
     */
    private static int strictestMaximum(int running, int candidate) {
        if (candidate <= 0) {
            return running;
        }
        return running <= 0 ? candidate : Math.min(running, candidate);
    }

    /**
     * Maps each of {@code postFilter}'s wildcard "Exclude subreddits" terms, lower-cased, to the
     * filter's own name. Exact terms are left out: nothing records or excepts them, so carrying
     * them would only grow the map.
     */
    private static Map<String, String> subredditTermOwnersOf(PostFilter postFilter) {
        if (postFilter.excludeSubreddits == null || postFilter.excludeSubreddits.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> owners = new HashMap<>();
        for (String s : postFilter.excludeSubreddits.split(",", 0)) {
            String term = s.trim();
            if (!term.isEmpty() && SubredditMatcher.isWildcard(term)) {
                owners.putIfAbsent(term.toLowerCase(Locale.ENGLISH), postFilter.name);
            }
        }
        return owners;
    }

    /**
     * Key into {@link #wildcardExceptionKeys}. Uses NUL as the separator because a filter name is
     * free text and could otherwise collide with a more ordinary delimiter.
     */
    public static String exceptionKey(String filterName, String term, String subredditName) {
        return filterName.toLowerCase(Locale.ENGLISH) + '\u0000'
                + term.toLowerCase(Locale.ENGLISH) + '\u0000'
                + subredditName.toLowerCase(Locale.ENGLISH);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(name);
        parcel.writeInt(maxVote);
        parcel.writeInt(minVote);
        parcel.writeInt(maxComments);
        parcel.writeInt(minComments);
        parcel.writeInt(maxAwards);
        parcel.writeInt(minAwards);
        parcel.writeByte((byte) (allowNSFW ? 1 : 0));
        parcel.writeByte((byte) (onlyNSFW ? 1 : 0));
        parcel.writeByte((byte) (onlySpoiler ? 1 : 0));
        parcel.writeString(postTitleExcludesRegex);
        parcel.writeString(postTitleContainsRegex);
        parcel.writeString(postTitleExcludesStrings);
        parcel.writeString(postTitleContainsStrings);
        parcel.writeString(excludeSubreddits);
        parcel.writeString(containSubreddits);
        parcel.writeString(excludeUsers);
        parcel.writeString(containUsers);
        parcel.writeString(containFlairs);
        parcel.writeString(excludeFlairs);
        parcel.writeString(excludeDomains);
        parcel.writeString(containDomains);
        parcel.writeByte((byte) (containTextType ? 1 : 0));
        parcel.writeByte((byte) (containLinkType ? 1 : 0));
        parcel.writeByte((byte) (containImageType ? 1 : 0));
        parcel.writeByte((byte) (containGifType ? 1 : 0));
        parcel.writeByte((byte) (containVideoType ? 1 : 0));
        parcel.writeByte((byte) (containGalleryType ? 1 : 0));
        parcel.writeStringList(postTitleExcludesRegexes);
        parcel.writeStringList(postTitleContainsRegexes);
        parcel.writeByte((byte) (wildcardSubredditMatchingEnabled ? 1 : 0));
        parcel.writeInt(subredditTermOwners.size());
        for (Map.Entry<String, String> entry : subredditTermOwners.entrySet()) {
            parcel.writeString(entry.getKey());
            parcel.writeString(entry.getValue());
        }
    }
}