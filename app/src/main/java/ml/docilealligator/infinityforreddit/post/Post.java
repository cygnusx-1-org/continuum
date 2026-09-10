package ml.docilealligator.infinityforreddit.post;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import ml.docilealligator.infinityforreddit.thing.MediaMetadata;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.ShortClipHostUtils;

/**
 * Created by alex on 3/1/18.
 */

public class Post implements Parcelable {
    public static final int NSFW_TYPE = -1;
    public static final int TEXT_TYPE = 0;
    public static final int IMAGE_TYPE = 1;
    public static final int LINK_TYPE = 2;
    public static final int VIDEO_TYPE = 3;
    public static final int GIF_TYPE = 4;
    public static final int NO_PREVIEW_LINK_TYPE = 5;
    public static final int GALLERY_TYPE = 6;

    private final String id;
    private final String fullName;
    private final String subredditName;
    private final String subredditNamePrefixed;
    @Nullable
    private String subredditIconUrl;
    private String author;
    @Nullable
    private String authorFullname;
    private String authorNamePrefixed;
    @Nullable
    private String authorIconUrl;
    private String authorFlair;
    private String authorFlairHTML;
    private String title;
    @Nullable
    private String selfText;
    @Nullable
    private String selfTextPlain;
    @Nullable
    private String selfTextPlainTrimmed;
    @Nullable
    private String url;
    @Nullable
    private String videoUrl;
    @Nullable
    private String videoDownloadUrl;
    @Nullable
    private String videoFallBackDirectUrl;
    @Nullable
    private String thumbnailUrl;
    @Nullable
    private String redgifsId;
    @Nullable
    private String streamableShortCode;
    /** A {@link ShortClipHostUtils.Host} name, or null when the post is not on a clip host. */
    @Nullable
    private String shortClipHost;
    @Nullable
    private String shortClipId;
    private boolean isImgur;
    private boolean isRedgifs;
    private boolean isStreamable;
    private boolean isTumblr;
    private boolean isMlbClip;
    private boolean loadedStreamableVideoAlready;
    private final String permalink;
    private String flair;
    private final long postTimeMillis;
    private long editedTimeMillis;
    private int score;
    private int postType;
    private int voteType;
    private int nComments;
    private int upvoteRatio;
    private boolean hidden;
    private boolean spoiler;
    private boolean nsfw;
    private boolean stickied;
    private final boolean archived;
    private boolean locked;
    private boolean saved;
    private boolean sendReplies;
    private final boolean isCrosspost;
    private boolean isRead;
    /**
     * Which image of a gallery post the user last swiped to in the feed, 0 for the first.
     *
     * <p>Kept on the post rather than on the view holder because the holder is recycled: scrolling
     * a gallery post off screen and back used to bring it back on image one. It rides the feed
     * cache too, so "Resume where I left off" reopens the gallery on the image that was showing.
     */
    private int galleryPageIndex;
    /** Set when this post's content came back from the archive rather than from Reddit. */
    private boolean isRecovered;
    @Nullable
    private String crosspostParentId;
    @Nullable
    private String distinguished;
    @Nullable
    private final String suggestedSort;
    @Nullable
    private String mp4Variant;
    private ArrayList<Preview> previews = new ArrayList<>();
    @Nullable
    private Map<String, MediaMetadata> mediaMetadataMap;
    private ArrayList<Gallery> gallery = new ArrayList<>();
    private boolean canModPost;
    private boolean approved;
    private long approvedAtUTC;
    @Nullable
    private String approvedBy;
    private boolean removed;
    private boolean spam;

    //Text and video posts
    public Post(String id, String fullName, String subredditName, String subredditNamePrefixed,
                String author, @Nullable String authorFullname, String authorFlair, String authorFlairHTML, long postTimeMillis,
                String title, String permalink, int score, int postType, int voteType, int nComments,
                int upvoteRatio, String flair, boolean hidden, boolean spoiler,
                boolean nsfw, boolean stickied, boolean archived, boolean locked, boolean saved, boolean sendReplies,
                boolean isCrosspost, boolean canModPost, boolean approved, long approvedAtUTC, @Nullable String approvedBy,
                boolean removed, boolean spam, String distinguished, @Nullable String suggestedSort) {
        this.id = id;
        this.fullName = fullName;
        this.subredditName = subredditName;
        this.subredditNamePrefixed = subredditNamePrefixed;
        this.author = author;
        this.authorFullname = authorFullname;
        this.authorNamePrefixed = "u/" + author;
        this.authorFlair = authorFlair;
        this.authorFlairHTML = authorFlairHTML;
        this.postTimeMillis = postTimeMillis;
        this.title = title;
        this.permalink = APIUtils.API_BASE_URI + permalink;
        this.score = score;
        this.postType = postType;
        this.voteType = voteType;
        this.nComments = nComments;
        this.upvoteRatio = upvoteRatio;
        this.flair = flair;
        this.hidden = hidden;
        this.spoiler = spoiler;
        this.nsfw = nsfw;
        this.stickied = stickied;
        this.archived = archived;
        this.locked = locked;
        this.saved = saved;
        this.sendReplies = sendReplies;
        this.isCrosspost = isCrosspost;
        this.canModPost = canModPost;
        this.approved = approved;
        this.approvedAtUTC = approvedAtUTC;
        this.approvedBy = approvedBy;
        this.removed = removed;
        this.spam = spam;
        this.distinguished = distinguished;
        this.suggestedSort = suggestedSort;
        isRead = false;
        isRecovered = false;
    }

    public Post(String id, String fullName, String subredditName, String subredditNamePrefixed,
                String author, @Nullable String authorFullname, String authorFlair, String authorFlairHTML, long postTimeMillis, String title,
                String url, String permalink, int score, int postType, int voteType, int nComments,
                int upvoteRatio, String flair, boolean hidden, boolean spoiler,
                boolean nsfw, boolean stickied, boolean archived, boolean locked, boolean saved, boolean sendReplies,
                boolean isCrosspost, boolean canModPost, boolean approved, long approvedAtUTC, @Nullable String approvedBy,
                boolean removed, boolean spam, String distinguished, @Nullable String suggestedSort) {
        this.id = id;
        this.fullName = fullName;
        this.subredditName = subredditName;
        this.subredditNamePrefixed = subredditNamePrefixed;
        this.author = author;
        this.authorFullname = authorFullname;
        this.authorNamePrefixed = "u/" + author;
        this.authorFlair = authorFlair;
        this.authorFlairHTML = authorFlairHTML;
        this.postTimeMillis = postTimeMillis;
        this.title = title;
        this.url = url;
        this.permalink = APIUtils.API_BASE_URI + permalink;
        this.score = score;
        this.postType = postType;
        this.voteType = voteType;
        this.nComments = nComments;
        this.upvoteRatio = upvoteRatio;
        this.flair = flair;
        this.hidden = hidden;
        this.spoiler = spoiler;
        this.nsfw = nsfw;
        this.stickied = stickied;
        this.archived = archived;
        this.locked = locked;
        this.saved = saved;
        this.sendReplies = sendReplies;
        this.isCrosspost = isCrosspost;
        this.canModPost = canModPost;
        this.approved = approved;
        this.approvedAtUTC = approvedAtUTC;
        this.approvedBy = approvedBy;
        this.removed = removed;
        this.spam = spam;
        this.distinguished = distinguished;
        this.suggestedSort = suggestedSort;
        isRead = false;
        isRecovered = false;
    }

    public Post(@NonNull Post postToBeCopied) {
        this.id = postToBeCopied.id;
        this.fullName = postToBeCopied.fullName;
        this.subredditName = postToBeCopied.subredditName;
        this.subredditNamePrefixed = postToBeCopied.subredditNamePrefixed;
        this.subredditIconUrl = postToBeCopied.subredditIconUrl;
        this.author = postToBeCopied.author;
        this.authorFullname = postToBeCopied.authorFullname;
        this.authorNamePrefixed = postToBeCopied.authorNamePrefixed;
        this.authorIconUrl = postToBeCopied.authorIconUrl;
        this.authorFlair = postToBeCopied.authorFlair;
        this.authorFlairHTML = postToBeCopied.authorFlairHTML;
        this.title = postToBeCopied.title;
        this.selfText = postToBeCopied.selfText;
        this.selfTextPlain = postToBeCopied.selfTextPlain;
        this.selfTextPlainTrimmed = postToBeCopied.selfTextPlainTrimmed;
        this.url = postToBeCopied.url;
        this.videoUrl = postToBeCopied.videoUrl;
        this.videoDownloadUrl = postToBeCopied.videoDownloadUrl;
        this.videoFallBackDirectUrl = postToBeCopied.videoFallBackDirectUrl;
        this.thumbnailUrl = postToBeCopied.thumbnailUrl;
        this.redgifsId = postToBeCopied.redgifsId;
        this.streamableShortCode = postToBeCopied.streamableShortCode;
        this.shortClipHost = postToBeCopied.shortClipHost;
        this.shortClipId = postToBeCopied.shortClipId;
        this.isImgur = postToBeCopied.isImgur;
        this.isRedgifs = postToBeCopied.isRedgifs;
        this.isStreamable = postToBeCopied.isStreamable;
        this.isTumblr = postToBeCopied.isTumblr;
        this.isMlbClip = postToBeCopied.isMlbClip;
        this.loadedStreamableVideoAlready = postToBeCopied.loadedStreamableVideoAlready;
        this.permalink = postToBeCopied.permalink;
        this.flair = postToBeCopied.flair;
        this.postTimeMillis = postToBeCopied.postTimeMillis;
        this.editedTimeMillis = postToBeCopied.editedTimeMillis;
        this.score = postToBeCopied.score;
        this.postType = postToBeCopied.postType;
        this.voteType = postToBeCopied.voteType;
        this.nComments = postToBeCopied.nComments;
        this.upvoteRatio = postToBeCopied.upvoteRatio;
        this.hidden = postToBeCopied.hidden;
        this.spoiler = postToBeCopied.spoiler;
        this.nsfw = postToBeCopied.nsfw;
        this.stickied = postToBeCopied.stickied;
        this.archived = postToBeCopied.archived;
        this.locked = postToBeCopied.locked;
        this.saved = postToBeCopied.saved;
        this.sendReplies = postToBeCopied.sendReplies;
        this.isCrosspost = postToBeCopied.isCrosspost;
        this.isRead = postToBeCopied.isRead;
        this.galleryPageIndex = postToBeCopied.galleryPageIndex;
        this.isRecovered = postToBeCopied.isRecovered;
        this.crosspostParentId = postToBeCopied.crosspostParentId;
        this.distinguished = postToBeCopied.distinguished;
        this.suggestedSort = postToBeCopied.suggestedSort;
        this.mp4Variant = postToBeCopied.mp4Variant;
        this.previews = postToBeCopied.previews;
        this.mediaMetadataMap = postToBeCopied.mediaMetadataMap;
        this.gallery = postToBeCopied.gallery;
        this.canModPost = postToBeCopied.canModPost;
        this.approved = postToBeCopied.approved;
        this.approvedAtUTC = postToBeCopied.approvedAtUTC;
        this.approvedBy = postToBeCopied.approvedBy;
        this.removed = postToBeCopied.removed;
        this.spam = postToBeCopied.spam;
    }

    protected Post(Parcel in) {
        id = Objects.requireNonNull(in.readString());
        fullName = Objects.requireNonNull(in.readString());
        subredditName = Objects.requireNonNull(in.readString());
        subredditNamePrefixed = Objects.requireNonNull(in.readString());
        subredditIconUrl = in.readString();
        author = Objects.requireNonNull(in.readString());
        authorFullname = in.readString();
        authorNamePrefixed = Objects.requireNonNull(in.readString());
        authorIconUrl = in.readString();
        authorFlair = Objects.requireNonNull(in.readString());
        authorFlairHTML = Objects.requireNonNull(in.readString());
        title = Objects.requireNonNull(in.readString());
        selfText = in.readString();
        selfTextPlain = in.readString();
        selfTextPlainTrimmed = in.readString();
        url = in.readString();
        videoUrl = in.readString();
        videoDownloadUrl = in.readString();
        videoFallBackDirectUrl = in.readString();
        thumbnailUrl = in.readString();
        redgifsId = in.readString();
        streamableShortCode = in.readString();
        shortClipHost = in.readString();
        shortClipId = in.readString();
        isImgur = in.readByte() != 0;
        isRedgifs = in.readByte() != 0;
        isStreamable = in.readByte() != 0;
        isTumblr = in.readByte() != 0;
        isMlbClip = in.readByte() != 0;
        loadedStreamableVideoAlready = in.readByte() != 0;
        permalink = Objects.requireNonNull(in.readString());
        flair = Objects.requireNonNull(in.readString());
        postTimeMillis = in.readLong();
        editedTimeMillis = in.readLong();
        score = in.readInt();
        postType = in.readInt();
        voteType = in.readInt();
        nComments = in.readInt();
        upvoteRatio = in.readInt();
        hidden = in.readByte() != 0;
        spoiler = in.readByte() != 0;
        nsfw = in.readByte() != 0;
        stickied = in.readByte() != 0;
        archived = in.readByte() != 0;
        locked = in.readByte() != 0;
        saved = in.readByte() != 0;
        sendReplies = in.readByte() != 0;
        isCrosspost = in.readByte() != 0;
        canModPost = in.readByte() != 0;
        approved = in.readByte() != 0;
        approvedAtUTC = in.readLong();
        approvedBy = in.readString();
        removed = in.readByte() != 0;
        spam = in.readByte() != 0;
        isRead = in.readByte() != 0;
        isRecovered = in.readByte() != 0;
        crosspostParentId = in.readString();
        distinguished = in.readString();
        suggestedSort = in.readString();
        mp4Variant = in.readString();
        previews = Objects.requireNonNull(in.createTypedArrayList(Preview.CREATOR));
        mediaMetadataMap = (Map<String, MediaMetadata>) in.readValue(getClass().getClassLoader());
        ArrayList<Gallery> parsedGallery = in.createTypedArrayList(Gallery.CREATOR);
        gallery = parsedGallery != null ? parsedGallery : new ArrayList<>();
        galleryPageIndex = in.readInt();
    }

    public static final Creator<Post> CREATOR = new Creator<Post>() {
        @Override
        public Post createFromParcel(Parcel in) {
            return new Post(in);
        }

        @Override
        public Post[] newArray(int size) {
            return new Post[size];
        }
    };

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getSubredditName() {
        return subredditName;
    }

    public String getSubredditNamePrefixed() {
        return subredditNamePrefixed;
    }

    @Nullable
    public String getSubredditIconUrl() {
        return subredditIconUrl;
    }

    public void setSubredditIconUrl(@Nullable String subredditIconUrl) {
        this.subredditIconUrl = subredditIconUrl;
    }

    public String getAuthor() {
        return author;
    }

    // Will be null or empty if the post is deleted
    @Nullable
    public String getAuthorFullname() {
        return authorFullname;
    }

    public boolean isAuthorDeleted() {
        return author != null && author.equals("[deleted]");
    }

    public void setAuthor(String author) {
        this.author = author;
        this.authorNamePrefixed = "u/" + author;
    }

    public String getAuthorNamePrefixed() {
        return authorNamePrefixed;
    }

    public String getAuthorFlair() {
        return authorFlair;
    }

    public void setAuthorFlair(String authorFlair) {
        this.authorFlair = authorFlair;
    }

    public String getAuthorFlairHTML() {
        return authorFlairHTML;
    }

    public void setAuthorFlairHTML(String authorFlairHTML) {
        this.authorFlairHTML = authorFlairHTML;
    }

    @Nullable
    public String getAuthorIconUrl() {
        return authorIconUrl;
    }

    public void setAuthorIconUrl(@Nullable String authorIconUrl) {
        this.authorIconUrl = authorIconUrl;
    }

    public long getPostTimeMillis() {
        return postTimeMillis;
    }

    /**
     * Reddit sends {@code edited} as {@code false} when a post was never edited and as the edit
     * timestamp otherwise, so a non-zero value is the flag. Note Reddit suppresses it entirely for
     * edits made within a few minutes of posting -- see the same grace period on comments.
     */
    public boolean isEdited() {
        return editedTimeMillis != 0;
    }

    public long getEditedTimeMillis() {
        return editedTimeMillis;
    }

    public void setEditedTimeMillis(long editedTimeMillis) {
        this.editedTimeMillis = editedTimeMillis;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Nullable
    public String getSelfText() {
        return selfText;
    }

    public void setSelfText(@Nullable String selfText) {
        this.selfText = selfText;
    }

    @Nullable
    public String getSelfTextPlain() {
        return selfTextPlain;
    }

    public void setSelfTextPlain(@Nullable String selfTextPlain) {
        this.selfTextPlain = selfTextPlain;
    }

    @Nullable
    public String getSelfTextPlainTrimmed() {
        return selfTextPlainTrimmed;
    }

    public void setSelfTextPlainTrimmed(@Nullable String selfTextPlainTrimmed) {
        this.selfTextPlainTrimmed = selfTextPlainTrimmed;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    @Nullable
    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(@Nullable String videoUrl) {
        this.videoUrl = videoUrl;
    }

    @Nullable
    public String getVideoDownloadUrl() {
        return videoDownloadUrl;
    }

    public void setVideoDownloadUrl(@Nullable String videoDownloadUrl) {
        this.videoDownloadUrl = videoDownloadUrl;
    }

    @Nullable
    public String getVideoFallBackDirectUrl() {
        return videoFallBackDirectUrl;
    }

    public void setVideoFallBackDirectUrl(@Nullable String videoFallBackDirectUrl) {
        this.videoFallBackDirectUrl = videoFallBackDirectUrl;
    }

    @Nullable
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(@Nullable String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    @Nullable
    public String getRedgifsId() {
        return redgifsId;
    }

    public void setRedgifsId(@Nullable String redgifsId) {
        this.redgifsId = redgifsId;
    }

    @Nullable
    public String getStreamableShortCode() {
        return streamableShortCode;
    }

    public void setStreamableShortCode(@Nullable String shortCode) {
        this.streamableShortCode = shortCode;
    }

    /**
     * The {@link ShortClipHostUtils.Host} this post's link belongs to, by name, or null when it is
     * not on one of those hosts.
     *
     * <p>Stored as a name rather than an ordinal so a reordered enum cannot silently re-point a
     * parcelled post at the wrong host.
     */
    @Nullable
    public ShortClipHostUtils.Host getShortClipHost() {
        if (shortClipHost == null) {
            return null;
        }
        try {
            return ShortClipHostUtils.Host.valueOf(shortClipHost);
        } catch (IllegalArgumentException e) {
            // A host dropped from the enum after a post was cached. Renders as a link card.
            return null;
        }
    }

    public void setShortClipHost(@Nullable ShortClipHostUtils.Host host) {
        this.shortClipHost = host == null ? null : host.name();
    }

    public boolean isShortClip() {
        return shortClipHost != null;
    }

    @Nullable
    public String getShortClipId() {
        return shortClipId;
    }

    public void setShortClipId(@Nullable String shortClipId) {
        this.shortClipId = shortClipId;
    }

    public void setIsImgur(boolean isImgur) {
        this.isImgur = isImgur;
    }

    public boolean isImgur() {
        return isImgur;
    }

    public boolean isRedgifs() {
        return isRedgifs;
    }

    public void setIsRedgifs(boolean isRedgifs) {
        this.isRedgifs = isRedgifs;
    }

    public boolean isStreamable() {
        return isStreamable;
    }

    public void setIsStreamable(boolean isStreamable) {
        this.isStreamable = isStreamable;
    }

    public boolean isTumblr() {
        return isTumblr;
    }

    public void setIsTumblr(boolean isTumblr) {
        this.isTumblr = isTumblr;
    }

    /**
     * Whether this post plays an MLB highlight, which is a direct single-file MP4 rather than
     * anything Reddit hosts.
     *
     * <p>Recorded at parse time rather than re-derived from the URL because three separate
     * decisions read it -- the quality selector, which download service handles it, and which
     * media source the fullscreen player builds -- and every one of them would otherwise treat
     * these as Reddit video and get it wrong.
     */
    public boolean isMlbClip() {
        return isMlbClip;
    }

    public void setIsMlbClip(boolean isMlbClip) {
        this.isMlbClip = isMlbClip;
    }

    /**
     * Whether this is a Reddit-hosted video, the only kind that carries an HLS track ladder.
     *
     * <p>Everything excluded here plays a progressive MP4 with a single video track, so offering
     * the quality selector for one would show a dialog that cannot change anything.
     */
    public boolean isNormalVideo() {
        return postType == Post.VIDEO_TYPE && !isImgur && !isRedgifs && !isStreamable && !isShortClip()
                && !isMlbClip;
    }

    /**
     * Turns this back into the link post it was parsed from, after the media it was promoted for
     * turned out to be unreachable.
     *
     * <p>Clip hosts purge aggressively and rotate CDNs without notice, so a dead clip is ordinary
     * rather than exceptional. Post type is mutable data, so the row can fall back to exactly the
     * link card the app showed before inline playback existed instead of a permanently empty
     * player. The URL is untouched, so tapping the card still opens the clip page.
     *
     * <p>Which of the two link types depends on whether Reddit gave the post a preview, matching
     * how {@code ParsePost} chose in the first place: the plain link holder leaves an empty image
     * slot when there is nothing to put in it.
     */
    public void demoteToLinkPost() {
        setShortClipHost(null);
        setShortClipId(null);
        setPostType(previews.isEmpty() ? NO_PREVIEW_LINK_TYPE : LINK_TYPE);
    }

    public boolean isLoadedStreamableVideoAlready() {
        return loadedStreamableVideoAlready;
    }

    public void setLoadedStreamableVideoAlready(boolean loadedStreamableVideoAlready) {
        this.loadedStreamableVideoAlready = loadedStreamableVideoAlready;
    }

    public String getPermalink() {
        return permalink;
    }

    public String getFlair() {
        return flair;
    }

    public void setFlair(String flair) {
        this.flair = flair;
    }

    public boolean isModerator() {
        return distinguished != null && distinguished.equals("moderator");
    }

    public void setIsModerator(boolean value) {
        distinguished = value ? "moderator" : null;
    }

    public boolean isAdmin() {
        return distinguished != null && distinguished.equals("admin");
    }

    @Nullable
    public String getSuggestedSort() {
        return suggestedSort;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getPostType() {
        return postType;
    }

    public void setPostType(int postType) {
        this.postType = postType;
    }

    /**
     * Whether the post's own content is media: an image, a GIF, a video or a Reddit gallery.
     *
     * <p>A link post is not media, preview or no. The preview Reddit attaches to one belongs to the
     * article on the far side of the link, not to the post, so a feed showing only media has no
     * business showing it. Text posts are not media either, and polls come through as text posts --
     * this app never parses {@code poll_data}, so a poll is just a self post.
     *
     * <p>Used by the gallery layout's "Media Posts Only" filter (issue #377).
     */
    public boolean isMediaPost() {
        return postType == IMAGE_TYPE || postType == GIF_TYPE || postType == VIDEO_TYPE
                || postType == GALLERY_TYPE;
    }

    public int getVoteType() {
        return voteType;
    }

    public void setVoteType(int voteType) {
        this.voteType = voteType;
    }

    public int getNComments() {
        return nComments;
    }

    public void setNComments(int nComments) {
        this.nComments = nComments;
    }

    public int getUpvoteRatio() {
        return upvoteRatio;
    }

    public void setUpvoteRatio(int upvoteRatio) {
        this.upvoteRatio = upvoteRatio;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isSpoiler() {
        return spoiler;
    }

    public void setSpoiler(boolean spoiler) {
        this.spoiler = spoiler;
    }

    public boolean isNSFW() {
        return nsfw;
    }

    public void setNSFW(boolean nsfw) {
        this.nsfw = nsfw;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(fullName);
        dest.writeString(subredditName);
        dest.writeString(subredditNamePrefixed);
        dest.writeString(subredditIconUrl);
        dest.writeString(author);
        dest.writeString(authorFullname);
        dest.writeString(authorNamePrefixed);
        dest.writeString(authorIconUrl);
        dest.writeString(authorFlair);
        dest.writeString(authorFlairHTML);
        dest.writeString(title);
        dest.writeString(selfText);
        dest.writeString(selfTextPlain);
        dest.writeString(selfTextPlainTrimmed);
        dest.writeString(url);
        dest.writeString(videoUrl);
        dest.writeString(videoDownloadUrl);
        dest.writeString(videoFallBackDirectUrl);
        dest.writeString(thumbnailUrl);
        dest.writeString(redgifsId);
        dest.writeString(streamableShortCode);
        dest.writeString(shortClipHost);
        dest.writeString(shortClipId);
        dest.writeByte((byte) (isImgur ? 1 : 0));
        dest.writeByte((byte) (isRedgifs ? 1 : 0));
        dest.writeByte((byte) (isStreamable ? 1 : 0));
        dest.writeByte((byte) (isTumblr ? 1 : 0));
        dest.writeByte((byte) (isMlbClip ? 1 : 0));
        dest.writeByte((byte) (loadedStreamableVideoAlready ? 1 : 0));
        dest.writeString(permalink);
        dest.writeString(flair);
        dest.writeLong(postTimeMillis);
        dest.writeLong(editedTimeMillis);
        dest.writeInt(score);
        dest.writeInt(postType);
        dest.writeInt(voteType);
        dest.writeInt(nComments);
        dest.writeInt(upvoteRatio);
        dest.writeByte((byte) (hidden ? 1 : 0));
        dest.writeByte((byte) (spoiler ? 1 : 0));
        dest.writeByte((byte) (nsfw ? 1 : 0));
        dest.writeByte((byte) (stickied ? 1 : 0));
        dest.writeByte((byte) (archived ? 1 : 0));
        dest.writeByte((byte) (locked ? 1 : 0));
        dest.writeByte((byte) (saved ? 1 : 0));
        dest.writeByte((byte) (sendReplies ? 1 : 0));
        dest.writeByte((byte) (isCrosspost ? 1 : 0));
        dest.writeByte((byte) (canModPost ? 1 : 0));
        dest.writeByte((byte) (approved ? 1 : 0));
        dest.writeLong(approvedAtUTC);
        dest.writeString(approvedBy);
        dest.writeByte((byte) (removed ? 1 : 0));
        dest.writeByte((byte) (spam ? 1 : 0));
        dest.writeByte((byte) (isRead ? 1 : 0));
        dest.writeByte((byte) (isRecovered ? 1 : 0));
        dest.writeString(crosspostParentId);
        dest.writeString(distinguished);
        dest.writeString(suggestedSort);
        dest.writeString(mp4Variant);
        dest.writeTypedList(previews);
        dest.writeValue(mediaMetadataMap);
        dest.writeTypedList(gallery);
        dest.writeInt(galleryPageIndex);
    }

    public boolean isStickied() {
        return stickied;
    }

    public void setIsStickied(boolean value) {
        stickied = value;
    }

    public boolean isArchived() {
        return archived;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setIsLocked(boolean value) {
        locked = value;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    public boolean isSendReplies() {
        return sendReplies;
    }

    public void setSendReplies(boolean sendReplies) {
        this.sendReplies = sendReplies;
    }

    public boolean isCrosspost() {
        return isCrosspost;
    }

    public boolean isCanModPost() {
        return canModPost;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public long getApprovedAtUTC() {
        return approvedAtUTC;
    }

    public void setApprovedAtUTC(long approvedAtUTC) {
        this.approvedAtUTC = approvedAtUTC;
    }

    @Nullable
    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(@Nullable String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed, boolean spam) {
        this.removed = removed;
        this.spam = spam;
    }

    public boolean isSpam() {
        return spam;
    }

    public void markAsRead() {
        isRead = true;
    }

    public boolean isRecovered() {
        return isRecovered;
    }

    public void setRecovered(boolean recovered) {
        isRecovered = recovered;
    }

    public boolean isRead() {
        return isRead;
    }

    /** Which image of this gallery the feed is showing. See {@link #galleryPageIndex}. */
    public int getGalleryPageIndex() {
        return galleryPageIndex;
    }

    public void setGalleryPageIndex(int galleryPageIndex) {
        this.galleryPageIndex = galleryPageIndex;
    }

    @Nullable
    public String getCrosspostParentId() {
        return crosspostParentId;
    }

    public void setCrosspostParentId(@Nullable String crosspostParentId) {
        this.crosspostParentId = crosspostParentId;
    }

    public ArrayList<Preview> getPreviews() {
        return previews;
    }

    public void setPreviews(ArrayList<Preview> previews) {
        this.previews = previews;
    }

    @Nullable
    public Map<String, MediaMetadata> getMediaMetadataMap() {
        return mediaMetadataMap;
    }

    /**
     * True when the post body embeds Reddit-hosted media (images/video) that is rendered inline in
     * the selftext. For text posts this means a separate Reddit-generated preview would just
     * duplicate what the body already shows, so callers should not surface it as a standalone
     * preview image. See issue #317.
     */
    public boolean embedsInlineBodyMedia() {
        return mediaMetadataMap != null && !mediaMetadataMap.isEmpty();
    }

    public void setMediaMetadataMap(@Nullable Map<String, MediaMetadata> mediaMetadataMap) {
        this.mediaMetadataMap = mediaMetadataMap;
    }

    public ArrayList<Gallery> getGallery() {
        return gallery;
    }

    public void setGallery(ArrayList<Gallery> gallery) {
        this.gallery = gallery != null ? gallery : new ArrayList<>();
    }

    /**
     * Whether any item in this gallery is an animated gif, i.e. whether the post has anything for
     * the feed's gif autoplay to play.
     */
    public boolean hasGalleryGif() {
        for (Gallery galleryItem : gallery) {
            if (galleryItem.mediaType == Gallery.TYPE_GIF) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public String getMp4Variant() {
        return mp4Variant;
    }

    public void setMp4Variant(@Nullable String mp4Variant) {
        this.mp4Variant = mp4Variant;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof Post)) {
            return false;
        }
        return ((Post) obj).id.equals(id)
                && nsfw == ((Post) obj).nsfw
                && spoiler == ((Post) obj).spoiler
                && isRead == ((Post) obj).isRead
                && saved == ((Post) obj).saved
                && hidden == ((Post) obj).hidden
                && voteType == ((Post) obj).voteType
                && stickied == ((Post) obj).stickied
                && approved == ((Post) obj).approved
                && approvedAtUTC == ((Post) obj).approvedAtUTC
                && Objects.equals(approvedBy, ((Post) obj).approvedBy)
                && removed == ((Post) obj).removed
                && spam == ((Post) obj).spam
                && locked == ((Post) obj).locked
                && Objects.equals(distinguished, ((Post) obj).distinguished)
                && Objects.equals(selfText, ((Post) obj).selfText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, nsfw, spoiler, isRead, saved, hidden, voteType,
                stickied, approved, approvedAtUTC, approvedBy, removed,
                spam, locked, distinguished
        );
    }

    public static class Gallery implements Parcelable {
        public static final int TYPE_IMAGE = 0;
        public static final int TYPE_GIF = 1;
        public static final int TYPE_VIDEO = 2;

        public String mimeType;
        public String url;
        public String fallbackUrl;
        private boolean hasFallback;
        // A smaller, resolution-bounded preview used when rendering the gallery inline in the
        // feed/post-detail card. The full-screen media view keeps using `url` (the source). For a
        // gif this is a static still, so the inline gallery falls back to `url` for the one tile
        // autoplay is animating. Null for items that have no usable preview.
        @Nullable
        public String feedPreviewUrl;
        public String fileName;
        public int mediaType;
        public String caption;
        public String captionUrl;

        public Gallery(String mimeType, String url, String fallbackUrl, String fileName, String caption, String captionUrl) {
            this.mimeType = mimeType;
            this.url = url;
            this.fallbackUrl = fallbackUrl;
            this.fileName = fileName;
            if (mimeType.contains("gif")) {
                mediaType = TYPE_GIF;
            } else if (mimeType.contains("jpg") || mimeType.contains("png")) {
                mediaType = TYPE_IMAGE;
            } else {
                mediaType = TYPE_VIDEO;
            }
            this.caption = caption;
            this.captionUrl = captionUrl;
        }

        protected Gallery(Parcel in) {
            mimeType = Objects.requireNonNull(in.readString());
            url = Objects.requireNonNull(in.readString());
            fallbackUrl = Objects.requireNonNull(in.readString());
            hasFallback = in.readByte() != 0;
            feedPreviewUrl = in.readString();
            fileName = Objects.requireNonNull(in.readString());
            mediaType = in.readInt();
            caption = Objects.requireNonNull(in.readString());
            captionUrl = Objects.requireNonNull(in.readString());
        }

        public static final Creator<Gallery> CREATOR = new Creator<Gallery>() {
            @Override
            public Gallery createFromParcel(Parcel in) {
                return new Gallery(in);
            }

            @Override
            public Gallery[] newArray(int size) {
                return new Gallery[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeString(mimeType);
            parcel.writeString(url);
            parcel.writeString(fallbackUrl);
            parcel.writeByte((byte) (hasFallback ? 1 : 0));
            parcel.writeString(feedPreviewUrl);
            parcel.writeString(fileName);
            parcel.writeInt(mediaType);
            parcel.writeString(caption);
            parcel.writeString(captionUrl);
        }

        public void setFallbackUrl(String fallbackUrl) { this.fallbackUrl = fallbackUrl; }

        public void setHasFallback(boolean hasFallback) { this.hasFallback = hasFallback; }

        public boolean hasFallback() { return this.hasFallback; }
    }

    public static class Preview implements Parcelable {
        private String previewUrl;
        private int previewWidth;
        private int previewHeight;
        private String previewCaption;
        private String previewCaptionUrl;

        public Preview(String previewUrl, int previewWidth, int previewHeight, String previewCaption, String previewCaptionUrl) {
            this.previewUrl = previewUrl;
            this.previewWidth = previewWidth;
            this.previewHeight = previewHeight;
            this.previewCaption = previewCaption;
            this.previewCaptionUrl = previewCaptionUrl;
        }

        protected Preview(Parcel in) {
            previewUrl = Objects.requireNonNull(in.readString());
            previewWidth = in.readInt();
            previewHeight = in.readInt();
            previewCaption = Objects.requireNonNull(in.readString());
            previewCaptionUrl = Objects.requireNonNull(in.readString());
        }

        public static final Creator<Preview> CREATOR = new Creator<Preview>() {
            @Override
            public Preview createFromParcel(Parcel in) {
                return new Preview(in);
            }

            @Override
            public Preview[] newArray(int size) {
                return new Preview[size];
            }
        };

        public String getPreviewUrl() {
            return previewUrl;
        }

        public void setPreviewUrl(String previewUrl) {
            this.previewUrl = previewUrl;
        }

        public int getPreviewWidth() {
            return previewWidth;
        }

        public void setPreviewWidth(int previewWidth) {
            this.previewWidth = previewWidth;
        }

        public int getPreviewHeight() {
            return previewHeight;
        }

        public void setPreviewHeight(int previewHeight) {
            this.previewHeight = previewHeight;
        }

        public String getPreviewCaption() {
            return previewCaption;
        }

        public void setPreviewCaption(String previewCaption) { this.previewCaption = previewCaption; }

        public String getPreviewCaptionUrl() {
            return previewCaptionUrl;
        }

        public void setPreviewCaptionUrl(String previewCaptionUrl) { this.previewCaptionUrl = previewCaptionUrl; }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeString(previewUrl);
            parcel.writeInt(previewWidth);
            parcel.writeInt(previewHeight);
            parcel.writeString(previewCaption);
            parcel.writeString(previewCaptionUrl);
        }
    }
}
