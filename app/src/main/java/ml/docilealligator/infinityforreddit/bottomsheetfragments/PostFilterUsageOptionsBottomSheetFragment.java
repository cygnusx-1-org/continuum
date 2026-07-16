package ml.docilealligator.infinityforreddit.bottomsheetfragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.activities.PostFilterUsageListingActivity;
import ml.docilealligator.infinityforreddit.customviews.LandscapeExpandedRoundedBottomSheetDialogFragment;
import ml.docilealligator.infinityforreddit.databinding.FragmentPostFilterUsageOptionsBottomSheetBinding;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class PostFilterUsageOptionsBottomSheetFragment extends LandscapeExpandedRoundedBottomSheetDialogFragment {

    public static final String EXTRA_POST_FILTER_USAGE = "EPFU";

    private PostFilterUsageListingActivity activity;

    public PostFilterUsageOptionsBottomSheetFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        FragmentPostFilterUsageOptionsBottomSheetBinding binding = FragmentPostFilterUsageOptionsBottomSheetBinding.inflate(inflater, container, false);

        PostFilterUsage postFilterUsage = java.util.Objects.requireNonNull(getArguments().getParcelable(EXTRA_POST_FILTER_USAGE));

        if (postFilterUsage.usage == PostFilterUsage.HOME_TYPE || postFilterUsage.usage == PostFilterUsage.SEARCH_TYPE
                || postFilterUsage.usage == PostFilterUsage.HISTORY_TYPE || postFilterUsage.usage == PostFilterUsage.UPVOTED_TYPE
                || postFilterUsage.usage == PostFilterUsage.DOWNVOTED_TYPE || postFilterUsage.usage == PostFilterUsage.HIDDEN_TYPE
                || postFilterUsage.usage == PostFilterUsage.SAVED_TYPE) {
            binding.editTextViewPostFilterUsageOptionsBottomSheetFragment.setVisibility(View.GONE);
        } else {
            binding.editTextViewPostFilterUsageOptionsBottomSheetFragment.setOnClickListener(view -> {
                activity.editPostFilterUsage(postFilterUsage);
                dismiss();
            });
        }

        binding.deleteTextViewPostFilterUsageOptionsBottomSheetFragment.setOnClickListener(view -> {
            activity.deletePostFilterUsage(postFilterUsage);
            dismiss();
        });

        if (activity.typeface != null) {
            Utils.setFontToAllTextViews(binding.getRoot(), activity.typeface);
        }

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (PostFilterUsageListingActivity) context;
    }
}