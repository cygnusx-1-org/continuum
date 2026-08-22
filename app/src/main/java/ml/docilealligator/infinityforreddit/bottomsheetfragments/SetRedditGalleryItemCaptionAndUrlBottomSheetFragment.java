package ml.docilealligator.infinityforreddit.bottomsheetfragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.PostGalleryActivity;
import ml.docilealligator.infinityforreddit.customviews.LandscapeExpandedRoundedBottomSheetDialogFragment;
import ml.docilealligator.infinityforreddit.databinding.FragmentSetRedditGalleryItemCaptionAndUrlBottomSheetBinding;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class SetRedditGalleryItemCaptionAndUrlBottomSheetFragment extends LandscapeExpandedRoundedBottomSheetDialogFragment {

    public static final String EXTRA_POSITION = "EP";
    public static final String EXTRA_CAPTION = "EC";
    public static final String EXTRA_URL = "EU";

    private PostGalleryActivity mActivity;

    public SetRedditGalleryItemCaptionAndUrlBottomSheetFragment() {

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentSetRedditGalleryItemCaptionAndUrlBottomSheetBinding binding = FragmentSetRedditGalleryItemCaptionAndUrlBottomSheetBinding.inflate(inflater, container, false);

        int primaryTextColor = mActivity.getResources().getColor(R.color.primaryTextColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.captionTextInputLayoutSetRedditGalleryItemCaptionAndUrlBottomSheetFragment.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.urlTextInputLayoutSetRedditGalleryItemCaptionAndUrlBottomSheetFragment.setCursorColor(ColorStateList.valueOf(primaryTextColor));
        } else {
            Utils.setCursorDrawableColor(binding.captionTextInputEditTextSetRedditGalleryItemCaptionAndUrlBottomSheetFragment, primaryTextColor);
            Utils.setCursorDrawableColor(binding.urlTextInputEditTextSetRedditGalleryItemCaptionAndUrlBottomSheetFragment, primaryTextColor);
        }

        int position = getArguments().getInt(EXTRA_POSITION, -1);
        String caption = getArguments().getString(EXTRA_CAPTION, "");
        String url = getArguments().getString(EXTRA_URL, "");

        binding.captionTextInputEditTextSetRedditGalleryItemCaptionAndUrlBottomSheetFragment.setText(caption);
        binding.urlTextInputEditTextSetRedditGalleryItemCaptionAndUrlBottomSheetFragment.setText(url);

        binding.okButtonSetRedditGalleryItemCaptionAndUrlBottomSheetFragment.setOnClickListener(view -> {
            mActivity.setCaptionAndUrl(position, java.util.Objects.requireNonNull(binding.captionTextInputEditTextSetRedditGalleryItemCaptionAndUrlBottomSheetFragment.getText()).toString(), java.util.Objects.requireNonNull(binding.urlTextInputEditTextSetRedditGalleryItemCaptionAndUrlBottomSheetFragment.getText()).toString());
            dismiss();
        });

        if (mActivity.typeface != null) {
            Utils.setFontToAllTextViews(binding.getRoot(), mActivity.typeface);
        }

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (PostGalleryActivity) context;
    }
}
