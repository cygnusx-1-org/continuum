package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.SettingsActivity;
import ml.docilealligator.infinityforreddit.adapters.SettingsSearchAdapter;
import ml.docilealligator.infinityforreddit.databinding.FragmentSettingsSearchBinding;

public class SettingsSearchFragment extends Fragment {

    private static final String TAG = "SettingsSearchFragment";

    @Nullable
    private FragmentSettingsSearchBinding binding;
    private SettingsActivity mActivity;
    private SettingsSearchAdapter mAdapter;
    /** Until the index is built there are legitimately no results, which is not "no matches". */
    private boolean mIndexReady;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (SettingsActivity) context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAdapter = new SettingsSearchAdapter(this::openSettingsItem);

        final FragmentSettingsSearchBinding binding = this.binding;
        if (binding == null) {
            return;
        }

        binding.rootFragmentSettingsSearch.setBackgroundColor(
                mActivity.customThemeWrapper.getBackgroundColor());
        binding.searchEditTextSettingsSearchFragment.setTextColor(
                mActivity.customThemeWrapper.getPrimaryTextColor());
        binding.searchEditTextSettingsSearchFragment.setHintTextColor(
                mActivity.customThemeWrapper.getSecondaryTextColor());
        binding.clearSearchSettingsSearchFragment.setColorFilter(
                mActivity.customThemeWrapper.getPrimaryIconColor());
        binding.emptyTextSettingsSearchFragment.setTextColor(
                mActivity.customThemeWrapper.getSecondaryTextColor());

        binding.recyclerViewSettingsSearchFragment.setLayoutManager(
                new LinearLayoutManager(mActivity));
        binding.recyclerViewSettingsSearchFragment.setAdapter(mAdapter);

        binding.searchEditTextSettingsSearchFragment.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString();
                mAdapter.filter(query);
                binding.clearSearchSettingsSearchFragment.setVisibility(
                        query.isEmpty() ? View.GONE : View.VISIBLE);
                updateEmptyState();
            }
        });

        binding.clearSearchSettingsSearchFragment.setOnClickListener(v ->
                binding.searchEditTextSettingsSearchFragment.setText(""));

        // Show keyboard
        binding.searchEditTextSettingsSearchFragment.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.searchEditTextSettingsSearchFragment,
                    InputMethodManager.SHOW_IMPLICIT);
        }

        loadIndex();
    }

    /**
     * Builds the search index off the main thread. It parses every preference XML, which is work
     * the settings screen should not pay for on the way in -- only opening search needs it.
     */
    private void loadIndex() {
        SettingsSearchRegistry registry = SettingsSearchRegistry.getInstance();
        if (registry.hasIndexFor(mActivity)) {
            onIndexReady();
            return;
        }
        updateEmptyState();

        // A context carrying the activity's configuration rather than the activity itself:
        // BaseActivity applies the in-app language override to the activity's resources, so the
        // application context would resolve stale strings, but the parse has no business holding
        // an Activity. Copied, because BaseActivity mutates that Configuration in place every
        // time it creates an activity. The callback still references this fragment, which the
        // binding check in onIndexReady makes harmless.
        Context context = mActivity.createConfigurationContext(
                new Configuration(mActivity.getResources().getConfiguration()));
        Handler mainHandler = new Handler(Looper.getMainLooper());

        // Its own thread, not the shared pool: the pool's four threads serve network and database
        // work, and queueing behind them would leave the results list blank for no good reason.
        new Thread(() -> {
            try {
                registry.buildRegistry(context);
            } catch (RuntimeException e) {
                // A malformed index should leave search empty, not take the process down from a
                // thread the user never asked for.
                Log.e(TAG, "Failed to build the settings search index", e);
            }
            mainHandler.post(this::onIndexReady);
        }, "settings-search-index").start();
    }

    private void onIndexReady() {
        FragmentSettingsSearchBinding binding = this.binding;
        if (binding == null) {
            return;
        }
        mIndexReady = true;
        // Also covers anything typed while the index was still building.
        mAdapter.filter(binding.searchEditTextSettingsSearchFragment.getText().toString());
        updateEmptyState();
    }

    /**
     * Opens the screen a result lives on, scrolled to the preference itself.
     *
     * <p>Results on the settings root are a special case: that screen is already at the bottom of
     * the back stack, so clear back to it rather than stacking a second copy above the results.
     */
    private void openSettingsItem(SettingsSearchItem item) {
        FragmentManager fragmentManager = mActivity.getSupportFragmentManager();

        if (item.fragmentClass == MainPreferenceFragment.class) {
            // The root is already at the bottom of the back stack, so clear back to it rather than
            // stacking a second copy above the results. Its instance is reused and its onViewCreated
            // already ran, so it will not read a scroll argument -- key the scroll to its view being
            // recreated by the pop instead of guessing when a posted runnable lands relative to it.
            String key = item.key;
            if (key != null) {
                scrollToPreferenceOnceReattached(fragmentManager, MainPreferenceFragment.class, key);
            }
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return;
        }

        Fragment fragment = fragmentManager.getFragmentFactory()
                .instantiate(mActivity.getClassLoader(), item.fragmentClass.getName());
        fragment.setArguments(SettingsScreenArgs.withScrollTarget(null, item.key));
        mActivity.navigateToSettingsFragment(fragment, item.fragmentTitle);
    }

    /**
     * Scrolls to {@code key} the moment an instance of {@code screen} has its view created, then
     * stops listening. Registered before the pop that brings the screen back, so the callback is in
     * place whenever that view is recreated -- no dependence on main-looper ordering.
     */
    private static void scrollToPreferenceOnceReattached(
            FragmentManager fragmentManager, Class<? extends Fragment> screen, String key) {
        fragmentManager.registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(@NonNull FragmentManager fm,
                            @NonNull Fragment f, @NonNull View v, @Nullable Bundle savedInstanceState) {
                        if (screen.isInstance(f)) {
                            fm.unregisterFragmentLifecycleCallbacks(this);
                            scrollToPreference(f, key);
                        }
                    }
                }, false);
    }

    private static void scrollToPreference(@Nullable Fragment fragment, @Nullable String key) {
        if (key != null && fragment instanceof PreferenceFragmentCompat) {
            ((PreferenceFragmentCompat) fragment).scrollToPreference(key);
        }
    }

    private void updateEmptyState() {
        final FragmentSettingsSearchBinding binding = this.binding;
        if (binding == null) {
            return;
        }
        if (!mIndexReady) {
            binding.emptyTextSettingsSearchFragment.setText(R.string.settings_search_loading);
            binding.recyclerViewSettingsSearchFragment.setVisibility(View.GONE);
            binding.emptyTextSettingsSearchFragment.setVisibility(View.VISIBLE);
            return;
        }

        boolean hasText = !binding.searchEditTextSettingsSearchFragment.getText().toString().isEmpty();
        if (hasText && mAdapter.isEmpty()) {
            binding.emptyTextSettingsSearchFragment.setText(R.string.settings_search_no_results);
            binding.recyclerViewSettingsSearchFragment.setVisibility(View.GONE);
            binding.emptyTextSettingsSearchFragment.setVisibility(View.VISIBLE);
        } else {
            binding.recyclerViewSettingsSearchFragment.setVisibility(View.VISIBLE);
            binding.emptyTextSettingsSearchFragment.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Hide keyboard when leaving
        InputMethodManager imm = (InputMethodManager)
                mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && binding != null) {
            imm.hideSoftInputFromWindow(
                    binding.searchEditTextSettingsSearchFragment.getWindowToken(), 0);
        }
        binding = null;
    }
}
