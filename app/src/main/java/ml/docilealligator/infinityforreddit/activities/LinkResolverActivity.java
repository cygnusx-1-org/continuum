package ml.docilealligator.infinityforreddit.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.URLUtil;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FilenameUtils;
import retrofit2.Retrofit;

public class LinkResolverActivity extends AppCompatActivity {

    public static final String EXTRA_MESSAGE_FULLNAME = "ENF";
    public static final String EXTRA_NEW_ACCOUNT_NAME = "ENAN";
    public static final String EXTRA_IS_NSFW = "EIN";
    public static final String EXTRA_SUBREDDIT_NAME = "ESN_LRA";
    public static final String EXTRA_POST_TITLE_KEY = "ET_LRA";

    private static final String POST_PATTERN = "/r/[\\w.-]+/comments/\\w+/?\\w+/?";
    private static final String POST_PATTERN_2 = "/(u|U|user)/[\\w-]+/comments/\\w+/?\\w+/?";
    private static final String POST_PATTERN_3 = "/[\\w-]+$";
    private static final String COMMENT_PATTERN = "/(r|u|U|user)/[\\w.-]+/comments/\\w+/?[\\w-]+/\\w+/?";
    // Optional listing-sort suffix that Reddit appends to a subreddit/multireddit URL,
    // e.g. /r/name/new or /r/a+b/top. Kept out of the captured subreddit name (see segments.get(1)).
    private static final String SUBREDDIT_SORT_SUFFIX = "(/(best|hot|new|rising|top|controversial))?";
    private static final String SUBREDDIT_PATTERN = "/[rR]/[\\w.-]+" + SUBREDDIT_SORT_SUFFIX + "/?";
    // A user profile, optionally with a "where" sub-listing (Reddit sorts users via ?sort=, not a path segment).
    private static final String USER_WHERE_SUFFIX = "(/(overview|submitted|posts|comments|saved|hidden|upvoted|downvoted|gilded|awards))?";
    private static final String USER_PATTERN = "/(u|U|user)/[\\w-]+" + USER_WHERE_SUFFIX + "/?";
    private static final String SHARELINK_SUBREDDIT_PATTERN = "/r/[\\w.-]+/s/[\\w-]+";
    private static final String SHARELINK_USER_PATTERN = "/u/[\\w-]+/s/[\\w-]+";
    // Subreddit-less post permalink shortcuts: /comments/<id>[/...] and /gallery/<id>
    // (Reddit 301s /gallery/<id> to /comments/<id>). The post id is the second path segment.
    private static final String POST_WITHOUT_SUBREDDIT_PATTERN = "/comments/\\w+(/[\\w-]+)*/?";
    private static final String REDDIT_GALLERY_PATTERN = "/gallery/\\w+/?";
    private static final String SIDEBAR_PATTERN = "/[rR]/[\\w.-]+/about/sidebar";
    private static final String MULTIREDDIT_PATTERN_2 = "/[rR]/(\\w+\\+?)+" + SUBREDDIT_SORT_SUFFIX + "/?";
    // A user's multireddit: /user/<name>/m/<multi> (or the /me/m/<multi> shortcut), optional sort suffix.
    private static final String MULTIREDDIT_USER_PATTERN = "/(u|U|user)/[\\w-]+/m/[\\w-]+" + SUBREDDIT_SORT_SUFFIX + "/?";
    private static final String MULTIREDDIT_ME_PATTERN = "/me/m/[\\w-]+" + SUBREDDIT_SORT_SUFFIX + "/?";
    private static final String REDD_IT_POST_PATTERN = "/\\w+/?";
    private static final String REDGIFS_PATTERN = "/watch/[\\w-]+$";
    private static final String IMGUR_GALLERY_PATTERN = "/gallery/\\w+/?";
    private static final String IMGUR_ALBUM_PATTERN = "/(album|a)/\\w+/?";
    private static final String IMGUR_IMAGE_PATTERN = "/\\w+/?";
    private static final String REDDIT_IMAGE_PATTERN =  "^/media$";
    private static final String WIKI_PATTERN = "/[rR]/[\\w.-]+/(wiki|w)(?:/[\\w-]+)*";
    private static final String GOOGLE_AMP_PATTERN = "/amp/s/amp.reddit.com/.*";
    private static final String STREAMABLE_PATTERN = "/\\w+/?";

    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;

    @Inject
    CustomThemeWrapper mCustomThemeWrapper;

    private Uri getRedditUriByPath(String path) {
        if (path.charAt(0) != '/') {
            return Uri.parse("https://www.reddit.com/" + path);
        } else {
            return Uri.parse("https://www.reddit.com" + path);
        }
    }

    /**
     * Threads a listing-sort suffix (e.g. /r/x/top or /user/x/m/y/top?t=week) through to the
     * target listing activity so it opens on the sort the link requested. {@code sortIndex} is
     * the path-segment index of the sort keyword. No-op when the URL carries no recognized sort
     * segment there. Sort segments are already constrained to the known set by the callers' patterns.
     */
    private void putInitialSort(Intent intent, List<String> segments, int sortIndex, Uri uri,
                                String typeExtraKey, String timeExtraKey) {
        if (segments.size() <= sortIndex) {
            return;
        }
        SortType.Type sortType = urlSegmentToSortType(segments.get(sortIndex));
        if (sortType == null) {
            return;
        }
        intent.putExtra(typeExtraKey, sortType.name());
        if (sortType == SortType.Type.TOP || sortType == SortType.Type.CONTROVERSIAL) {
            // Match the app's own default (ALL) for top/controversial when the link omits ?t=.
            SortType.Time sortTime = urlParamToSortTime(uri.getQueryParameter("t"));
            intent.putExtra(timeExtraKey, (sortTime != null ? sortTime : SortType.Time.ALL).name());
        }
    }

    /**
     * User pages carry their sort as ?sort=/?t= query params (not a path segment), so parse those
     * and thread them to {@link ViewUserDetailActivity}'s submitted tab. No-op when absent/unknown.
     */
    private void putUserQuerySort(Intent intent, Uri uri) {
        SortType.Type sortType = urlSegmentToSortType(uri.getQueryParameter("sort"));
        if (sortType == null) {
            return;
        }
        intent.putExtra(ViewUserDetailActivity.EXTRA_INITIAL_SORT_TYPE, sortType.name());
        if (sortType == SortType.Type.TOP || sortType == SortType.Type.CONTROVERSIAL) {
            SortType.Time sortTime = urlParamToSortTime(uri.getQueryParameter("t"));
            intent.putExtra(ViewUserDetailActivity.EXTRA_INITIAL_SORT_TIME,
                    (sortTime != null ? sortTime : SortType.Time.ALL).name());
        }
    }

    @Nullable
    private static SortType.Type urlSegmentToSortType(@Nullable String segment) {
        for (SortType.Type type : SortType.Type.values()) {
            if (type.value.equals(segment)) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    private static SortType.Time urlParamToSortTime(@Nullable String param) {
        if (param == null) {
            return null;
        }
        for (SortType.Time time : SortType.Time.values()) {
            if (time.value.equals(param)) {
                return time;
            }
        }
        return null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((Infinity) getApplication()).getAppComponent().inject(this);

        Uri uri = getIntent().getData();
        if (uri == null) {
            String url = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (!URLUtil.isValidUrl(url)) {
                Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            try {
                uri = Uri.parse(url);
            } catch (NullPointerException e) {
                Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        if (uri.getScheme() == null && uri.getHost() == null) {
            if (uri.toString().isEmpty()) {
                Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            handleUri(getRedditUriByPath(uri.toString()));
        } else {
            handleUri(uri);
        }
    }

    private void handleUri(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, R.string.no_link_available, Toast.LENGTH_SHORT).show();
        } else {
            String path = uri.getPath();
            if (path == null) {
                deepLinkError(uri);
            } else {
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }

                if (path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".jpeg")) {
                    Intent intent = new Intent(this, ViewImageOrGifActivity.class);
                    String url = uri.toString();
                    String fileName = FilenameUtils.getName(path);
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_IMAGE_URL_KEY, url);
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_FILE_NAME_KEY, fileName);
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_TITLE_KEY, fileName);
                    startActivity(intent);
                } else if (path.endsWith(".gif")) {
                    Intent intent = new Intent(this, ViewImageOrGifActivity.class);
                    String url = uri.toString();
                    String fileName = FilenameUtils.getName(path);
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_GIF_URL_KEY, url);
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_FILE_NAME_KEY, fileName);
                    intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_TITLE_KEY, fileName);
                    startActivity(intent);
                } else if (path.endsWith(".mp4")) {
                    Intent intent = new Intent(this, ViewVideoActivity.class);
                    intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_DIRECT);
                    intent.putExtra(ViewVideoActivity.EXTRA_IS_NSFW, getIntent().getBooleanExtra(EXTRA_IS_NSFW, false));
                    intent.setData(uri);
                    startActivity(intent);
                } else {
                    String messageFullname = getIntent().getStringExtra(EXTRA_MESSAGE_FULLNAME);
                    String newAccountName = getIntent().getStringExtra(EXTRA_NEW_ACCOUNT_NAME);

                    String authority = uri.getAuthority();
                    List<String> segments = uri.getPathSegments();

                    if (authority != null) {
                        if (authority.equals("sh.reddit.com")) {
                            deepLinkError(uri);
                        } else if (authority.equals("reddit-uploaded-media.s3-accelerate.amazonaws.com")) {
                            String unescapedUrl = uri.toString().replace("%2F", "/");
                            int lastSlashIndex = unescapedUrl.lastIndexOf("/");
                            if (lastSlashIndex < 0 || lastSlashIndex == unescapedUrl.length() - 1) {
                                deepLinkError(uri);
                                return;
                            }
                            String id = unescapedUrl.substring(lastSlashIndex + 1);
                            Intent intent = new Intent(this, ViewImageOrGifActivity.class);
                            intent.putExtra(ViewImageOrGifActivity.EXTRA_IMAGE_URL_KEY, uri.toString());
                            intent.putExtra(ViewImageOrGifActivity.EXTRA_FILE_NAME_KEY, id + ".jpg");
                            startActivity(intent);
                        } else if (authority.equals("v.redd.it")) {
                            Intent intent = new Intent(this, ViewVideoActivity.class);
                            intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_V_REDD_IT);
                            intent.putExtra(ViewVideoActivity.EXTRA_V_REDD_IT_URL, uri.toString());
                            startActivity(intent);
                        } else if (authority.contains("reddit.com") || authority.contains("redd.it") || authority.contains("reddit.app")) {
                            if (authority.equals("reddit.app.link") && path.isEmpty()) {
                                String redirect = uri.getQueryParameter("$og_redirect");
                                if (redirect != null) {
                                    handleUri(Uri.parse(redirect));
                                } else {
                                    deepLinkError(uri);
                                }
                            } else if (path.isEmpty()) {
                                Intent intent = new Intent(this, MainActivity.class);
                                startActivity(intent);
                            } else if (path.equals("/report")) {
                                openInWebView(uri);
                            } else if (path.matches(REDDIT_IMAGE_PATTERN)) {
                                // reddit.com/media, actual image url is stored in the "url" query param
                                try {
                                    Intent intent = new Intent(this, ViewImageOrGifActivity.class);
                                    String real_url = uri.getQueryParameter("url");
                                    Uri real_uri = Uri.parse(real_url);
                                    String fileName = FilenameUtils.getBaseName(real_uri.getPath());
                                    intent.putExtra(ViewImageOrGifActivity.EXTRA_IMAGE_URL_KEY, real_url);
                                    intent.putExtra(ViewImageOrGifActivity.EXTRA_FILE_NAME_KEY, fileName);
                                    intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_TITLE_KEY, fileName);
                                    startActivity(intent);
                                } catch (Exception e) {
                                    deepLinkError(uri);
                                }
                            } else if (path.matches(MULTIREDDIT_USER_PATTERN) || path.matches(MULTIREDDIT_ME_PATTERN)) {
                                // Multireddit: /user/<name>/m/<multi> or the /me/m/<multi> shortcut,
                                // optionally with a listing-sort suffix. Normalize both to /user/<owner>/m/<multi>.
                                boolean meForm = segments.get(0).equals("me");
                                int mIndex = meForm ? 1 : 2;
                                String multiOwner = meForm
                                        ? mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, "")
                                        : segments.get(1);
                                if (meForm && (multiOwner == null || multiOwner.isEmpty())) {
                                    // /me/ has no meaning without a signed-in account.
                                    deepLinkError(uri);
                                } else {
                                    String multiPath = "/user/" + multiOwner + "/m/" + segments.get(mIndex + 1);
                                    Intent intent = new Intent(this, ViewMultiRedditDetailActivity.class);
                                    intent.putExtra(ViewMultiRedditDetailActivity.EXTRA_MULTIREDDIT_PATH, multiPath);
                                    putInitialSort(intent, segments, mIndex + 2, uri,
                                            ViewMultiRedditDetailActivity.EXTRA_INITIAL_SORT_TYPE, ViewMultiRedditDetailActivity.EXTRA_INITIAL_SORT_TIME);
                                    startActivity(intent);
                                }
                            } else if (path.matches(POST_PATTERN) || path.matches(POST_PATTERN_2)) {
                                int commentsIndex = segments.lastIndexOf("comments");
                                if (commentsIndex >= 0 && commentsIndex < segments.size() - 1) {
                                    Intent intent = new Intent(this, ViewPostDetailActivity.class);
                                    intent.putExtra(ViewPostDetailActivity.EXTRA_POST_ID, segments.get(commentsIndex + 1));
                                    intent.putExtra(ViewPostDetailActivity.EXTRA_MESSAGE_FULLNAME, messageFullname);
                                    intent.putExtra(ViewPostDetailActivity.EXTRA_NEW_ACCOUNT_NAME, newAccountName);
                                    startActivity(intent);
                                } else {
                                    deepLinkError(uri);
                                }
                            } else if (path.matches(POST_PATTERN_3)) {
                                Intent intent = new Intent(this, ViewPostDetailActivity.class);
                                intent.putExtra(ViewPostDetailActivity.EXTRA_POST_ID, path.substring(1));
                                intent.putExtra(ViewPostDetailActivity.EXTRA_MESSAGE_FULLNAME, messageFullname);
                                intent.putExtra(ViewPostDetailActivity.EXTRA_NEW_ACCOUNT_NAME, newAccountName);
                                startActivity(intent);
                            } else if (path.matches(COMMENT_PATTERN)) {
                                int commentsIndex = segments.lastIndexOf("comments");
                                if (commentsIndex >= 0 && commentsIndex < segments.size() - 1) {
                                    Intent intent = new Intent(this, ViewPostDetailActivity.class);
                                    intent.putExtra(ViewPostDetailActivity.EXTRA_POST_ID, segments.get(commentsIndex + 1));
                                    intent.putExtra(ViewPostDetailActivity.EXTRA_SINGLE_COMMENT_ID, segments.get(segments.size() - 1));
                                    intent.putExtra(ViewPostDetailActivity.EXTRA_MESSAGE_FULLNAME, messageFullname);
                                    intent.putExtra(ViewPostDetailActivity.EXTRA_NEW_ACCOUNT_NAME, newAccountName);
                                    startActivity(intent);
                                } else {
                                    deepLinkError(uri);
                                }
                            } else if (path.matches(POST_WITHOUT_SUBREDDIT_PATTERN) || path.matches(REDDIT_GALLERY_PATTERN)) {
                                // /comments/<id>[/<slug>[/<comment id>]] and /gallery/<id> — post
                                // permalinks with no subreddit segment. The single-comment form is
                                // exactly id/slug/commentId (mirrors COMMENT_PATTERN); shorter forms open
                                // the post, and Reddit produces nothing deeper.
                                Intent intent = new Intent(this, ViewPostDetailActivity.class);
                                intent.putExtra(ViewPostDetailActivity.EXTRA_POST_ID, segments.get(1));
                                if (segments.size() == 4) {
                                    intent.putExtra(ViewPostDetailActivity.EXTRA_SINGLE_COMMENT_ID, segments.get(3));
                                }
                                intent.putExtra(ViewPostDetailActivity.EXTRA_MESSAGE_FULLNAME, messageFullname);
                                intent.putExtra(ViewPostDetailActivity.EXTRA_NEW_ACCOUNT_NAME, newAccountName);
                                startActivity(intent);
                            } else if (path.matches(WIKI_PATTERN)) {
                                String[] pathSegments = path.split("/");
                                String wikiPage;
                                if (pathSegments.length == 4) {
                                    wikiPage = "index";
                                } else {
                                    int lengthThroughWiki = 0;
                                    for (int i = 1; i <= 3; ++i) {
                                        lengthThroughWiki += pathSegments[i].length() + 1;
                                    }
                                    wikiPage = path.substring(lengthThroughWiki);
                                }
                                Intent intent = new Intent(this, WikiActivity.class);
                                intent.putExtra(WikiActivity.EXTRA_SUBREDDIT_NAME, segments.get(1));
                                intent.putExtra(WikiActivity.EXTRA_WIKI_PATH, wikiPage);
                                startActivity(intent);
                            } else if (path.matches(SUBREDDIT_PATTERN)) {
                                Intent intent = new Intent(this, ViewSubredditDetailActivity.class);
                                intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, segments.get(1));
                                intent.putExtra(ViewSubredditDetailActivity.EXTRA_MESSAGE_FULLNAME, messageFullname);
                                intent.putExtra(ViewSubredditDetailActivity.EXTRA_NEW_ACCOUNT_NAME, newAccountName);
                                putInitialSort(intent, segments, 2, uri,
                                        ViewSubredditDetailActivity.EXTRA_INITIAL_SORT_TYPE, ViewSubredditDetailActivity.EXTRA_INITIAL_SORT_TIME);
                                startActivity(intent);
                            } else if (path.matches(USER_PATTERN)) {
                                String userName = segments.get(1);
                                String where = segments.size() > 2 ? segments.get(2) : null;
                                String currentAccount = Objects.requireNonNull(mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, ""));
                                boolean loggedIn = currentAccount != null && !currentAccount.isEmpty();
                                boolean isSelf = loggedIn
                                        && (currentAccount.equalsIgnoreCase(userName) || userName.equalsIgnoreCase("me"));
                                if (isSelf && ("upvoted".equals(where) || "downvoted".equals(where) || "hidden".equals(where))) {
                                    // Private account listings — only meaningful for the signed-in user.
                                    Intent intent = new Intent(this, AccountPostsActivity.class);
                                    intent.putExtra(AccountPostsActivity.EXTRA_USER_WHERE, where);
                                    startActivity(intent);
                                } else {
                                    Intent intent = new Intent(this, ViewUserDetailActivity.class);
                                    intent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, userName);
                                    intent.putExtra(ViewUserDetailActivity.EXTRA_MESSAGE_FULLNAME, messageFullname);
                                    intent.putExtra(ViewUserDetailActivity.EXTRA_NEW_ACCOUNT_NAME, newAccountName);
                                    if ("comments".equals(where)) {
                                        intent.putExtra(ViewUserDetailActivity.EXTRA_INITIAL_TAB, ViewUserDetailActivity.TAB_COMMENTS);
                                    }
                                    // Both the submitted and comments tabs honor ?sort=/?t=; the activity
                                    // applies it to whichever tab the link targets.
                                    putUserQuerySort(intent, uri);
                                    startActivity(intent);
                                }
                            } else if (path.matches(SIDEBAR_PATTERN)) {
                                Intent intent = new Intent(this, ViewSubredditDetailActivity.class);
                                intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, path.substring(3, path.length() - 14));
                                intent.putExtra(ViewSubredditDetailActivity.EXTRA_VIEW_SIDEBAR, true);
                                startActivity(intent);
                            } else if (path.matches(MULTIREDDIT_PATTERN_2)) {
                                String subredditName = segments.get(1);
                                Intent intent = new Intent(this, ViewSubredditDetailActivity.class);
                                intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, subredditName);
                                intent.putExtra(ViewSubredditDetailActivity.EXTRA_MESSAGE_FULLNAME, messageFullname);
                                intent.putExtra(ViewSubredditDetailActivity.EXTRA_NEW_ACCOUNT_NAME, newAccountName);
                                putInitialSort(intent, segments, 2, uri,
                                        ViewSubredditDetailActivity.EXTRA_INITIAL_SORT_TYPE, ViewSubredditDetailActivity.EXTRA_INITIAL_SORT_TIME);
                                startActivity(intent);
                            } else if (authority.equals("redd.it") && path.matches(REDD_IT_POST_PATTERN)) {
                                Intent intent = new Intent(this, ViewPostDetailActivity.class);
                                intent.putExtra(ViewPostDetailActivity.EXTRA_POST_ID, path.substring(1));
                                startActivity(intent);
                            } else if (path.matches(SHARELINK_SUBREDDIT_PATTERN)
                                    || path.matches(SHARELINK_USER_PATTERN)) {
                                mRetrofit.callFactory().newCall(new Request.Builder().url(uri.toString()).build()).enqueue(new Callback() {
                                    @Override
                                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                                        if (response.isSuccessful()) {
                                            Uri newUri = Uri.parse(response.request().url().toString());
                                            if (newUri.getPath() != null) {
                                                if (newUri.getPath().matches(SHARELINK_SUBREDDIT_PATTERN)
                                                        || newUri.getPath().matches(SHARELINK_USER_PATTERN)) {
                                                    deepLinkError(newUri);
                                                } else {
                                                    handleUri(newUri);
                                                }
                                            } else {
                                                handleUri(uri);
                                            }
                                        } else {
                                            deepLinkError(uri);
                                        }
                                    }

                                    @Override
                                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                        deepLinkError(uri);
                                    }
                                });
                            } else {
                                deepLinkError(uri);
                            }
                        } else if (authority.equals("click.redditmail.com")) {
                            if (path.startsWith("/CL0/")) {
                                handleUri(Uri.parse(path.substring("/CL0/".length())));
                            }
                        } else if (authority.contains("redgifs.com")) {
                            if (path.matches(REDGIFS_PATTERN)) {
                                Intent intent = new Intent(this, ViewVideoActivity.class);
                                intent.putExtra(ViewVideoActivity.EXTRA_REDGIFS_ID, path.substring(path.lastIndexOf("/") + 1));
                                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_REDGIFS);
                                intent.putExtra(ViewVideoActivity.EXTRA_IS_NSFW, true);
                                startActivity(intent);
                            } else {
                                deepLinkError(uri);
                            }
                        } else if (authority.contains("imgur.com")) {
                            if (path.matches(IMGUR_GALLERY_PATTERN)) {
                                Intent intent = new Intent(this, ViewImgurMediaActivity.class);
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_IMGUR_TYPE, ViewImgurMediaActivity.IMGUR_TYPE_GALLERY);
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_IMGUR_ID, segments.get(1));
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_SUBREDDIT_NAME, getIntent().getStringExtra(EXTRA_SUBREDDIT_NAME));
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_IS_NSFW, getIntent().getBooleanExtra(EXTRA_IS_NSFW, false));
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_POST_TITLE_KEY, getIntent().getStringExtra(EXTRA_POST_TITLE_KEY));
                                startActivity(intent);
                            } else if (path.matches(IMGUR_ALBUM_PATTERN)) {
                                Intent intent = new Intent(this, ViewImgurMediaActivity.class);
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_IMGUR_TYPE, ViewImgurMediaActivity.IMGUR_TYPE_ALBUM);
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_IMGUR_ID, segments.get(1));
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_SUBREDDIT_NAME, getIntent().getStringExtra(EXTRA_SUBREDDIT_NAME));
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_IS_NSFW, getIntent().getBooleanExtra(EXTRA_IS_NSFW, false));
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_POST_TITLE_KEY, getIntent().getStringExtra(EXTRA_POST_TITLE_KEY));
                                startActivity(intent);
                            } else if (path.matches(IMGUR_IMAGE_PATTERN)) {
                                Intent intent = new Intent(this, ViewImgurMediaActivity.class);
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_IMGUR_TYPE, ViewImgurMediaActivity.IMGUR_TYPE_IMAGE);
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_IMGUR_ID, path.substring(1));
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_SUBREDDIT_NAME, getIntent().getStringExtra(EXTRA_SUBREDDIT_NAME));
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_IS_NSFW, getIntent().getBooleanExtra(EXTRA_IS_NSFW, false));
                                intent.putExtra(ViewImgurMediaActivity.EXTRA_POST_TITLE_KEY, getIntent().getStringExtra(EXTRA_POST_TITLE_KEY));
                                startActivity(intent);
                            } else if (path.endsWith("gifv") || path.endsWith("mp4")) {
                                String url = uri.toString();
                                if (path.endsWith("gifv")) {
                                    url = url.substring(0, url.length() - 5) + ".mp4";
                                }
                                Intent intent = new Intent(this, ViewVideoActivity.class);
                                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_IMGUR);
                                intent.putExtra(ViewVideoActivity.EXTRA_IS_NSFW, getIntent().getBooleanExtra(EXTRA_IS_NSFW, false));
                                intent.setData(Uri.parse(url));
                                startActivity(intent);
                            } else {
                                deepLinkError(uri);
                            }
                        } else if (authority.contains("google.com")) {
                            if (path.matches(GOOGLE_AMP_PATTERN)) {
                                String url = path.substring(11);
                                handleUri(Uri.parse("https://" + url));
                            } else {
                                deepLinkError(uri);
                            }
                        } else if (authority.equals("streamable.com")) {
                            if (path.matches(STREAMABLE_PATTERN)) {
                                String shortCode = segments.get(0);
                                Intent intent = new Intent(this, ViewVideoActivity.class);
                                intent.putExtra(ViewVideoActivity.EXTRA_VIDEO_TYPE, ViewVideoActivity.VIDEO_TYPE_STREAMABLE);
                                intent.putExtra(ViewVideoActivity.EXTRA_STREAMABLE_SHORT_CODE, shortCode);
                                startActivity(intent);
                            } else {
                                deepLinkError(uri);
                            }
                        } else {
                            deepLinkError(uri);
                        }
                    } else {
                        deepLinkError(uri);
                    }
                }
            }

        }
        finish();
    }

    private void deepLinkError(Uri uri) {
        PackageManager pm = getPackageManager();

        String authority = uri.getAuthority();
        if(authority != null && (authority.contains("reddit.com") || authority.contains("redd.it") || authority.contains("reddit.app.link"))) {
            openInCustomTabs(uri, pm, false, false);
            return;
        }

        String accountName = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, "");
        String linkHandlerKey = (accountName != null && !accountName.isEmpty())
                ? accountName + SharedPreferencesUtils.LINK_HANDLER_BASE
                : SharedPreferencesUtils.LINK_HANDLER;
        int linkHandler = Integer.parseInt(mSharedPreferences.getString(linkHandlerKey, "0"));
        if (linkHandler == 0) {
            openInBrowser(uri, pm, true);
        } else if (linkHandler == 1) {
            openInCustomTabs(uri, pm, true, false);
        } else if (linkHandler == 3) {
            openInCustomTabs(uri, pm, true, true);
        } else if (linkHandler == 4) {
            openInSpecificBrowser(uri, pm);
        } else {
            openInWebView(uri);
        }
    }

    private void openInSpecificBrowser(Uri uri, PackageManager pm) {
        String accountName = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, "");
        String pkgKey = (accountName != null && !accountName.isEmpty())
                ? accountName + SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE_BASE
                : SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE;
        String pkg = mSharedPreferences.getString(pkgKey, "");
        if (pkg != null && !pkg.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(uri);
            intent.setPackage(pkg);
            try {
                startActivity(intent);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        openInBrowser(uri, pm, true);
    }

    private void openInBrowser(Uri uri, PackageManager pm, boolean handleError) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            if (handleError) {
                openInCustomTabs(uri, pm, false, false);
            } else {
                openInWebView(uri);
            }
        }
    }

    private ArrayList<ResolveInfo> getCustomTabsPackages(PackageManager pm) {
        // Get default VIEW intent handler.
        Intent activityIntent = new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.fromParts("http", "", null));

        // Get all apps that can handle VIEW intents.
        List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, 0);
        ArrayList<ResolveInfo> packagesSupportingCustomTabs = new ArrayList<>();
        for (ResolveInfo info : resolvedActivityList) {
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(info.activityInfo.packageName);
            // Check if this package also resolves the Custom Tabs service.
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(info);
            }
        }
        return packagesSupportingCustomTabs;
    }

    private void openInCustomTabs(Uri uri, PackageManager pm, boolean handleError, boolean ephemeral) {
        String selectedPackage = null;
        if (ephemeral) {
            String accountName = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, "");
            String ephemeralPkgKey = (accountName != null && !accountName.isEmpty())
                    ? accountName + SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE_BASE
                    : SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE;
            String preferredPackage = mSharedPreferences.getString(ephemeralPkgKey, "");
            if (preferredPackage != null && !preferredPackage.isEmpty()
                    && CustomTabsClient.isEphemeralBrowsingSupported(this, preferredPackage)) {
                selectedPackage = preferredPackage;
            } else {
                // queryIntentActivities for http only returns the default browser
                // on API 30+, so enumerate Custom Tabs services directly to find
                // any installed browser that declares ephemeral support.
                Intent svcQuery = new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
                for (ResolveInfo info : pm.queryIntentServices(svcQuery, 0)) {
                    if (info.serviceInfo == null) continue;
                    String pkg = info.serviceInfo.packageName;
                    if (CustomTabsClient.isEphemeralBrowsingSupported(this, pkg)) {
                        selectedPackage = pkg;
                        break;
                    }
                }
            }
            if (selectedPackage == null) {
                // No installed Custom Tabs provider supports ephemeral browsing.
                // Fall back to the internal WebView, which is closer in privacy
                // posture than reusing the system browser session.
                openInWebView(uri);
                return;
            }
        } else {
            ArrayList<ResolveInfo> resolveInfos = getCustomTabsPackages(pm);
            if (!resolveInfos.isEmpty()) {
                selectedPackage = resolveInfos.get(0).activityInfo.packageName;
            }
        }
        if (selectedPackage != null) {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            // add share action to menu list
            builder.setShareState(CustomTabsIntent.SHARE_STATE_ON);
            builder.setDefaultColorSchemeParams(
                    new CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(mCustomThemeWrapper.getColorPrimary())
                            .build());
            if (ephemeral) {
                builder.setEphemeralBrowsingEnabled(true);
            }
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.intent.setPackage(selectedPackage);
            if (uri.getScheme() == null) {
                uri = Uri.parse("http://" + uri);
            }
            try {
                customTabsIntent.launchUrl(this, uri);
            } catch (ActivityNotFoundException e) {
                if (handleError) {
                    openInBrowser(uri, pm, false);
                } else {
                    openInWebView(uri);
                }
            }
        } else {
            if (handleError) {
                openInBrowser(uri, pm, false);
            } else {
                openInWebView(uri);
            }
        }
    }

    private void openInWebView(Uri uri) {
        Intent intent = new Intent(this, WebViewActivity.class);
        intent.setData(uri);
        startActivity(intent);
    }
}
