package ml.docilealligator.infinityforreddit.bottomsheetfragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customviews.LandscapeExpandedRoundedBottomSheetDialogFragment;
import ml.docilealligator.infinityforreddit.databinding.FragmentNewPostFilterUsageBottomSheetBinding;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class NewPostFilterUsageBottomSheetFragment extends LandscapeExpandedRoundedBottomSheetDialogFragment {

    /**
     * Implemented by whichever screen is editing the filter's feeds. An interface rather than a cast
     * to a concrete activity, so this sheet is not tied to the standalone "Apply to" screen it was
     * originally written for.
     */
    public interface Host {
        void newPostFilterUsage(int type);
    }

    @SuppressWarnings("NullAway.Init")
    private Host host;
    @SuppressWarnings("NullAway.Init")
    private BaseActivity activity;

    public NewPostFilterUsageBottomSheetFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        FragmentNewPostFilterUsageBottomSheetBinding binding = FragmentNewPostFilterUsageBottomSheetBinding.inflate(inflater, container, false);

        binding.homeTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.HOME_TYPE);
            dismiss();
        });

        binding.subredditTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.SUBREDDIT_TYPE);
            dismiss();
        });

        binding.userTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.USER_TYPE);
            dismiss();
        });

        binding.multiredditTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.MULTIREDDIT_TYPE);
            dismiss();
        });

        binding.searchTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.SEARCH_TYPE);
            dismiss();
        });

        binding.historyTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.HISTORY_TYPE);
            dismiss();
        });

        binding.upvotedTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.UPVOTED_TYPE);
            dismiss();
        });

        binding.downvotedTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.DOWNVOTED_TYPE);
            dismiss();
        });

        binding.hiddenTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.HIDDEN_TYPE);
            dismiss();
        });

        binding.savedTextViewNewPostFilterUsageBottomSheetFragment.setOnClickListener(view -> {
            host.newPostFilterUsage(PostFilterUsage.SAVED_TYPE);
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
        host = (Host) context;
        activity = (BaseActivity) context;
    }
}