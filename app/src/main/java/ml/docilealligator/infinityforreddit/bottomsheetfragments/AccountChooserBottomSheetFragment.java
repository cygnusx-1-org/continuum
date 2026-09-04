package ml.docilealligator.infinityforreddit.bottomsheetfragments;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import com.bumptech.glide.Glide;
import java.util.List;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.account.AccountViewModel;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.adapters.AccountChooserRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.LandscapeExpandedRoundedBottomSheetDialogFragment;
import ml.docilealligator.infinityforreddit.databinding.FragmentAccountChooserBottomSheetBinding;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class AccountChooserBottomSheetFragment extends LandscapeExpandedRoundedBottomSheetDialogFragment {

    /**
     * Leaves the account in use out of the list, for callers that mean "an account other than this
     * one". Anonymous is absent either way: it is not a row in the accounts table.
     */
    public static final String EXTRA_EXCLUDE_CURRENT_ACCOUNT = "EECA";

    @Inject
    RedditDataRoomDatabase redditDataRoomDatabase;
    @Inject
    CustomThemeWrapper customThemeWrapper;
    @Inject
    @Named("security")
    SharedPreferences sharedPreferences;
    @Inject
    Executor executor;
    BaseActivity activity;
    AccountChooserRecyclerViewAdapter adapter;
    @SuppressWarnings("NullAway.Init")
    AccountViewModel accountViewModel;

    public AccountChooserBottomSheetFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        FragmentAccountChooserBottomSheetBinding binding = FragmentAccountChooserBottomSheetBinding.inflate(inflater, container, false);

        ((Infinity) activity.getApplication()).getAppComponent().inject(this);

        Utils.hideKeyboard(activity);

        adapter = new AccountChooserRecyclerViewAdapter(activity, customThemeWrapper, Glide.with(this),
                account -> {
                    if (activity instanceof AccountChooserListener) {
                        ((AccountChooserListener) activity).onAccountSelected(account);
                    }
                    dismiss();
                });
        binding.recyclerViewAccountChooserBottomSheetFragment.setAdapter(adapter);

        return binding.getRoot();
    }

    /**
     * Fills the list, behind the fingerprint prompt when the account section is locked.
     *
     * Here rather than in {@code onCreateView} so that the view exists by the time anything
     * observes against its lifecycle: the prompt answers asynchronously, and
     * {@link #observeAccounts()} tests for the view being gone.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (sharedPreferences.getBoolean(SharedPreferencesUtils.REQUIRE_AUTHENTICATION_TO_GO_TO_ACCOUNT_SECTION_IN_NAVIGATION_DRAWER, false)) {
            BiometricManager biometricManager = BiometricManager.from(activity);
            if (biometricManager.canAuthenticate(BIOMETRIC_STRONG | DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
                Executor executor = ContextCompat.getMainExecutor(activity);
                BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                        executor, new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        observeAccounts();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        dismiss();
                    }
                });

                BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.unlock))
                        .setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL)
                        .build();

                biometricPrompt.authenticate(promptInfo);
            } else {
                dismiss();
            }
        } else {
            observeAccounts();
        }
    }

    /**
     * Fills the list, with every account or with every account but the one in use, per
     * {@link #EXTRA_EXCLUDE_CURRENT_ACCOUNT}.
     *
     * Reached from the biometric callback as well, which arrives whenever the fingerprint prompt
     * is answered -- possibly after the sheet has been dismissed or rotated away, at which point
     * {@code getViewLifecycleOwner()} throws.
     */
    private void observeAccounts() {
        if (getView() == null) {
            return;
        }

        accountViewModel = new ViewModelProvider(this,
                new AccountViewModel.Factory(executor, redditDataRoomDatabase)).get(AccountViewModel.class);

        Bundle arguments = getArguments();
        LiveData<List<Account>> accounts =
                arguments != null && arguments.getBoolean(EXTRA_EXCLUDE_CURRENT_ACCOUNT, false)
                        ? accountViewModel.getAccountsExceptCurrentAccountLiveData()
                        : accountViewModel.getAllAccountsLiveData();

        accounts.observe(getViewLifecycleOwner(), adapter::changeAccountsDataset);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (BaseActivity) context;
    }

    public interface AccountChooserListener {
        void onAccountSelected(Account account);
    }
}