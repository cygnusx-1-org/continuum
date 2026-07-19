package ml.docilealligator.infinityforreddit.adapters;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.UUID;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.PostGalleryActivity;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.SetRedditGalleryItemCaptionAndUrlBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemRedditGallerySubmissionImageBinding;
import ml.docilealligator.infinityforreddit.post.RedditGalleryPayload;

public class RedditGallerySubmissionRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_IMAGE = 1;
    private static final int VIEW_TYPE_ADD_IMAGE = 2;
    /** Reddit's cap on gallery submissions. At the cap the trailing add tile is dropped. */
    private static final int MAX_IMAGES = 20;

    private final PostGalleryActivity activity;
    private ArrayList<RedditGalleryImageInfo> redditGalleryImageInfoList = new ArrayList<>();
    private final CustomThemeWrapper customThemeWrapper;
    private final ItemClickListener itemClickListener;
    private final RequestManager glide;

    public RedditGallerySubmissionRecyclerViewAdapter(PostGalleryActivity activity, CustomThemeWrapper customThemeWrapper,
                                                      ItemClickListener itemClickListener) {
        this.activity = activity;
        glide = Glide.with(activity);
        this.customThemeWrapper = customThemeWrapper;
        this.itemClickListener = itemClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= redditGalleryImageInfoList.size()) {
            return VIEW_TYPE_ADD_IMAGE;
        }

        return VIEW_TYPE_IMAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ADD_IMAGE) {
            return new AddImageViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reddit_gallery_submission_add_image, parent, false));
        }
        return new ImageViewHolder(ItemRedditGallerySubmissionImageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ImageViewHolder) {
            ItemRedditGallerySubmissionImageBinding itemBinding = ((ImageViewHolder) holder).binding;

            // Set both directions here rather than leaning on onViewRecycled to restore the
            // pending state: an entry is removable only once its upload has produced a payload.
            // Before the Glide load, so a synchronous failure's listener still wins below.
            boolean uploaded = redditGalleryImageInfoList.get(position).payload != null;
            itemBinding.progressBarItemRedditGallerySubmissionImage.setVisibility(uploaded ? View.GONE : View.VISIBLE);
            itemBinding.closeImageViewItemRedditGallerySubmissionImage.setVisibility(uploaded ? View.VISIBLE : View.GONE);

            glide.load(redditGalleryImageInfoList.get(position).imageUrlString)
                    .apply(new RequestOptions().transform(new CenterCrop(), new RoundedCorners(48)))
                    .listener(new RequestListener<>() {

                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            // No thumbnail to show, so surface the close button and let the user
                            // drop the entry even if its upload never settled.
                            itemBinding.progressBarItemRedditGallerySubmissionImage.setVisibility(View.GONE);
                            itemBinding.closeImageViewItemRedditGallerySubmissionImage.setVisibility(View.VISIBLE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(itemBinding.aspectRatioGifImageViewItemRedditGallerySubmissionImage);
        }
    }

    @Override
    public int getItemCount() {
        return redditGalleryImageInfoList.size() + (hasAddTile() ? 1 : 0);
    }

    /**
     * The add tile occupies the position after the last image, and disappears at {@link #MAX_IMAGES}.
     * Crossing that boundary changes the item count by zero even though an image was added or
     * removed, so the mutators below pair their granular notification with a matching one for the
     * tile — otherwise RecyclerView's count bookkeeping desyncs and the next layout throws.
     */
    private boolean hasAddTile() {
        return redditGalleryImageInfoList.size() < MAX_IMAGES;
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof ImageViewHolder) {
            glide.clear(((ImageViewHolder) holder).binding.aspectRatioGifImageViewItemRedditGallerySubmissionImage);
            ((ImageViewHolder) holder).binding.progressBarItemRedditGallerySubmissionImage.setVisibility(View.VISIBLE);
            ((ImageViewHolder) holder).binding.closeImageViewItemRedditGallerySubmissionImage.setVisibility(View.GONE);
        }
    }

    /**
     * @return a snapshot of the entries. Structural changes have to go through this adapter so its
     * item-count bookkeeping stays in step with what it notified; callers get a copy so they cannot
     * add or remove behind its back. The entries themselves are shared, so a payload that lands
     * after this returns is still visible to the caller.
     */
    public ArrayList<RedditGalleryImageInfo> getRedditGalleryImageInfoList() {
        return new ArrayList<>(redditGalleryImageInfoList);
    }

    public void setRedditGalleryImageInfoList(ArrayList<RedditGalleryImageInfo> redditGalleryImageInfoList) {
        // Copied for the same reason: whoever handed this in keeps their reference.
        this.redditGalleryImageInfoList = new ArrayList<>(redditGalleryImageInfoList);
        notifyDataSetChanged();
    }

    /**
     * @return the new entry's {@link RedditGalleryImageInfo#id}, to hand back to
     * {@link #setImageAsUploaded} or {@link #removeFailedToUploadImage} when its upload settles.
     */
    public String addImage(String imageUrl) {
        RedditGalleryImageInfo imageInfo = new RedditGalleryImageInfo(imageUrl);
        redditGalleryImageInfoList.add(imageInfo);
        notifyItemInserted(redditGalleryImageInfoList.size() - 1);
        if (!hasAddTile()) {
            // Reaching the cap consumed the add tile, which sat just past the new image.
            notifyItemRemoved(redditGalleryImageInfoList.size());
        }
        return imageInfo.id;
    }

    public void setImageAsUploaded(String imageId, String mediaId) {
        int position = indexOfImage(imageId);
        if (position == RecyclerView.NO_POSITION) {
            // Removed while its upload was in flight — there is nothing left to mark.
            return;
        }
        redditGalleryImageInfoList.get(position).payload = new RedditGalleryPayload.Item("", "", mediaId);
        notifyItemChanged(position);
    }

    public void removeFailedToUploadImage(String imageId) {
        int position = indexOfImage(imageId);
        if (position == RecyclerView.NO_POSITION) {
            // The user already removed it; nothing to clean up.
            return;
        }
        removeImageAt(position);
    }

    private void removeImageAt(int position) {
        boolean hadAddTile = hasAddTile();
        redditGalleryImageInfoList.remove(position);
        notifyItemRemoved(position);
        if (!hadAddTile && hasAddTile()) {
            // Dropping back under the cap restores the add tile after the last image.
            notifyItemInserted(redditGalleryImageInfoList.size());
        }
    }

    private int indexOfImage(String imageId) {
        for (int i = 0; i < redditGalleryImageInfoList.size(); i++) {
            if (redditGalleryImageInfoList.get(i).id.equals(imageId)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    public void setCaptionAndUrl(int position, String caption, String url) {
        if (redditGalleryImageInfoList.size() > position && position >= 0) {
            var payload = redditGalleryImageInfoList.get(position).payload;
            if (payload != null) {
                payload.setCaption(caption);
                payload.setOutboundUrl(url);
            }
        }
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {
        ItemRedditGallerySubmissionImageBinding binding;

        public ImageViewHolder(@NonNull ItemRedditGallerySubmissionImageBinding binding) {
            super(binding.getRoot());

            this.binding = binding;

            binding.aspectRatioGifImageViewItemRedditGallerySubmissionImage.setRatio(1);

            binding.aspectRatioGifImageViewItemRedditGallerySubmissionImage.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                RedditGalleryPayload.Item payload = redditGalleryImageInfoList.get(position).payload;
                if (payload != null) {
                    SetRedditGalleryItemCaptionAndUrlBottomSheetFragment fragment = new SetRedditGalleryItemCaptionAndUrlBottomSheetFragment();
                    Bundle bundle = new Bundle();
                    bundle.putInt(SetRedditGalleryItemCaptionAndUrlBottomSheetFragment.EXTRA_POSITION, position);
                    bundle.putString(SetRedditGalleryItemCaptionAndUrlBottomSheetFragment.EXTRA_CAPTION, payload.getCaption());
                    bundle.putString(SetRedditGalleryItemCaptionAndUrlBottomSheetFragment.EXTRA_URL, payload.getOutboundUrl());
                    fragment.setArguments(bundle);
                    fragment.show(activity.getSupportFragmentManager(), fragment.getTag());
                }
            });

            binding.closeImageViewItemRedditGallerySubmissionImage.setOnClickListener(view -> {
                // Read once: removing shifts every later position, so re-reading it for the
                // notify would report the wrong row.
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                removeImageAt(position);
            });
        }
    }

    class AddImageViewHolder extends RecyclerView.ViewHolder {

        public AddImageViewHolder(@NonNull View itemView) {
            super(itemView);

            FloatingActionButton fab = itemView.findViewById(R.id.fab_item_gallery_submission_add_image);
            fab.setBackgroundTintList(ColorStateList.valueOf(customThemeWrapper.getColorAccent()));
            fab.setImageTintList(ColorStateList.valueOf(customThemeWrapper.getFABIconColor()));

            itemView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    itemView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    int width = itemView.getMeasuredWidth();
                    ViewGroup.LayoutParams params = itemView.getLayoutParams();
                    params.height = width;
                    itemView.setLayoutParams(params);
                }
            });

            fab.setOnClickListener(view -> itemClickListener.onAddImageClicked());
            itemView.setOnClickListener(view -> fab.performClick());
        }
    }

    public static class RedditGalleryImageInfo implements Parcelable {
        /**
         * Identifies this entry for the duration of its upload. Positions shift when the user
         * removes an image, and the same picture can legitimately be added twice, so neither an
         * index nor {@link #imageUrlString} can tell the uploads apart.
         */
        public final String id;
        public String imageUrlString;
        @androidx.annotation.Nullable
        public RedditGalleryPayload.Item payload;

        public RedditGalleryImageInfo(String imageUrlString) {
            this.id = UUID.randomUUID().toString();
            this.imageUrlString = imageUrlString;
        }

        protected RedditGalleryImageInfo(Parcel in) {
            id = java.util.Objects.requireNonNull(in.readString());
            imageUrlString = java.util.Objects.requireNonNull(in.readString());
            payload = in.readParcelable(RedditGalleryPayload.Item.class.getClassLoader());
        }

        public static final Creator<RedditGalleryImageInfo> CREATOR = new Creator<RedditGalleryImageInfo>() {
            @Override
            public RedditGalleryImageInfo createFromParcel(Parcel in) {
                return new RedditGalleryImageInfo(in);
            }

            @Override
            public RedditGalleryImageInfo[] newArray(int size) {
                return new RedditGalleryImageInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeString(id);
            parcel.writeString(imageUrlString);
            parcel.writeParcelable(payload, i);
        }
    }

    public interface ItemClickListener {
        void onAddImageClicked();
    }
}
