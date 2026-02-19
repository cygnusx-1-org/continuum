package ml.docilealligator.infinityforreddit.bottomsheetfragments;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.PostModerationActionHandler;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.activities.CommentActivity;
import ml.docilealligator.infinityforreddit.activities.PostFilterPreferenceActivity;
import ml.docilealligator.infinityforreddit.activities.ReportActivity;
import ml.docilealligator.infinityforreddit.activities.SubmitCrosspostActivity;
import ml.docilealligator.infinityforreddit.customviews.LandscapeExpandedRoundedBottomSheetDialogFragment;
import ml.docilealligator.infinityforreddit.databinding.FragmentPostOptionsBottomSheetBinding;
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostList;
import ml.docilealligator.infinityforreddit.post.HidePost;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.services.DownloadMediaService;
import ml.docilealligator.infinityforreddit.services.DownloadRedditVideoService;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import retrofit2.Retrofit;

import java.util.ArrayList;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PostOptionsBottomSheetFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PostOptionsBottomSheetFragment extends LandscapeExpandedRoundedBottomSheetDialogFragment {

    private static final String EXTRA_POST = "EP";
    private static final String EXTRA_POST_LIST_POSITION = "EPLP";
    private static final String EXTRA_GALLERY_INDEX = "EGI";
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 0;

    private BaseActivity mBaseActivity;
    private Post mPost;
    private FragmentPostOptionsBottomSheetBinding binding;
    private boolean isDownloading = false;
    private boolean isDownloadingGallery = false;

    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;

    public PostOptionsBottomSheetFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param post Post
     * @return A new instance of fragment PostOptionsBottomSheetFragment.
     */
    public static PostOptionsBottomSheetFragment newInstance(Post post, int postListPosition, int galleryIndex) {
        PostOptionsBottomSheetFragment fragment = new PostOptionsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_POST, post);
        args.putInt(EXTRA_POST_LIST_POSITION, postListPosition);
        args.putInt(EXTRA_GALLERY_INDEX, galleryIndex);
        fragment.setArguments(args);
        return fragment;
    }

    public static PostOptionsBottomSheetFragment newInstance(Post post, int postListPosition) {
        PostOptionsBottomSheetFragment fragment = new PostOptionsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_POST, post);
        args.putInt(EXTRA_POST_LIST_POSITION, postListPosition);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mPost = getArguments().getParcelable(EXTRA_POST);
        } else {
            dismiss();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((Infinity) mBaseActivity.getApplication()).getAppComponent().inject(this);
        // Inflate the layout for this fragment
        binding = FragmentPostOptionsBottomSheetBinding.inflate(inflater, container, false);

        if (mPost != null) {
            switch (mPost.getPostType()) {
                case Post.IMAGE_TYPE:
                case Post.GALLERY_TYPE:
                    binding.downloadTextViewPostOptionsBottomSheetFragment.setVisibility(View.VISIBLE);
                    binding.downloadTextViewPostOptionsBottomSheetFragment.setText(R.string.download_image);
                    break;
                case Post.GIF_TYPE:
                    binding.downloadTextViewPostOptionsBottomSheetFragment.setVisibility(View.VISIBLE);
                    binding.downloadTextViewPostOptionsBottomSheetFragment.setText(R.string.download_gif);
                    break;
                case Post.VIDEO_TYPE:
                    binding.downloadTextViewPostOptionsBottomSheetFragment.setVisibility(View.VISIBLE);
                    binding.downloadTextViewPostOptionsBottomSheetFragment.setText(R.string.download_video);
                    break;
            }

            if (binding.downloadTextViewPostOptionsBottomSheetFragment.getVisibility() == View.VISIBLE) {
                binding.downloadTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                    if (isDownloading) {
                        return;
                    }

                    isDownloading = true;
                    requestPermissionAndDownload();
                });
            }

            if (mPost.getPostType() == Post.GALLERY_TYPE) {
                binding.downloadAllTextViewPostOptionsBottomSheetFragment.setVisibility(View.VISIBLE);
                binding.downloadAllTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                    if (isDownloadingGallery) {
                        return;
                    }

                    isDownloadingGallery = true;
                    requestPermissionAndDownloadGallery();
                });
            }

            binding.shareTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                Bundle bundle = new Bundle();
                bundle.putString(ShareBottomSheetFragment.EXTRA_POST_LINK, mPost.getPermalink());
                if (mPost.getPostType() != Post.TEXT_TYPE) {
                    bundle.putInt(ShareBottomSheetFragment.EXTRA_MEDIA_TYPE, mPost.getPostType());
                    switch (mPost.getPostType()) {
                        case Post.IMAGE_TYPE:
                        case Post.GIF_TYPE:
                        case Post.LINK_TYPE:
                        case Post.NO_PREVIEW_LINK_TYPE:
                            bundle.putString(ShareBottomSheetFragment.EXTRA_MEDIA_LINK, mPost.getUrl());
                            break;
                        case Post.VIDEO_TYPE:
                            bundle.putString(ShareBottomSheetFragment.EXTRA_MEDIA_LINK, mPost.getVideoDownloadUrl());
                            break;
                    }
                }
                bundle.putParcelable(ShareBottomSheetFragment.EXTRA_POST, mPost);
                ShareBottomSheetFragment shareBottomSheetFragment = new ShareBottomSheetFragment();
                shareBottomSheetFragment.setArguments(bundle);
                Fragment parentFragment = getParentFragment();
                if (parentFragment != null) {
                    shareBottomSheetFragment.show(parentFragment.getChildFragmentManager(), shareBottomSheetFragment.getTag());
                } else {
                    shareBottomSheetFragment.show(mBaseActivity.getSupportFragmentManager(), shareBottomSheetFragment.getTag());
                }

                dismiss();
            });

            binding.addToPostFilterTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                Intent intent = new Intent(mBaseActivity, PostFilterPreferenceActivity.class);
                intent.putExtra(PostFilterPreferenceActivity.EXTRA_POST, mPost);
                startActivity(intent);

                dismiss();
            });

            if (mBaseActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                binding.commentTextViewPostOptionsBottomSheetFragment.setVisibility(View.GONE);
                binding.hidePostTextViewPostOptionsBottomSheetFragment.setVisibility(View.GONE);
                binding.crosspostTextViewPostOptionsBottomSheetFragment.setVisibility(View.GONE);
                binding.reportTextViewPostOptionsBottomSheetFragment.setVisibility(View.GONE);
            } else {
                if (mPost.isLocked() || mPost.isArchived()) {
                    binding.commentTextViewPostOptionsBottomSheetFragment.setVisibility(View.GONE);
                } else {
                    binding.commentTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                        Intent intent = new Intent(mBaseActivity, CommentActivity.class);
                        intent.putExtra(CommentActivity.EXTRA_PARENT_FULLNAME_KEY, mPost.getFullName());
                        intent.putExtra(CommentActivity.EXTRA_COMMENT_PARENT_TITLE_KEY, mPost.getTitle());
                        intent.putExtra(CommentActivity.EXTRA_COMMENT_PARENT_BODY_MARKDOWN_KEY, mPost.getSelfText());
                        intent.putExtra(CommentActivity.EXTRA_COMMENT_PARENT_BODY_KEY, mPost.getSelfTextPlain());
                        intent.putExtra(CommentActivity.EXTRA_SUBREDDIT_NAME_KEY, mPost.getSubredditName());
                        intent.putExtra(CommentActivity.EXTRA_IS_REPLYING_KEY, false);
                        intent.putExtra(CommentActivity.EXTRA_PARENT_DEPTH_KEY, 0);
                        mBaseActivity.startActivity(intent);

                        dismiss();
                    });
                }

                if (mPost.isHidden()) {
                    binding.hidePostTextViewPostOptionsBottomSheetFragment.setText(R.string.action_unhide_post);
                } else {
                    binding.hidePostTextViewPostOptionsBottomSheetFragment.setText(R.string.action_hide_post);
                }

                binding.hidePostTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                    if (mPost.isHidden()) {
                        HidePost.unhidePost(mOauthRetrofit, mBaseActivity.accessToken, mPost.getFullName(), new HidePost.HidePostListener() {
                            @Override
                            public void success() {
                                mPost.setHidden(false);
                                Toast.makeText(mBaseActivity, R.string.post_unhide_success, Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new PostUpdateEventToPostList(mPost, getArguments().getInt(EXTRA_POST_LIST_POSITION, 0)));
                                dismiss();
                            }

                            @Override
                            public void failed() {
                                mPost.setHidden(true);
                                Toast.makeText(mBaseActivity, R.string.post_unhide_failed, Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new PostUpdateEventToPostList(mPost, getArguments().getInt(EXTRA_POST_LIST_POSITION, 0)));
                                dismiss();
                            }
                        });
                    } else {
                        HidePost.hidePost(mOauthRetrofit, mBaseActivity.accessToken, mPost.getFullName(), new HidePost.HidePostListener() {
                            @Override
                            public void success() {
                                mPost.setHidden(true);
                                Toast.makeText(mBaseActivity, R.string.post_hide_success, Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new PostUpdateEventToPostList(mPost, getArguments().getInt(EXTRA_POST_LIST_POSITION, 0)));
                                dismiss();
                            }

                            @Override
                            public void failed() {
                                mPost.setHidden(false);
                                Toast.makeText(mBaseActivity, R.string.post_hide_failed, Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new PostUpdateEventToPostList(mPost, getArguments().getInt(EXTRA_POST_LIST_POSITION, 0)));
                                dismiss();
                            }
                        });
                    }
                });

                binding.crosspostTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                    Intent submitCrosspostIntent = new Intent(mBaseActivity, SubmitCrosspostActivity.class);
                    submitCrosspostIntent.putExtra(SubmitCrosspostActivity.EXTRA_POST, mPost);
                    startActivity(submitCrosspostIntent);

                    dismiss();
                });

                if (mPost.getAuthor().equals(mBaseActivity.accountName)) {
                    binding.notificationTextViewPostOptionsBottomSheetFragment.setVisibility(View.VISIBLE);
                    binding.notificationTextViewPostOptionsBottomSheetFragment.setText(mPost.isSendReplies() ? R.string.disable_reply_notifications : R.string.enable_reply_notifications);
                    binding.notificationTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                        if (getParentFragment() instanceof PostModerationActionHandler) {
                            ((PostModerationActionHandler) getParentFragment()).toggleNotification(mPost, getArguments().getInt(EXTRA_POST_LIST_POSITION, 0));
                        }
                        dismiss();
                    });
                }

                binding.reportTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                    Intent intent = new Intent(mBaseActivity, ReportActivity.class);
                    intent.putExtra(ReportActivity.EXTRA_SUBREDDIT_NAME, mPost.getSubredditName());
                    intent.putExtra(ReportActivity.EXTRA_THING_FULLNAME, mPost.getFullName());
                    startActivity(intent);

                    dismiss();
                });

                if (mPost.isCanModPost()) {
                    binding.modTextViewPostOptionsBottomSheetFragment.setVisibility(View.VISIBLE);
                    binding.modTextViewPostOptionsBottomSheetFragment.setOnClickListener(view -> {
                        PostModerationActionBottomSheetFragment postModerationActionBottomSheetFragment = PostModerationActionBottomSheetFragment.newInstance(mPost, getArguments().getInt(EXTRA_POST_LIST_POSITION, 0));
                        Fragment parentFragment = getParentFragment();
                        if (parentFragment != null) {
                            postModerationActionBottomSheetFragment.show(parentFragment.getChildFragmentManager(), postModerationActionBottomSheetFragment.getTag());
                        } else {
                            postModerationActionBottomSheetFragment.show(mBaseActivity.getSupportFragmentManager(), postModerationActionBottomSheetFragment.getTag());
                        }
                        dismiss();
                    });
                }
            }

            if (mPost.isApproved()) {
                binding.statusTextViewPostOptionsBottomSheetFragment.setText(getString(R.string.approved_status, mPost.getApprovedBy()));
            } else if (mPost.isRemoved()) {
                if (mPost.isSpam()) {
                    binding.statusTextViewPostOptionsBottomSheetFragment.setText(R.string.post_spam_status);
                } else {
                    binding.statusTextViewPostOptionsBottomSheetFragment.setText(R.string.post_removed_status);
                }
            } else {
                binding.statusTextViewPostOptionsBottomSheetFragment.setVisibility(View.GONE);
            }
        }

        if (mBaseActivity.typeface != null) {
            Utils.setFontToAllTextViews(binding.getRoot(), mBaseActivity.typeface);
        }

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mBaseActivity = (BaseActivity) context;
    }

    private void requestPermissionAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(mBaseActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                // Permission is not granted
                // No explanation needed; request the permission
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                // Permission has already been granted
                download();
            }
        } else {
            download();
        }
    }

    private void requestPermissionAndDownloadGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(mBaseActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                // Permission is not granted
                // No explanation needed; request the permission
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                // Permission has already been granted
                downloadGallery();
            }
        } else {
            downloadGallery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(mBaseActivity, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
                isDownloading = false;
                isDownloadingGallery = false;
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isDownloading) {
                    download();
                } else if (isDownloadingGallery) {
                    downloadGallery();
                }
            }
        }
    }

    private void download() {
        isDownloading = false;

        // Check if download location is set
        SharedPreferences sharedPreferences = mSharedPreferences;
        String downloadLocation;
        boolean isNsfw = mPost.isNSFW();

        int mediaType;
        switch (mPost.getPostType()) {
            case Post.VIDEO_TYPE:
                mediaType = DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO;
                break;
            case Post.GIF_TYPE:
                mediaType = DownloadMediaService.EXTRA_MEDIA_TYPE_GIF;
                break;
            default:
                mediaType = DownloadMediaService.EXTRA_MEDIA_TYPE_IMAGE;
                break;
        }

        if (isNsfw && sharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_NSFW_MEDIA_IN_DIFFERENT_FOLDER, false)) {
            downloadLocation = sharedPreferences.getString(SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION, "");
        } else {
            if (mediaType == DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO) {
                downloadLocation = sharedPreferences.getString(SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION, "");
            } else if (mediaType == DownloadMediaService.EXTRA_MEDIA_TYPE_GIF) {
                downloadLocation = sharedPreferences.getString(SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION, "");
            } else {
                downloadLocation = sharedPreferences.getString(SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION, "");
            }
        }

        if (downloadLocation == null || downloadLocation.isEmpty()) {
            Toast.makeText(mBaseActivity, R.string.download_location_not_set, Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        Toast.makeText(mBaseActivity, R.string.download_started, Toast.LENGTH_SHORT).show();
        if (mPost.getPostType() == Post.VIDEO_TYPE) {
            if (!mPost.isRedgifs() && !mPost.isStreamable() && !mPost.isImgur()) {
                PersistableBundle extras = new PersistableBundle();
                extras.putString(DownloadRedditVideoService.EXTRA_VIDEO_URL, mPost.getVideoDownloadUrl());
                extras.putString(DownloadRedditVideoService.EXTRA_POST_ID, mPost.getId());
                extras.putString(DownloadRedditVideoService.EXTRA_SUBREDDIT, mPost.getSubredditName());
                extras.putInt(DownloadRedditVideoService.EXTRA_IS_NSFW, mPost.isNSFW() ? 1 : 0);


                String title = (mPost != null) ? mPost.getTitle() : "reddit_video"; // Get title or use default
                String sanitizedTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[\\s_]+", "_").replaceAll("^_+|_+$", "");
                if (sanitizedTitle.length() > 100) sanitizedTitle = sanitizedTitle.substring(0, 100).replaceAll("_+$", "");
                if (sanitizedTitle.isEmpty()) sanitizedTitle = "reddit_video_" + System.currentTimeMillis();

                String finalFileName = sanitizedTitle + ".mp4";
                extras.putString(DownloadRedditVideoService.EXTRA_FILE_NAME, finalFileName);

                //TODO: contentEstimatedBytes
                JobInfo jobInfo = DownloadRedditVideoService.constructJobInfo(mBaseActivity, 5000000, extras);
                ((JobScheduler) mBaseActivity.getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(jobInfo);

                dismiss();
                return;
            }
        }

        JobInfo jobInfo = DownloadMediaService.constructJobInfo(mBaseActivity, 5000000, mPost, getArguments().getInt(EXTRA_GALLERY_INDEX, 0));
        ((JobScheduler) mBaseActivity.getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(jobInfo);

        dismiss();
    }

    private void downloadGallery() {
        isDownloadingGallery = false;

        // Check if download locations are set for all media types
        SharedPreferences sharedPreferences = mSharedPreferences;
        String imageDownloadLocation = sharedPreferences.getString(SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION, "");
        String gifDownloadLocation = sharedPreferences.getString(SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION, "");
        String videoDownloadLocation = sharedPreferences.getString(SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION, "");
        String nsfwDownloadLocation = "";

        boolean needsNsfwLocation = mPost.isNSFW() &&
                sharedPreferences.getBoolean(SharedPreferencesUtils.SAVE_NSFW_MEDIA_IN_DIFFERENT_FOLDER, false);

        if (needsNsfwLocation) {
            nsfwDownloadLocation = sharedPreferences.getString(SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION, "");
            if (nsfwDownloadLocation == null || nsfwDownloadLocation.isEmpty()) {
                Toast.makeText(mBaseActivity, R.string.download_location_not_set, Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }
        } else {
            // Check for required download locations based on the gallery content
            boolean hasImage = false;
            boolean hasGif = false;
            boolean hasVideo = false;

            ArrayList<Post.Gallery> gallery = mPost.getGallery();
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
                Toast.makeText(mBaseActivity, R.string.download_location_not_set, Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }
        }

        Toast.makeText(mBaseActivity, R.string.download_started, Toast.LENGTH_SHORT).show();
        JobInfo jobInfo = DownloadMediaService.constructGalleryDownloadAllMediaJobInfo(mBaseActivity, 5000000, mPost);
        ((JobScheduler) mBaseActivity.getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(jobInfo);

        dismiss();
    }
}
