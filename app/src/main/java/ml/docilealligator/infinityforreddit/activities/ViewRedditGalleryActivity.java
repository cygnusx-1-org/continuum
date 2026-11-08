package ml.docilealligator.infinityforreddit.activities;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.futured.hauler.DragDirection;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.google.gson.Gson;
import ml.docilealligator.infinityforreddit.utils.SharedGson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.CustomFontReceiver;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.SetAsWallpaperCallback;
import ml.docilealligator.infinityforreddit.WallpaperSetter;
import ml.docilealligator.infinityforreddit.databinding.ActivityViewRedditGalleryBinding;
import ml.docilealligator.infinityforreddit.databinding.ItemGalleryListEntryBinding;
import ml.docilealligator.infinityforreddit.events.FinishViewMediaActivityEvent;
import ml.docilealligator.infinityforreddit.font.ContentFontFamily;
import ml.docilealligator.infinityforreddit.font.ContentFontStyle;
import ml.docilealligator.infinityforreddit.font.FontFamily;
import ml.docilealligator.infinityforreddit.font.FontStyle;
import ml.docilealligator.infinityforreddit.font.TitleFontFamily;
import ml.docilealligator.infinityforreddit.font.TitleFontStyle;
import ml.docilealligator.infinityforreddit.fragments.ViewRedditGalleryImageOrGifFragment;
import ml.docilealligator.infinityforreddit.fragments.ViewRedditGalleryVideoFragment;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.resume.Restorable;
import ml.docilealligator.infinityforreddit.resume.ResumeLaunchExtras;
import ml.docilealligator.infinityforreddit.resume.ResumeState;
import ml.docilealligator.infinityforreddit.services.DownloadMediaService;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import ml.docilealligator.infinityforreddit.viewmodels.ViewGalleryViewModel;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

public class ViewRedditGalleryActivity extends AppCompatActivity
        implements SetAsWallpaperCallback, CustomFontReceiver, Restorable, ResumeLaunchExtras {

    public static final String EXTRA_POST = "EP";
    /**
     * The same post as {@link #EXTRA_POST}, as JSON, for a resume replay. See
     * {@link #resumeLaunchExtras()}.
     */
    public static final String EXTRA_POST_JSON = "EPJ";
    public static final String EXTRA_GALLERY_ITEM_INDEX = "EGII";
    /**
     * Change image by swiping up and down instead of sideways. Then a swipe left goes back to the
     * posts and a swipe right opens the post and its comments.
     */
    public static final String EXTRA_VERTICAL_SWIPE = "EVS";
    /**
     * Open on a scrolling list of every image rather than the pager; a tap on an image opens the
     * pager on it, and back returns to the list. The same left and right swipes as above.
     */
    public static final String EXTRA_SCROLL_LIST = "ESL";
    /** How many rows of the scrolling list play their clip at once. */
    private static final int MAX_PLAYING_ROWS = 3;
    /** Which item of the gallery the user was on. See {@link #saveResumeState}. */
    private static final String STATE_RESUME_PAGE = "RP";
    private static final String STATE_SHOWING_LIST = "SSL";
    private static final String STATE_CAME_FROM_LIST = "SCFL";

    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;
    @Inject
    Executor executor;
    @Nullable
    public Typeface typeface;
    // Assigned from the required EXTRA_POST intent extra after a finish()+return guard, so its
    // initialization along the early-return path can't be proven; live paths always set it.
    @SuppressWarnings("NullAway.Init")
    private Post post;
    @SuppressWarnings("NullAway.Init")
    private ArrayList<Post.Gallery> gallery;
    @Nullable
    private String subredditName;
    private boolean isNsfw;
    private boolean isActionBarHidden = false;
    // Resume where I left off: which item of the gallery was open. The gallery itself replays in the
    // extras, so this needs no network of its own.
    private int resumePage = -1;
    private boolean verticalSwipe;
    private boolean showingList;
    // Whether the list may play its gifs and videos by itself, under the app's Video Autoplay settings.
    private boolean autoplayMedia;
    // The rows whose clip is playing: the most visible few, so a screenful of gifs does not mean a
    // screenful of decoders.
    private final Set<GalleryListViewHolder> playingRows = new LinkedHashSet<>();
    // Set when a swipe left or right means something here: left closes the gallery, right opens the
    // post. Null when the gallery pages sideways, where those swipes belong to the pager.
    @Nullable
    private GestureDetector sideSwipeDetector;
    // Enabled while the pager is open over the list, so back returns to the list instead of leaving.
    private final OnBackPressedCallback backToList = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            showList(binding.viewPagerViewRedditGalleryActivity.getCurrentItem());
        }
    };
    private ActivityViewRedditGalleryBinding binding;
    ViewGalleryViewModel viewGalleryViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((Infinity) getApplication()).getAppComponent().inject(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }

        boolean systemDefault = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        int systemThemeType = SharedPreferencesUtils.getInt(sharedPreferences, SharedPreferencesUtils.THEME_KEY, SharedPreferencesUtils.THEME_FOLLOW_SYSTEM);
        switch (systemThemeType) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO);
                getTheme().applyStyle(R.style.Theme_Normal, true);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES);
                if (sharedPreferences.getBoolean(SharedPreferencesUtils.AMOLED_DARK_KEY, false)) {
                    getTheme().applyStyle(R.style.Theme_Normal_AmoledDark, true);
                } else {
                    getTheme().applyStyle(R.style.Theme_Normal_NormalDark, true);
                }
                break;
            case 2:
                if (systemDefault) {
                    AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_AUTO_BATTERY);
                }
                if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO) {
                    getTheme().applyStyle(R.style.Theme_Normal, true);
                } else {
                    if (sharedPreferences.getBoolean(SharedPreferencesUtils.AMOLED_DARK_KEY, false)) {
                        getTheme().applyStyle(R.style.Theme_Normal_AmoledDark, true);
                    } else {
                        getTheme().applyStyle(R.style.Theme_Normal_NormalDark, true);
                    }
                }
        }

        getTheme().applyStyle(FontStyle.valueOf(Objects.requireNonNull(sharedPreferences
                .getString(SharedPreferencesUtils.FONT_SIZE_KEY, FontStyle.Normal.name()))).getResId(), true);

        getTheme().applyStyle(TitleFontStyle.valueOf(Objects.requireNonNull(sharedPreferences
                .getString(SharedPreferencesUtils.TITLE_FONT_SIZE_KEY, TitleFontStyle.Normal.name()))).getResId(), true);

        getTheme().applyStyle(ContentFontStyle.valueOf(Objects.requireNonNull(sharedPreferences
                .getString(SharedPreferencesUtils.CONTENT_FONT_SIZE_KEY, ContentFontStyle.Normal.name()))).getResId(), true);

        getTheme().applyStyle(FontFamily.of(Objects.requireNonNull(sharedPreferences
                .getString(SharedPreferencesUtils.FONT_FAMILY_KEY, FontFamily.Default.name()))).getResId(), true);

        getTheme().applyStyle(TitleFontFamily.of(Objects.requireNonNull(sharedPreferences
                .getString(SharedPreferencesUtils.TITLE_FONT_FAMILY_KEY, TitleFontFamily.Default.name()))).getResId(), true);

        getTheme().applyStyle(ContentFontFamily.of(Objects.requireNonNull(sharedPreferences
                .getString(SharedPreferencesUtils.CONTENT_FONT_FAMILY_KEY, ContentFontFamily.Default.name()))).getResId(), true);

        binding = ActivityViewRedditGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        // Reddit Jr. dropped the "use bottom toolbar in media viewer" option and made the bottom
        // bar the default (it carries the rotate action), so the top toolbar upstream reinstated
        // never shows here.
        binding.toolbarViewRedditGalleryActivity.setVisibility(View.GONE);

        viewGalleryViewModel = new ViewModelProvider(this).get(ViewGalleryViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                Insets allInsets = Utils.getInsets(insets, false, false);

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.toolbarViewRedditGalleryActivity.getLayoutParams();
                params.topMargin = allInsets.top;
                binding.toolbarViewRedditGalleryActivity.setLayoutParams(params);

                binding.recyclerViewViewRedditGalleryActivity.setPadding(allInsets.left, allInsets.top, allInsets.right, allInsets.bottom);

                viewGalleryViewModel.setInsets(allInsets);
                return WindowInsetsCompat.CONSUMED;
            }
        });

        Post post = getIntent().getParcelableExtra(EXTRA_POST);
        if (post == null) {
            // A resume replay carries the post as JSON, because a marshalled Parcel cannot be
            // stored. It has to be the whole post and not just the gallery: downloading and sharing
            // an item name the post they came from, and both would break on a restored screen if it
            // came back holding only the media.
            String postJson = getIntent().getStringExtra(EXTRA_POST_JSON);
            if (postJson != null) {
                try {
                    post = SharedGson.INSTANCE.fromJson(postJson, Post.class);
                } catch (JsonSyntaxException e) {
                    post = null;
                }
            }
        }
        if (post == null) {
            finish();
            return;
        }
        this.post = post;
        gallery = post.getGallery();
        if (gallery == null || gallery.isEmpty()) {
            finish();
            return;
        }
        subredditName = post.getSubredditName();
        isNsfw = post.isNSFW();

        autoplayMedia = shouldAutoplayMedia();
        verticalSwipe = getIntent().getBooleanExtra(EXTRA_VERTICAL_SWIPE, false);
        boolean scrollList = getIntent().getBooleanExtra(EXTRA_SCROLL_LIST, false);
        binding.viewPagerViewRedditGalleryActivity.setVertical(verticalSwipe);
        if (verticalSwipe || scrollList) {
            // An up or down drag changes image or scrolls the list, so it cannot also close the
            // gallery; a swipe left does that.
            binding.haulerViewViewRedditGalleryActivity.setDragEnabled(false);
            sideSwipeDetector = new GestureDetector(this, new SideSwipeListener());
        } else if (sharedPreferences.getBoolean(SharedPreferencesUtils.SWIPE_VERTICALLY_TO_GO_BACK_FROM_MEDIA, true)) {
            binding.haulerViewViewRedditGalleryActivity.setOnDragDismissedListener(dragDirection -> {
                int slide = dragDirection == DragDirection.UP ? R.anim.slide_out_up : R.anim.slide_out_down;
                finish();
                overridePendingTransition(0, slide);
            });
        } else {
            binding.haulerViewViewRedditGalleryActivity.setDragEnabled(false);
        }

        getOnBackPressedDispatcher().addCallback(this, backToList);
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_SHOWING_LIST)) {
            showList(0);
        } else if (savedInstanceState == null && scrollList) {
            showList(firstIndex());
        } else {
            setupViewPager(savedInstanceState);
            backToList.setEnabled(savedInstanceState != null && savedInstanceState.getBoolean(STATE_CAME_FROM_LIST));
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (sideSwipeDetector != null) {
            sideSwipeDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * A swipe left goes back to the posts and a swipe right opens the post and its comments,
     * wherever the swipe is on the screen. Only while nothing here pages sideways -- the list, or the pager
     * when it pages up and down -- and not while a picture is zoomed in, where a sideways drag
     * pans it.
     */
    private class SideSwipeListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(@Nullable MotionEvent down, @NonNull MotionEvent up, float velocityX, float velocityY) {
            if (down == null || !(showingList || verticalSwipe) || isPictureZoomedIn()) {
                return false;
            }
            float distanceX = up.getX() - down.getX();
            float distanceY = up.getY() - down.getY();
            float minimumDistance = 100 * getResources().getDisplayMetrics().density;
            if (Math.abs(distanceX) < minimumDistance || Math.abs(distanceX) < 2 * Math.abs(distanceY)) {
                return false;
            }
            if (distanceX > 0) {
                finish();
            } else {
                Intent intent = new Intent(ViewRedditGalleryActivity.this, ViewPostDetailActivity.class);
                intent.putExtra(ViewPostDetailActivity.EXTRA_POST_DATA, post);
                startActivity(intent);
                finish();
            }
            return true;
        }
    }

    private boolean isPictureZoomedIn() {
        if (showingList) {
            return false;
        }
        Fragment current = getSupportFragmentManager().findFragmentByTag(
                "android:switcher:" + binding.viewPagerViewRedditGalleryActivity.getId()
                        + ":" + binding.viewPagerViewRedditGalleryActivity.getCurrentItem());
        return current instanceof ViewRedditGalleryImageOrGifFragment
                && ((ViewRedditGalleryImageOrGifFragment) current).isZoomedIn();
    }

    private class GalleryListAdapter extends RecyclerView.Adapter<GalleryListViewHolder> {
        @NonNull
        @Override
        public GalleryListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new GalleryListViewHolder(ItemGalleryListEntryBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull GalleryListViewHolder holder, int position) {
            holder.bind(gallery.get(position));
        }

        @Override
        public int getItemCount() {
            return gallery.size();
        }

        @Override
        public void onViewRecycled(@NonNull GalleryListViewHolder holder) {
            stopPlaying(holder);
            Glide.with(ViewRedditGalleryActivity.this).clear(holder.binding.imageViewItemGalleryListEntry);
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull GalleryListViewHolder holder) {
            stopPlaying(holder);
        }
    }

    private class GalleryListViewHolder extends RecyclerView.ViewHolder {
        final ItemGalleryListEntryBinding binding;
        @Nullable
        Post.Gallery item;
        @Nullable
        ExoPlayer player;

        GalleryListViewHolder(ItemGalleryListEntryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (typeface != null) {
                binding.captionTextViewItemGalleryListEntry.setTypeface(typeface);
            }
            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    showPager(position);
                }
            });
        }

        void bind(Post.Gallery item) {
            this.item = item;
            // The source's own shape reserves the row's height before the picture arrives, so the
            // list does not jump as images load.
            binding.imageViewItemGalleryListEntry.setRatio(
                    item.sourceWidth > 0 && item.sourceHeight > 0 ? (float) item.sourceHeight / item.sourceWidth : 0f);
            // A clip that plays by itself needs no badge to say it can be played.
            binding.playBadgeImageViewItemGalleryListEntry.setVisibility(
                    item.mediaType == Post.Gallery.TYPE_IMAGE || (autoplayMedia && clipOf(item) != null)
                            ? View.GONE : View.VISIBLE);
            // The resolution-bounded still Reddit publishes, whatever the item is: the source is
            // several thousand pixels wide, and a video's own url is an mp4 an ImageView cannot
            // show. The clip, where there is one, plays over the still while the row is on screen.
            String still = item.feedPreviewUrl;
            if (still == null && item.mediaType != Post.Gallery.TYPE_VIDEO) {
                still = item.url;
            }
            if (still == null) {
                Glide.with(ViewRedditGalleryActivity.this).clear(binding.imageViewItemGalleryListEntry);
                binding.imageViewItemGalleryListEntry.setImageDrawable(null);
                binding.errorImageViewItemGalleryListEntry.setVisibility(View.VISIBLE);
            } else {
                binding.errorImageViewItemGalleryListEntry.setVisibility(View.GONE);
                // Decoded at the screen's width, whatever size the file is.
                Glide.with(ViewRedditGalleryActivity.this).load(still)
                        .override(getResources().getDisplayMetrics().widthPixels, Target.SIZE_ORIGINAL)
                        .into(binding.imageViewItemGalleryListEntry);
            }
            if (TextUtils.isEmpty(item.caption)) {
                binding.captionTextViewItemGalleryListEntry.setVisibility(View.GONE);
            } else {
                binding.captionTextViewItemGalleryListEntry.setVisibility(View.VISIBLE);
                binding.captionTextViewItemGalleryListEntry.setText(item.caption);
            }
        }
    }

    /** The clip a row plays, or null for a still picture and for a GIF Reddit gave no mp4 for. */
    @Nullable
    private static String clipOf(Post.Gallery item) {
        if (item.mediaType == Post.Gallery.TYPE_VIDEO) {
            return item.url;
        }
        return item.mediaType == Post.Gallery.TYPE_GIF ? item.mp4Url : null;
    }

    /**
     * The same rule the feed applies: Video Autoplay set to never, or to Wi-Fi only while on mobile
     * data, means nothing plays by itself, and Autoplay NSFW Videos excludes NSFW posts.
     */
    private boolean shouldAutoplayMedia() {
        String setting = sharedPreferences.getString(
                SharedPreferencesUtils.VIDEO_AUTOPLAY, SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_NEVER);
        boolean allowed = SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_ALWAYS_ON.equals(setting)
                || (SharedPreferencesUtils.VIDEO_AUTOPLAY_VALUE_ON_WIFI.equals(setting)
                && Utils.getConnectedNetwork(this) == Utils.NETWORK_TYPE_WIFI);
        return allowed && (!isNsfw || sharedPreferences.getBoolean(SharedPreferencesUtils.AUTOPLAY_NSFW_VIDEOS, true));
    }

    /**
     * Plays the clips of the rows showing most of themselves, up to {@link #MAX_PLAYING_ROWS}, and
     * stops the rest. Run when the list comes to rest, not while it moves: a row flung past never
     * starts a download.
     */
    private void updatePlayback() {
        if (!autoplayMedia || !showingList) {
            return;
        }
        RecyclerView list = binding.recyclerViewViewRedditGalleryActivity;
        List<GalleryListViewHolder> wanted = new ArrayList<>();
        List<Integer> visibleHeights = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            RecyclerView.ViewHolder holder = list.getChildViewHolder(child);
            if (!(holder instanceof GalleryListViewHolder)) {
                continue;
            }
            GalleryListViewHolder row = (GalleryListViewHolder) holder;
            int visible = Math.min(child.getBottom(), list.getHeight()) - Math.max(child.getTop(), 0);
            if (row.item != null && clipOf(row.item) != null && visible > 0) {
                wanted.add(row);
                visibleHeights.add(visible);
            }
        }
        while (wanted.size() > MAX_PLAYING_ROWS) {
            int least = visibleHeights.indexOf(Collections.min(visibleHeights));
            wanted.remove(least);
            visibleHeights.remove(least);
        }
        for (GalleryListViewHolder playing : new ArrayList<>(playingRows)) {
            if (!wanted.contains(playing)) {
                stopPlaying(playing);
            }
        }
        for (GalleryListViewHolder row : wanted) {
            if (row.player == null) {
                startPlaying(row);
            }
        }
    }

    private void startPlaying(GalleryListViewHolder row) {
        String clip = row.item == null ? null : clipOf(row.item);
        if (clip == null) {
            return;
        }
        ExoPlayer player = new ExoPlayer.Builder(this).build();
        player.setVolume(0f);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        row.player = player;
        playingRows.add(row);
        row.binding.playerViewItemGalleryListEntry.setPlayer(player);
        row.binding.playerViewItemGalleryListEntry.setVisibility(View.VISIBLE);
        player.setMediaItem(MediaItem.fromUri(clip));
        player.setPlayWhenReady(true);
        player.prepare();
    }

    private void stopPlaying(GalleryListViewHolder row) {
        playingRows.remove(row);
        row.binding.playerViewItemGalleryListEntry.setPlayer(null);
        row.binding.playerViewItemGalleryListEntry.setVisibility(View.GONE);
        if (row.player != null) {
            row.player.release();
            row.player = null;
        }
    }

    private void stopAllPlaying() {
        for (GalleryListViewHolder row : new ArrayList<>(playingRows)) {
            stopPlaying(row);
        }
    }

    @Override
    protected void onPause() {
        stopAllPlaying();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.recyclerViewViewRedditGalleryActivity.post(this::updatePlayback);
    }

    @Override
    protected void onDestroy() {
        stopAllPlaying();
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    /** Lays every image out in one scrolling list. The pager is not created until an image is tapped. */
    private void showList(int scrollTo) {
        showingList = true;
        backToList.setEnabled(false);
        binding.viewPagerViewRedditGalleryActivity.setVisibility(View.GONE);
        binding.viewPagerViewRedditGalleryActivity.setAdapter(null);
        binding.recyclerViewViewRedditGalleryActivity.setVisibility(View.VISIBLE);
        if (binding.recyclerViewViewRedditGalleryActivity.getAdapter() == null) {
            binding.recyclerViewViewRedditGalleryActivity.setLayoutManager(new LinearLayoutManager(this));
            binding.recyclerViewViewRedditGalleryActivity.setAdapter(new GalleryListAdapter());
            binding.recyclerViewViewRedditGalleryActivity.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        updatePlayback();
                    }
                }
            });
        }
        if (scrollTo > 0 && scrollTo < gallery.size()) {
            binding.recyclerViewViewRedditGalleryActivity.scrollToPosition(scrollTo);
        }
        binding.recyclerViewViewRedditGalleryActivity.post(this::updatePlayback);
    }

    /** Opens the pager, with zoom, download and share, on one image of the list. */
    private void showPager(int index) {
        showingList = false;
        backToList.setEnabled(true);
        stopAllPlaying();
        binding.recyclerViewViewRedditGalleryActivity.setVisibility(View.GONE);
        binding.viewPagerViewRedditGalleryActivity.setVisibility(View.VISIBLE);
        createPagerAdapter();
        binding.viewPagerViewRedditGalleryActivity.setCurrentItem(index, false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOWING_LIST, showingList);
        outState.putBoolean(STATE_CAME_FROM_LIST, backToList.isEnabled());
    }

    /**
     * The item the opening intent names, which wins over a resume: it is what the user tapped just
     * now, where the resume is what they were looking at last time.
     */
    private int firstIndex() {
        Bundle resumeState = ResumeState.claim(this);
        if (resumeState != null) {
            restoreResumeState(resumeState);
        }
        int index = getIntent().getIntExtra(EXTRA_GALLERY_ITEM_INDEX, 0);
        if (index == 0 && resumePage > 0 && resumePage < gallery.size()) {
            index = resumePage;
        }
        return index;
    }

    private void createPagerAdapter() {
        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        binding.viewPagerViewRedditGalleryActivity.setAdapter(sectionsPagerAdapter);
        binding.viewPagerViewRedditGalleryActivity.setOffscreenPageLimit(3);
    }

    private void setupViewPager(@Nullable Bundle savedInstanceState) {
        createPagerAdapter();
        if (savedInstanceState == null) {
            binding.viewPagerViewRedditGalleryActivity.setCurrentItem(firstIndex(), false);
        }
    }

    @Override
    public void saveResumeState(@NonNull Bundle out) {
        out.putInt(STATE_RESUME_PAGE, binding.viewPagerViewRedditGalleryActivity.getCurrentItem());
    }

    @Override
    public void restoreResumeState(@NonNull Bundle state) {
        resumePage = state.getInt(STATE_RESUME_PAGE, -1);
    }

    /**
     * The launch extras with the post turned into JSON.
     *
     * <p>This screen is handed the post whole, and a marshalled {@code Parcel} must never be written
     * to disk -- its layout is a private implementation detail that changes between releases.
     * Recording the post's id instead, as the comments screen does, would mean refetching it, and a
     * media viewer that needs the network to reopen defeats the point of a resume that otherwise
     * costs none. {@link Post} is a plain record, so its JSON round-trips, and a gallery's media URLs
     * do not go stale the way a comment thread does.
     */
    /**
     * The post's id, which is what makes one gallery a different screen from another. Only the part
     * of this screen's identity hidden inside a {@code Parcelable}; ResumeState adds the primitive
     * extras itself. Pulled out rather than serialized: unparcelling costs microseconds where
     * {@link #resumeLaunchExtras()}'s Gson call was measured at 192.9 ms on a device.
     */
    @Nullable
    @Override
    public String resumeIdentity() {
        Post post = getIntent().getParcelableExtra(EXTRA_POST);
        if (post != null) {
            return post.getId();
        }
        // A replayed launch carries the post as JSON instead; its id is not worth digging out of
        // that, and a replay is not a rotation, so the full comparison can have this one.
        return null;
    }

    @Nullable
    @Override
    public Bundle resumeLaunchExtras() {
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            return null;
        }
        Post post = getIntent().getParcelableExtra(EXTRA_POST);
        if (post == null) {
            // Already a replayed launch: its extras are recordable as they stand.
            return extras;
        }
        Bundle out = new Bundle(extras);
        out.remove(EXTRA_POST);
        out.putString(EXTRA_POST_JSON, SharedGson.INSTANCE.toJson(post));
        // The index the user originally tapped is superseded by the item they were actually on when
        // they left, so it must not travel with the replay. Left in, it would make the restored
        // screen open on the tapped item and ignore the recorded one -- and it is precisely its
        // absence that tells setupViewPager a launch is a replay rather than a fresh tap.
        out.remove(EXTRA_GALLERY_ITEM_INDEX);
        return out;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.view_reddit_gallery_activity, menu);
        for (int i = 0; i < menu.size(); i++) {
            Utils.setTitleWithCustomFontToMenuItem(typeface, menu.getItem(i), null);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_download_all_gallery_media_view_reddit_gallery_activity) {
            downloadAllGalleryMedia();
            return true;
        }

        return false;
    }

    public void downloadAllGalleryMedia() {
        // Check if download locations are set for all media types
        // Gallery can contain images, GIFs, and videos, so we need to check all relevant locations
        String imageDownloadLocation = sharedPreferences.getString(SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION, "");
        String gifDownloadLocation = sharedPreferences.getString(SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION, "");
        String videoDownloadLocation = sharedPreferences.getString(SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION, "");
        String nsfwDownloadLocation = "";

        boolean needsNsfwLocation = isNsfw &&
                sharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_NSFW_MEDIA_IN_DIFFERENT_FOLDER, false);

        if (needsNsfwLocation) {
            nsfwDownloadLocation = sharedPreferences.getString(SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION, "");
            if (nsfwDownloadLocation == null || nsfwDownloadLocation.isEmpty()) {
                Toast.makeText(this, R.string.download_location_not_set, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // Check for required download locations based on the gallery content
            boolean hasImage = false;
            boolean hasGif = false;
            boolean hasVideo = false;

            for (Post.Gallery galleryItem : gallery) {
                if (galleryItem.mediaType == Post.Gallery.TYPE_VIDEO) {
                    hasVideo = true;
                } else if (galleryItem.mediaType == Post.Gallery.TYPE_GIF) {
                    hasGif = true;
                } else {
                    hasImage = true;
                }
            }

            if ((hasImage && (imageDownloadLocation == null || imageDownloadLocation.isEmpty())) ||
                (hasGif && (gifDownloadLocation == null || gifDownloadLocation.isEmpty())) ||
                (hasVideo && (videoDownloadLocation == null || videoDownloadLocation.isEmpty()))) {
                Toast.makeText(this, R.string.download_location_not_set, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        //TODO: contentEstimatedBytes
        JobInfo jobInfo = DownloadMediaService.constructGalleryDownloadAllMediaJobInfo(this, 5000000L * gallery.size(), post);
        ((JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(jobInfo);

        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void setToHomeScreen(int viewPagerPosition) {
        if (gallery != null && viewPagerPosition >= 0 && viewPagerPosition < gallery.size()) {
            WallpaperSetter.set(executor, new Handler(), gallery.get(viewPagerPosition).url, WallpaperSetter.HOME_SCREEN, this,
                    new WallpaperSetter.SetWallpaperListener() {
                        @Override
                        public void success() {
                            Toast.makeText(ViewRedditGalleryActivity.this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void failed() {
                            Toast.makeText(ViewRedditGalleryActivity.this, R.string.error_set_wallpaper, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    public void setToLockScreen(int viewPagerPosition) {
        if (gallery != null && viewPagerPosition >= 0 && viewPagerPosition < gallery.size()) {
            WallpaperSetter.set(executor, new Handler(), gallery.get(viewPagerPosition).url, WallpaperSetter.LOCK_SCREEN, this,
                    new WallpaperSetter.SetWallpaperListener() {
                        @Override
                        public void success() {
                            Toast.makeText(ViewRedditGalleryActivity.this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void failed() {
                            Toast.makeText(ViewRedditGalleryActivity.this, R.string.error_set_wallpaper, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    public void setToBoth(int viewPagerPosition) {
        if (gallery != null && viewPagerPosition >= 0 && viewPagerPosition < gallery.size()) {
            WallpaperSetter.set(executor, new Handler(), gallery.get(viewPagerPosition).url, WallpaperSetter.BOTH_SCREENS, this,
                    new WallpaperSetter.SetWallpaperListener() {
                        @Override
                        public void success() {
                            Toast.makeText(ViewRedditGalleryActivity.this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void failed() {
                            Toast.makeText(ViewRedditGalleryActivity.this, R.string.error_set_wallpaper, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    public int getCurrentPagePosition() {
        return binding.viewPagerViewRedditGalleryActivity.getCurrentItem();
    }

    @Override
    public void setCustomFont(@Nullable Typeface typeface, @Nullable Typeface titleTypeface, @Nullable Typeface contentTypeface) {
        this.typeface = typeface;
    }

    public boolean isActionBarHidden() {
        return isActionBarHidden;
    }

    public void setActionBarHidden(boolean isActionBarHidden) {
        this.isActionBarHidden = isActionBarHidden;
    }

    // Add getter for the Post object
    public Post getPost() {
        return post;
    }

    @Subscribe
    public void onFinishViewMediaActivityEvent(FinishViewMediaActivityEvent e) {
        finish();
    }

    private class SectionsPagerAdapter extends FragmentStatePagerAdapter {

        SectionsPagerAdapter(@NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Post.Gallery media = gallery.get(position);
            if (media.mediaType == Post.Gallery.TYPE_VIDEO) {
                ViewRedditGalleryVideoFragment fragment = new ViewRedditGalleryVideoFragment();
                Bundle bundle = new Bundle();
                bundle.putParcelable(ViewRedditGalleryVideoFragment.EXTRA_REDDIT_GALLERY_VIDEO, media);
                bundle.putString(ViewRedditGalleryVideoFragment.EXTRA_SUBREDDIT_NAME, subredditName);
                bundle.putInt(ViewRedditGalleryVideoFragment.EXTRA_INDEX, position);
                bundle.putInt(ViewRedditGalleryVideoFragment.EXTRA_MEDIA_COUNT, gallery.size());
                bundle.putBoolean(ViewRedditGalleryVideoFragment.EXTRA_IS_NSFW, isNsfw);
                fragment.setArguments(bundle);
                return fragment;
            } else {
                ViewRedditGalleryImageOrGifFragment fragment = new ViewRedditGalleryImageOrGifFragment();
                Bundle bundle = new Bundle();
                bundle.putParcelable(ViewRedditGalleryImageOrGifFragment.EXTRA_REDDIT_GALLERY_MEDIA, media);
                bundle.putString(ViewRedditGalleryImageOrGifFragment.EXTRA_SUBREDDIT_NAME, subredditName);
                bundle.putInt(ViewRedditGalleryImageOrGifFragment.EXTRA_INDEX, position);
                bundle.putInt(ViewRedditGalleryImageOrGifFragment.EXTRA_MEDIA_COUNT, gallery.size());
                bundle.putBoolean(ViewRedditGalleryImageOrGifFragment.EXTRA_IS_NSFW, isNsfw);
                fragment.setArguments(bundle);
                return fragment;
            }
        }

        @Override
        public int getCount() {
            return gallery.size();
        }
    }
}
