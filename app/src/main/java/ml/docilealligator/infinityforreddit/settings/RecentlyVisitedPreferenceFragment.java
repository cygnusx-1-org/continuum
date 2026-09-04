package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.AccountScope;
import ml.docilealligator.infinityforreddit.activities.SettingsActivity;
import ml.docilealligator.infinityforreddit.databinding.FragmentRecentlyVisitedSettingsBinding;
import ml.docilealligator.infinityforreddit.recentlyvisited.RecordRecentlyVisited;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

/**
 * The single toggle behind Settings -> Recently Visited. Hand-built rather than a
 * PreferenceFragmentCompat because the key is account-prefixed, which the preference framework
 * cannot express.
 */
public class RecentlyVisitedPreferenceFragment extends Fragment {

    private FragmentRecentlyVisitedSettingsBinding binding;
    @Inject
    @Named("recently_visited")
    SharedPreferences recentlyVisitedSharedPreferences;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    Executor mExecutor;
    private SettingsActivity mActivity;

    public RecentlyVisitedPreferenceFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRecentlyVisitedSettingsBinding.inflate(inflater, container, false);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        binding.getRoot().setBackgroundColor(mActivity.customThemeWrapper.getBackgroundColor());
        binding.saveRecentlyVisitedTextViewRecentlyVisitedPreferenceFragment.setTextColor(
                mActivity.customThemeWrapper.getPrimaryTextColor());
        binding.saveRecentlyVisitedDescriptionTextViewRecentlyVisitedPreferenceFragment.setTextColor(
                mActivity.customThemeWrapper.getSecondaryTextColor());

        if (mActivity.isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, mActivity.isForcedImmersiveInterface());
                    binding.getRoot().setPadding(allInsets.left, 0, allInsets.right, allInsets.bottom);
                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        if (mActivity.typeface != null) {
            Utils.setFontToAllTextViews(binding.getRoot(), mActivity.typeface);
        }

        binding.saveRecentlyVisitedSwitchRecentlyVisitedPreferenceFragment.setChecked(
                recentlyVisitedSharedPreferences.getBoolean(
                        AccountScope.key(mActivity.accountName, SharedPreferencesUtils.RECENTLY_VISITED_ENABLED_BASE), false));

        binding.saveRecentlyVisitedLinearLayoutRecentlyVisitedPreferenceFragment.setOnClickListener(view ->
                binding.saveRecentlyVisitedSwitchRecentlyVisitedPreferenceFragment.performClick());
        binding.saveRecentlyVisitedSwitchRecentlyVisitedPreferenceFragment.setOnCheckedChangeListener((compoundButton, b) -> {
            recentlyVisitedSharedPreferences.edit().putBoolean(
                    AccountScope.key(mActivity.accountName, SharedPreferencesUtils.RECENTLY_VISITED_ENABLED_BASE), b).apply();
            if (!b) {
                // Switching it off is the only way to clear the list, so it has to actually clear it.
                RecordRecentlyVisited.purge(mExecutor, mRedditDataRoomDatabase, mActivity.accountName);
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.mActivity = (SettingsActivity) context;
    }
}
