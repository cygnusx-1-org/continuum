package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import ml.docilealligator.infinityforreddit.activities.SettingsActivity;
import ml.docilealligator.infinityforreddit.adapters.SettingsSearchAdapter;
import ml.docilealligator.infinityforreddit.databinding.FragmentSettingsSearchBinding;

public class SettingsSearchFragment extends Fragment {

    private FragmentSettingsSearchBinding binding;
    private SettingsActivity mActivity;
    private SettingsSearchAdapter mAdapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (SettingsActivity) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        mAdapter = new SettingsSearchAdapter(item -> {
            Fragment fragment = mActivity.getSupportFragmentManager()
                    .getFragmentFactory()
                    .instantiate(requireActivity().getClassLoader(), item.fragmentClass.getName());
            mActivity.navigateToSettingsFragment(fragment, item.fragmentTitleResId);
        });

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
    }

    private void updateEmptyState() {
        boolean hasText = !binding.searchEditTextSettingsSearchFragment.getText().toString().isEmpty();
        boolean isEmpty = mAdapter.isEmpty();
        if (hasText && isEmpty) {
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
